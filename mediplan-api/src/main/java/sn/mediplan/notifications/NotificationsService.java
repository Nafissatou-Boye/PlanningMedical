package sn.mediplan.notifications;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.conges.Conge;
import sn.mediplan.conges.CongeRepository;
import sn.mediplan.diffusion.DiffusionService;
import sn.mediplan.diffusion.EnvoiPdf;
import sn.mediplan.diffusion.EnvoiPdfRepository;
import sn.mediplan.diffusion.StatutEnvoi;
import sn.mediplan.personnel.Agent;
import sn.mediplan.personnel.AgentRepository;
import sn.mediplan.personnel.Stage;
import sn.mediplan.personnel.StageRepository;
import sn.mediplan.planning.Affectation;
import sn.mediplan.planning.AffectationRepository;
import sn.mediplan.planning.JourFerieRepository;
import sn.mediplan.planning.PlanningDtos.Grille;
import sn.mediplan.planning.PlanningService;
import sn.mediplan.stagiaires.AffectationStage;
import sn.mediplan.stagiaires.AffectationStageRepository;

/**
 * Notifications calculées à la demande (rien n'est stocké) : ce qui demande l'attention du planificateur.
 * Chaque notification a une clé stable : le navigateur retient celles qui ont été lues.
 */
@Service
public class NotificationsService {

    public enum Niveau { URGENT, ATTENTION, INFO }

    public record Notification(String cle, Niveau niveau, String icone, String titre, String detail, String lien) {
    }

    private static final DateTimeFormatter JJ_MM = DateTimeFormatter.ofPattern("dd/MM");
    private static final Locale FR = Locale.FRENCH;

    private final PlanningService plannings;
    private final AffectationRepository affectations;
    private final AffectationStageRepository affectationsStage;
    private final CongeRepository conges;
    private final StageRepository stages;
    private final AgentRepository agents;
    private final JourFerieRepository feries;
    private final EnvoiPdfRepository envois;
    private final DiffusionService diffusion;

    public NotificationsService(PlanningService plannings, AffectationRepository affectations,
            AffectationStageRepository affectationsStage, CongeRepository conges, StageRepository stages,
            AgentRepository agents, JourFerieRepository feries, EnvoiPdfRepository envois,
            DiffusionService diffusion) {
        this.plannings = plannings;
        this.affectations = affectations;
        this.affectationsStage = affectationsStage;
        this.conges = conges;
        this.stages = stages;
        this.agents = agents;
        this.feries = feries;
        this.envois = envois;
        this.diffusion = diffusion;
    }

