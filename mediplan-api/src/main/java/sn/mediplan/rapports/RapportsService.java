package sn.mediplan.rapports;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.conges.Conge;
import sn.mediplan.conges.CongeRepository;
import sn.mediplan.parametrage.TypeGarde;
import sn.mediplan.personnel.Agent;
import sn.mediplan.personnel.AgentRepository;
import sn.mediplan.personnel.CategorieAgent;
import sn.mediplan.planning.PlanningDtos.Case;
import sn.mediplan.planning.PlanningDtos.ColonnePlage;
import sn.mediplan.planning.PlanningDtos.ColonneSousService;
import sn.mediplan.planning.PlanningDtos.Grille;
import sn.mediplan.planning.PlanningDtos.Jour;
import sn.mediplan.planning.PlanningDtos.PeriodeAstreinte;
import sn.mediplan.planning.PlanningService;

/**
 * Rapport annuel d'un service : couverture mois par mois et répartition des gardes par agent
 * (pour vérifier que la charge est équitable).
 */
@Service
public class RapportsService {

    public record Mois(int mois, boolean planningCree, int placesTotales, int placesRemplies, int couverture) {
    }

    public record LigneAgent(Long agentId, String nomAffichage, String profession, CategorieAgent categorie,
            boolean actif, int[] parMois, int total, int jour, int nuit, int h24, int weekendFerie, int astreintes,
            int joursAbsence) {
    }

    public record Rapport(Long serviceId, String service, int annee, List<Mois> mois, List<LigneAgent> agents,
            int totalGardes, int couvertureMoyenne, int moisCrees) {
    }

    private final PlanningService plannings;
    private final AgentRepository agents;
    private final CongeRepository conges;

    public RapportsService(PlanningService plannings, AgentRepository agents, CongeRepository conges) {
        this.plannings = plannings;
        this.agents = agents;
        this.conges = conges;
    }

    /** Compteurs d'un agent pendant le calcul. */
    private static final class Compteur {
        String nom;
        String profession;
        CategorieAgent categorie;
        boolean actif;
        final int[] parMois = new int[12];
        int jour, nuit, h24, weekendFerie, astreintes, absence;

        void init(Agent a) {
            nom = a.getNomAffichage();
            profession = a.getProfession() != null ? a.getProfession().getLibelle() : null;
            categorie = a.getCategorie();
            actif = a.isActif();
        }
    }

    @Transactional(readOnly = true)
    public Rapport rapport(Long serviceId, int annee) {
        Map<Long, Compteur> parAgent = new HashMap<>();
        // Agents actifs du service : ils apparaissent même avec 0 garde (c'est justement ce qu'on veut voir)
        for (Agent a : agents.findActifsDuService(serviceId)) {
            compteur(parAgent, a.getId(), a.getNomAffichage()).init(a);
        }

        String nomService = null;
        List<Mois> mois = new java.util.ArrayList<>();
        int totalGardes = 0, sommeCouverture = 0, moisCrees = 0;
        for (int m = 1; m <= 12; m++) {
            Grille g = plannings.grille(serviceId, annee, m);
            nomService = g.service();
            boolean cree = g.planningId() != null;
            int couverture = g.statistiques().placesTotales() == 0 ? 0
                    : Math.round(100f * g.statistiques().placesRemplies() / g.statistiques().placesTotales());
            mois.add(new Mois(m, cree, g.statistiques().placesTotales(), g.statistiques().placesRemplies(), couverture));
            if (!cree) {
                continue;
            }
            moisCrees++;
            sommeCouverture += couverture;

            Map<Long, TypeGarde> types = new HashMap<>();
            for (ColonneSousService ss : g.colonnes()) {
                for (ColonnePlage p : ss.plages()) {
                    types.put(p.plageId(), p.typeGarde());
                }
            }
            Map<LocalDate, Jour> jours = new HashMap<>();
            for (Jour j : g.jours()) {
                jours.put(j.date(), j);
            }
            for (Case c : g.cases()) {
                Jour j = jours.get(c.date());
                boolean special = j != null && (j.weekend() || j.ferie() != null);
                for (var place : c.agents()) {
                    Compteur k = compteur(parAgent, place.agentId(), place.nomAffichage());
                    k.parMois[m - 1]++;
                    totalGardes++;
                    switch (types.getOrDefault(c.plageId(), TypeGarde.JOUR)) {
                        case NUIT -> k.nuit++;
                        case H24 -> k.h24++;
                        default -> k.jour++;
                    }
                    if (special) {
                        k.weekendFerie++;
                    }
                }
            }
            for (PeriodeAstreinte a : g.astreintes()) {
                if (a.agentId() != null) {
                    compteur(parAgent, a.agentId(), a.nomAffichage()).astreintes++;
                }
            }
        }

        // Jours d'absence (congés et indisponibilités) comptés dans l'année
        LocalDate debut = LocalDate.of(annee, 1, 1), fin = LocalDate.of(annee, 12, 31);
        for (Conge c : conges.findChevauchant(debut, fin)) {
            Compteur k = parAgent.get(c.getAgent().getId());
            if (k != null) {
                LocalDate d = c.getDateDebut().isBefore(debut) ? debut : c.getDateDebut();
                LocalDate f = c.getDateFin().isAfter(fin) ? fin : c.getDateFin();
                k.absence += (int) ChronoUnit.DAYS.between(d, f) + 1;
            }
        }

        List<LigneAgent> lignes = parAgent.entrySet().stream()
                .map(e -> {
                    Compteur k = e.getValue();
                    int total = java.util.Arrays.stream(k.parMois).sum();
                    return new LigneAgent(e.getKey(), k.nom, k.profession, k.categorie, k.actif, k.parMois, total,
                            k.jour, k.nuit, k.h24, k.weekendFerie, k.astreintes, k.absence);
                })
                .sorted(Comparator.comparingInt(LigneAgent::total).reversed()
                        .thenComparing(LigneAgent::nomAffichage, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new Rapport(serviceId, nomService, annee, mois, lignes, totalGardes,
                moisCrees == 0 ? 0 : Math.round((float) sommeCouverture / moisCrees), moisCrees);
    }

    private static Compteur compteur(Map<Long, Compteur> m, Long id, String nom) {
        return m.computeIfAbsent(id, k -> {
            Compteur c = new Compteur();
            c.nom = nom;
            return c;
        });
    }
}
