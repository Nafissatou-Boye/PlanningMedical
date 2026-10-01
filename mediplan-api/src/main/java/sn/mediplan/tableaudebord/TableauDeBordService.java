package sn.mediplan.tableaudebord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.conges.Conge;
import sn.mediplan.conges.CongeRepository;
import sn.mediplan.parametrage.PlageHoraire;
import sn.mediplan.personnel.Agent;
import sn.mediplan.personnel.AgentRepository;
import sn.mediplan.personnel.CategorieAgent;
import sn.mediplan.planning.Affectation;
import sn.mediplan.planning.AffectationRepository;
import sn.mediplan.planning.PlanningDtos.Statistiques;
import sn.mediplan.planning.PlanningService;

/** Indicateurs du tableau de bord pour un service, calculés sur le mois en cours. */
@Service
public class TableauDeBordService {

    public enum StatutJour { EN_GARDE, DE_GARDE_AUJOURDHUI, EN_CONGE, DISPONIBLE }

    public record MembreEquipe(Long agentId, String nomAffichage, String profession, CategorieAgent categorie,
            StatutJour statut, String detail) {
    }

    public record Indicateurs(boolean planningCree, int personnelActif, int stagiairesActifs, int gardesDuMois, int gardesMoisPrecedent,
            int enGardeMaintenant, int couverture, int couverturePrecedente, int placesLibres,
            List<MembreEquipe> equipe) {
    }

    private static final DateTimeFormatter JJ_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final PlanningService plannings;
    private final AgentRepository agents;
    private final AffectationRepository affectations;
    private final CongeRepository conges;

    public TableauDeBordService(PlanningService plannings, AgentRepository agents,
            AffectationRepository affectations, CongeRepository conges) {
        this.plannings = plannings;
        this.agents = agents;
        this.affectations = affectations;
        this.conges = conges;
    }

    @Transactional(readOnly = true)
    public Indicateurs indicateurs(Long serviceId) {
        LocalDate aujourdhui = LocalDate.now();
        LocalDateTime maintenant = LocalDateTime.now();
        LocalDate precedent = aujourdhui.minusMonths(1);

        var grilleDuMois = plannings.grille(serviceId, aujourdhui.getYear(), aujourdhui.getMonthValue());
        Statistiques ce = grilleDuMois.statistiques();
        Statistiques av = plannings.grille(serviceId, precedent.getYear(), precedent.getMonthValue()).statistiques();

        List<Agent> actifs = agents.findActifsDuService(serviceId);
        Set<Long> ids = actifs.stream().map(Agent::getId).collect(Collectors.toSet());

        // Gardes d'aujourd'hui (tous services : un agent peut être de garde ailleurs)
        List<Affectation> duJour = affectations.findDansPeriode(aujourdhui.atStartOfDay(),
                aujourdhui.plusDays(1).atStartOfDay());
        Map<Long, Affectation> gardeParAgent = new HashMap<>();
        for (Affectation a : duJour) {
            Long id = a.getAgent().getId();
            if (!ids.contains(id)) {
                continue;
            }
            Affectation deja = gardeParAgent.get(id);
            boolean enCours = !a.getDebut().isAfter(maintenant) && a.getFin().isAfter(maintenant);
            if (deja == null || enCours) {
                gardeParAgent.put(id, a);
            }
        }
        Map<Long, Conge> congeParAgent = conges.findChevauchant(aujourdhui, aujourdhui).stream()
                .filter(c -> ids.contains(c.getAgent().getId()))
                .collect(Collectors.toMap(c -> c.getAgent().getId(), c -> c, (a, b) -> a));

        List<MembreEquipe> equipe = actifs.stream().map(a -> {
            Affectation g = gardeParAgent.get(a.getId());
            Conge c = congeParAgent.get(a.getId());
            StatutJour statut;
            String detail = null;
            if (g != null) {
                boolean enCours = !g.getDebut().isAfter(maintenant) && g.getFin().isAfter(maintenant);
                statut = enCours ? StatutJour.EN_GARDE : StatutJour.DE_GARDE_AUJOURDHUI;
                PlageHoraire p = g.getCreneau().getPlageHoraire();
                detail = p.getSousService().getNom() + " " + p.getLibelle();
            } else if (c != null) {
                statut = StatutJour.EN_CONGE;
                detail = "jusqu'au " + c.getDateFin().format(JJ_MM);
            } else {
                statut = StatutJour.DISPONIBLE;
            }
            return new MembreEquipe(a.getId(), a.getNomAffichage(),
                    a.getProfession() != null ? a.getProfession().getLibelle() : null, a.getCategorie(), statut,
                    detail);
        }).sorted(Comparator.comparing(MembreEquipe::statut).thenComparing(MembreEquipe::nomAffichage,
                String.CASE_INSENSITIVE_ORDER)).toList();

        int enGarde = (int) equipe.stream().filter(m -> m.statut() == StatutJour.EN_GARDE).count();
        int stagiaires = (int) actifs.stream().filter(a -> a.getCategorie() == CategorieAgent.STAGIAIRE).count();

        return new Indicateurs(grilleDuMois.planningId() != null, actifs.size() - stagiaires, stagiaires, ce.placesRemplies(), av.placesRemplies(),
                enGarde, pourcentage(ce), pourcentage(av), ce.placesTotales() - ce.placesRemplies(), equipe);
    }

    private static int pourcentage(Statistiques s) {
        return s.placesTotales() == 0 ? 0 : Math.round(100f * s.placesRemplies() / s.placesTotales());
    }
}