    @Transactional(readOnly = true)
    public List<Notification> notifications(Long serviceId) {
        LocalDate auj = LocalDate.now();
        LocalDate horizon = auj.plusDays(60);
        List<Notification> l = new ArrayList<>();
        Set<Long> equipe = agents.findActifsDuService(serviceId).stream().map(Agent::getId)
                .collect(Collectors.toSet());
        List<Conge> congesAVenir = conges.findChevauchant(auj, horizon);
        Map<Long, List<Conge>> congesParAgent = congesAVenir.stream()
                .collect(Collectors.groupingBy(c -> c.getAgent().getId()));

        // 1. Agent de garde pendant un congé saisi après coup (le planning doit être corrigé)
        for (Affectation a : affectations.findDansPeriode(auj.atStartOfDay(), horizon.atStartOfDay())) {
            if (!a.getCreneau().getPlageHoraire().getSousService().getService().getId().equals(serviceId)) {
                continue;
            }
            LocalDate jour = a.getCreneau().getDateGarde();
            for (Conge c : congesParAgent.getOrDefault(a.getAgent().getId(), List.of())) {
                if (!jour.isBefore(c.getDateDebut()) && !jour.isAfter(c.getDateFin())) {
                    var p = a.getCreneau().getPlageHoraire();
                    l.add(new Notification("conflit-garde-" + a.getId() + "-" + c.getId(), Niveau.URGENT, "event_busy",
                            a.getAgent().getNomAffichage() + " est de garde pendant son congé",
                            jourLong(jour) + " · " + p.getSousService().getNom() + " " + p.getLibelle()
                                    + ". Libérez sa place et choisissez un remplaçant.",
                            "/planning"));
                }
            }
        }
        for (AffectationStage a : affectationsStage.findDansPeriode(auj, horizon)) {
            if (!a.getPoste().getService().getId().equals(serviceId)) {
                continue;
            }
            for (Conge c : congesParAgent.getOrDefault(a.getAgent().getId(), List.of())) {
                if (!a.getPeriode().getDateDebut().isAfter(c.getDateFin())
                        && !a.getPeriode().getDateFin().isBefore(c.getDateDebut())) {
                    l.add(new Notification("conflit-stage-" + a.getId() + "-" + c.getId(), Niveau.URGENT, "event_busy",
                            a.getAgent().getNomAffichage() + " a un poste pendant son congé",
                            a.getPoste().getLibelle() + " du " + a.getPeriode().getDateDebut().format(JJ_MM) + " au "
                                    + a.getPeriode().getDateFin().format(JJ_MM) + ".",
                            "/stagiaires"));
                }
            }
        }

        // 2. Planning du mois prochain : à créer (dès le 15) ou places à pourvoir
        LocalDate suivant = auj.plusMonths(1).withDayOfMonth(1);
        Grille gs = plannings.grille(serviceId, suivant.getYear(), suivant.getMonthValue());
        String moisSuivant = nomMois(suivant);
        if (gs.planningId() == null) {
            if (auj.getDayOfMonth() >= 15) {
                l.add(new Notification("planning-a-creer-" + suivant, Niveau.ATTENTION, "event_note",
                        "Le planning " + moisSuivant + " n'est pas encore créé",
                        "Il commence dans " + (suivant.toEpochDay() - auj.toEpochDay()) + " jour(s).", "/planning"));
            }
        } else {
            placesLibres(l, gs, moisSuivant, Niveau.INFO);
        }
        // Mois en cours : places encore libres
        Grille gc = plannings.grille(serviceId, auj.getYear(), auj.getMonthValue());
        if (gc.planningId() != null) {
            placesLibres(l, gc, nomMois(auj), Niveau.ATTENTION);
        }

        // 3. Congés qui commencent dans les 7 jours
        for (Conge c : congesAVenir) {
            if (equipe.contains(c.getAgent().getId()) && !c.getDateDebut().isBefore(auj)
                    && !c.getDateDebut().isAfter(auj.plusDays(7))) {
                l.add(new Notification("conge-" + c.getId(), Niveau.INFO, "beach_access",
                        c.getAgent().getNomAffichage() + " part en congé " + quand(c.getDateDebut(), auj),
                        "Du " + c.getDateDebut().format(JJ_MM) + " au " + c.getDateFin().format(JJ_MM) + ".", "/conges"));
            }
        }

        // 4. Stages qui se terminent dans les 14 jours
        for (Stage s : stages.findChevauchant(auj, auj.plusDays(14))) {
            if (s.getService().getId().equals(serviceId) && !s.getDateFin().isAfter(auj.plusDays(14))
                    && equipe.contains(s.getAgent().getId())) {
                l.add(new Notification("fin-stage-" + s.getId(), Niveau.INFO, "school",
                        "Fin de stage de " + s.getAgent().getNomAffichage() + " " + quand(s.getDateFin(), auj),
                        "Il ne sera plus proposé après le " + s.getDateFin().format(JJ_MM) + ".", "/stagiaires"));
            }
        }

        // 5. Envois d'e-mails en échec (7 derniers jours)
        List<EnvoiPdf> echecs = envois.findParStatutDepuis(StatutEnvoi.ECHEC, LocalDateTime.now().minusDays(7)).stream()
                .filter(e -> e.getPlanning().getService().getId().equals(serviceId)).toList();
        if (!echecs.isEmpty()) {
            EnvoiPdf dernier = echecs.get(0);
            l.add(new Notification("echecs-envoi-" + dernier.getId(), Niveau.ATTENTION, "mail_lock",
                    echecs.size() + " e-mail" + (echecs.size() > 1 ? "s" : "") + " non envoyé" + (echecs.size() > 1 ? "s" : "")
                            + " cette semaine",
                    "Dernier : " + (dernier.getAgent() != null ? dernier.getAgent().getNomAffichage() : dernier.getEmailDestinataire())
                            + (dernier.getMessageErreur() != null ? " — " + court(dernier.getMessageErreur()) : ""),
                    "/aide"));
        }

        // 6. Réglages manquants
        if (!diffusion.configuration().configure()) {
            l.add(new Notification("email-non-configure", Niveau.INFO, "forward_to_inbox",
                    "L'envoi des e-mails n'est pas configuré",
                    "Renseignez secrets.properties pour envoyer les plannings.", "/aide"));
        }
        if (feries.findByDateFerieBetween(LocalDate.of(auj.getYear(), 1, 1), LocalDate.of(auj.getYear(), 12, 31))
                .isEmpty()) {
            l.add(new Notification("feries-" + auj.getYear(), Niveau.INFO, "celebration",
                    "Aucun jour férié enregistré pour " + auj.getYear(),
                    "Ajoutez-les en un clic dans Paramètres.", "/parametres"));
        }

        l.sort(java.util.Comparator.comparing(Notification::niveau));
        return l;
    }

    private static void placesLibres(List<Notification> l, Grille g, String mois, Niveau niveau) {
        int libres = g.statistiques().placesTotales() - g.statistiques().placesRemplies();
        int ast = g.statistiques().astreintesTotales() - g.statistiques().astreintesRemplies();
        if (libres > 0 || ast > 0) {
            String detail = (libres > 0 ? libres + " place" + (libres > 1 ? "s" : "") : "")
                    + (libres > 0 && ast > 0 ? " et " : "")
                    + (ast > 0 ? ast + " astreinte" + (ast > 1 ? "s" : "") : "") + " à pourvoir.";
            l.add(new Notification("places-" + g.annee() + "-" + g.mois() + "-" + libres + "-" + ast, niveau,
                    "pending_actions", "Planning " + mois + " incomplet", detail, "/planning"));
        }
    }

    private static String nomMois(LocalDate d) {
        String m = d.getMonth().getDisplayName(TextStyle.FULL, FR);
        return (m.matches("^[aeiouy].*") ? "d'" : "de ") + m + " " + d.getYear();
    }

    private static String jourLong(LocalDate d) {
        String j = d.getDayOfWeek().getDisplayName(TextStyle.FULL, FR);
        return Character.toUpperCase(j.charAt(0)) + j.substring(1) + " " + d.format(JJ_MM);
    }

    private static String quand(LocalDate d, LocalDate auj) {
        long n = d.toEpochDay() - auj.toEpochDay();
        return n == 0 ? "aujourd'hui" : n == 1 ? "demain" : "le " + d.format(JJ_MM);
    }

    private static String court(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
