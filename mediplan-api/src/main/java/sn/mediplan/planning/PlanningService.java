package sn.mediplan.planning;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.commun.RegleMetierException;
import sn.mediplan.conges.Conge;
import sn.mediplan.conges.CongeRepository;
import sn.mediplan.parametrage.ModeAffectation;
import sn.mediplan.parametrage.PlageHoraire;
import sn.mediplan.parametrage.PlageHoraireRepository;
import sn.mediplan.parametrage.ServiceHospitalier;
import sn.mediplan.parametrage.ServiceHospitalierRepository;
import sn.mediplan.parametrage.SousService;
import sn.mediplan.parametrage.SousServiceRepository;
import sn.mediplan.personnel.Agent;
import sn.mediplan.personnel.AgentRepository;
import sn.mediplan.personnel.CategorieAgent;
import sn.mediplan.personnel.Stage;
import sn.mediplan.personnel.StageRepository;
import sn.mediplan.planning.Periodes.Periode;
import sn.mediplan.planning.PlanningDtos.AffectationRequete;
import sn.mediplan.planning.PlanningDtos.AstreinteRequete;
import sn.mediplan.planning.PlanningDtos.Candidat;
import sn.mediplan.planning.PlanningDtos.Case;
import sn.mediplan.planning.PlanningDtos.ColonnePlage;
import sn.mediplan.planning.PlanningDtos.ColonneSousService;
import sn.mediplan.planning.PlanningDtos.CongeDuMois;
import sn.mediplan.planning.PlanningDtos.Grille;
import sn.mediplan.planning.PlanningDtos.Jour;
import sn.mediplan.planning.PlanningDtos.LiberationRequete;
import sn.mediplan.planning.PlanningDtos.ModeRequete;
import sn.mediplan.planning.PlanningDtos.PeriodeAstreinte;
import sn.mediplan.planning.PlanningDtos.Place;
import sn.mediplan.planning.PlanningDtos.Statistiques;

/**
 * Planning mensuel des gardes.
 *
 * Règles appliquées à chaque affectation :
 * 1. une place déjà attribuée est verrouillée : il faut la libérer avant de la donner à un autre agent ;
 * 2. un agent ne peut pas avoir deux gardes qui se chevauchent (tous services confondus) ;
 * 3. un agent en congé, inactif, non rattaché au service ou hors de sa période de stage n'est pas accepté.
 */
@Service
public class PlanningService {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter JJ_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final PlanningRepository plannings;
    private final CreneauRepository creneaux;
    private final AffectationRepository affectations;
    private final AstreinteRepository astreintes;
    private final JourFerieRepository joursFeries;
    private final ServiceHospitalierRepository services;
    private final SousServiceRepository sousServices;
    private final PlageHoraireRepository plages;
    private final AgentRepository agents;
    private final StageRepository stages;
    private final CongeRepository conges;

    public PlanningService(PlanningRepository plannings, CreneauRepository creneaux,
            AffectationRepository affectations, AstreinteRepository astreintes, JourFerieRepository joursFeries,
            ServiceHospitalierRepository services, SousServiceRepository sousServices,
            PlageHoraireRepository plages, AgentRepository agents, StageRepository stages,
            CongeRepository conges) {
        this.plannings = plannings;
        this.creneaux = creneaux;
        this.affectations = affectations;
        this.astreintes = astreintes;
        this.joursFeries = joursFeries;
        this.services = services;
        this.sousServices = sousServices;
        this.plages = plages;
        this.agents = agents;
        this.stages = stages;
        this.conges = conges;
    }

    // =====================================================================
    //  Lecture de la grille
    // =====================================================================

    @Transactional(readOnly = true)
    public Grille grille(Long serviceId, int annee, int mois) {
        ServiceHospitalier service = services.findById(serviceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Service introuvable."));
        Optional<Planning> planning = plannings.findByServiceIdAndTypeAndAnneeAndMois(
                serviceId, TypePlanning.GARDES, annee, mois);
        return construire(service, annee, mois, planning.orElse(null));
    }

    @Transactional(readOnly = true)
    public Grille grille(Long planningId) {
        Planning p = trouverPlanning(planningId);
        return construire(p.getService(), p.getAnnee(), p.getMois(), p);
    }

    private Grille construire(ServiceHospitalier service, int annee, int mois, Planning planning) {
        LocalDate premier = LocalDate.of(annee, mois, 1);
        LocalDate dernier = premier.withDayOfMonth(premier.lengthOfMonth());

        List<Creneau> cr = planning == null ? List.of() : creneaux.findByPlanning(planning.getId());
        List<Affectation> aff = planning == null ? List.of() : affectations.findByPlanning(planning.getId());

        // Colonnes : plages actives du service (+ plages archivées déjà utilisées ce mois-ci)
        var plagesUtilisees = cr.stream().map(c -> c.getPlageHoraire().getId()).collect(Collectors.toSet());
        List<PlageHoraire> toutesPlages = plages.findAllOrdonnees();
        List<ColonneSousService> colonnes = new ArrayList<>();
        for (SousService ss : sousServices.findAllOrdonnes()) {
            if (!ss.getService().getId().equals(service.getId())) {
                continue;
            }
            List<ColonnePlage> cols = toutesPlages.stream()
                    .filter(p -> p.getSousService().getId().equals(ss.getId()))
                    .filter(p -> (ss.isActif() && p.isActif()) || plagesUtilisees.contains(p.getId()))
                    .map(p -> new ColonnePlage(p.getId(), p.getLibelle(), p.getHeureDebut(), p.getHeureFin(),
                            p.getTypeGarde(), p.getModeParDefaut()))
                    .toList();
            if (!cols.isEmpty()) {
                colonnes.add(new ColonneSousService(ss.getId(), ss.getNom(), cols));
            }
        }

        // Jours du mois
        Map<LocalDate, String> feries = joursFeries.findByDateFerieBetween(premier, dernier).stream()
                .collect(Collectors.toMap(JourFerie::getDateFerie, JourFerie::getLibelle, (a, b) -> a));
        List<Jour> jours = new ArrayList<>();
        for (LocalDate d = premier; !d.isAfter(dernier); d = d.plusDays(1)) {
            DayOfWeek j = d.getDayOfWeek();
            String nomJour = j.getDisplayName(TextStyle.FULL, FR);
            jours.add(new Jour(d, Character.toUpperCase(nomJour.charAt(0)) + nomJour.substring(1) + " "
                    + String.format("%02d", d.getDayOfMonth()),
                    j == DayOfWeek.SATURDAY || j == DayOfWeek.SUNDAY, feries.get(d)));
        }

        // Cases remplies
        Map<Long, List<Affectation>> parCreneau = aff.stream()
                .collect(Collectors.groupingBy(a -> a.getCreneau().getId()));
        List<Case> cases = cr.stream()
                .map(c -> new Case(c.getPlageHoraire().getId(), c.getDateGarde(), c.getMode(),
                        parCreneau.getOrDefault(c.getId(), List.of()).stream()
                                .sorted(Comparator.comparingInt(Affectation::getPosition))
                                .map(a -> new Place(a.getPosition(), a.getAgent().getId(),
                                        a.getAgent().getNomAffichage()))
                                .toList()))
                .toList();

        // Astreintes par période
        Map<LocalDate, Astreinte> astParDebut = planning == null ? Map.of()
                : astreintes.findByPlanning(planning.getId()).stream()
                        .collect(Collectors.toMap(Astreinte::getDateDebut, a -> a, (a, b) -> a));
        List<PeriodeAstreinte> periodes = Periodes.astreintes(annee, mois).stream()
                .map(p -> {
                    Astreinte a = astParDebut.get(p.debut());
                    return new PeriodeAstreinte(p.debut(), p.fin(), a == null ? null : a.getAgent().getId(),
                            a == null ? null : a.getAgent().getNomAffichage());
                })
                .toList();

        // Bloc « CONGÉS » : congés affichables des agents du service qui touchent le mois
        var agentsDuService = agents.findActifsDuService(service.getId()).stream().map(Agent::getId)
                .collect(Collectors.toSet());
        List<CongeDuMois> congesDuMois = conges.findChevauchant(premier, dernier).stream()
                .filter(Conge::isAfficheSurPdf)
                .filter(c -> agentsDuService.contains(c.getAgent().getId()))
                .map(c -> new CongeDuMois(c.getAgent().getNomAffichage(), c.getDateDebut(), c.getDateFin()))
                .toList();

        // Statistiques de couverture
        Map<String, Creneau> creneauParCle = cr.stream()
                .collect(Collectors.toMap(c -> cle(c.getPlageHoraire().getId(), c.getDateGarde()), c -> c));
        int placesTotales = 0;
        for (ColonneSousService col : colonnes) {
            for (ColonnePlage p : col.plages()) {
                for (Jour j : jours) {
                    Creneau c = creneauParCle.get(cle(p.plageId(), j.date()));
                    ModeAffectation mode = c != null ? c.getMode() : p.modeParDefaut();
                    placesTotales += mode == ModeAffectation.BINOME ? 2 : 1;
                }
            }
        }
        int astreintesRemplies = (int) periodes.stream().filter(p -> p.agentId() != null).count();

        return new Grille(planning == null ? null : planning.getId(),
                planning == null ? null : planning.getStatut(),
                service.getId(), service.getNom(), annee, mois,
                planning == null ? null : planning.getDateDocument(),
                reference(planning, service),
                colonnes, jours, cases, periodes, congesDuMois,
                new Statistiques(placesTotales, aff.size(), periodes.size(), astreintesRemplies));
    }

    private static String cle(Long plageId, LocalDate date) {
        return plageId + "|" + date;
    }

    // =====================================================================
    //  Création et statut
    // =====================================================================

    @Transactional
    public Long creer(Long serviceId, int annee, int mois) {
        ServiceHospitalier service = services.findById(serviceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Service introuvable."));
        if (plannings.findByServiceIdAndTypeAndAnneeAndMois(serviceId, TypePlanning.GARDES, annee, mois)
                .isPresent()) {
            throw RegleMetierException.conflit("Le planning de ce mois existe déjà pour ce service.");
        }
        Planning p = new Planning();
        p.setService(service);
        p.setType(TypePlanning.GARDES);
        p.setAnnee(annee);
        p.setMois(mois);
        p.setStatut(StatutPlanning.BROUILLON);
        p.setDateDocument(LocalDate.now());
        p.setNumeroReference(service.getReferenceDocument());
        return plannings.save(p).getId();
    }

    @Transactional
    public void changerStatut(Long planningId, StatutPlanning statut) {
        Planning p = trouverPlanning(planningId);
        if (statut == StatutPlanning.PUBLIE && p.getStatut() != StatutPlanning.PUBLIE) {
            p.setDateDocument(LocalDate.now());
        }
        p.setStatut(statut);
    }

    // =====================================================================
    //  Gardes : candidats, affecter, libérer, mode
    // =====================================================================

    /** Tous les agents du service, disponibles d'abord, avec la raison quand ils ne le sont pas. */
    @Transactional(readOnly = true)
    public List<Candidat> candidats(Long planningId, Long plageId, LocalDate date) {
        Planning planning = trouverPlanning(planningId);
        PlageHoraire plage = trouverPlage(planning, plageId);
        LocalDateTime debut = debut(plage, date);
        LocalDateTime fin = fin(plage, date);
        Contexte ctx = contexte(planning, debut.toLocalDate(), fin.toLocalDate());

        Creneau creneau = creneaux.findByPlageHoraireIdAndDateGarde(plageId, date).orElse(null);
        var dansLaCase = creneau == null ? java.util.Set.<Long>of()
                : affectations.findByCreneauId(creneau.getId()).stream().map(a -> a.getAgent().getId())
                        .collect(Collectors.toSet());

        return ctx.agents.stream()
                .map(a -> {
                    String raison = dansLaCase.contains(a.getId()) ? "Déjà dans cette case"
                            : raisonIndisponible(a, debut, fin, ctx);
                    return new Candidat(a.getId(), a.getNomAffichage(), a.getPrenom() + " " + a.getNom(),
                            a.getProfession() != null ? a.getProfession().getLibelle() : null, a.getCategorie(),
                            raison == null, raison, ctx.gardesDuMois.getOrDefault(a.getId(), 0));
                })
                .sorted(Comparator.comparing((Candidat c) -> !c.disponible())
                        .thenComparingInt(Candidat::gardesDuMois)
                        .thenComparing(Candidat::nomAffichage, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public void affecter(Long planningId, AffectationRequete r) {
        Planning planning = trouverPlanning(planningId);
        PlageHoraire plage = trouverPlage(planning, r.plageId());
        verifierDate(planning, r.date());

        Creneau creneau = trouverOuCreerCreneau(planning, plage, r.date());
        if (r.position() > creneau.places()) {
            throw RegleMetierException.conflit(
                    "Cette case est en mode Unique : passez-la en Binôme pour ajouter un deuxième agent.");
        }

        List<Affectation> presentes = affectations.findByCreneauId(creneau.getId());
        presentes.stream().filter(a -> a.getPosition() == r.position()).findFirst().ifPresent(a -> {
            throw RegleMetierException.conflit("Cette place est déjà attribuée à " + a.getAgent().getNomAffichage()
                    + ". Libérez-la d'abord pour la donner à quelqu'un d'autre.");
        });
        if (presentes.stream().anyMatch(a -> a.getAgent().getId().equals(r.agentId()))) {
            throw RegleMetierException.conflit("Cet agent est déjà dans cette case.");
        }

        Agent agent = agents.findById(r.agentId())
                .orElseThrow(() -> RegleMetierException.introuvable("Agent introuvable."));
        Contexte ctx = contexte(planning, creneau.getDebut().toLocalDate(), creneau.getFin().toLocalDate());
        if (ctx.agents.stream().noneMatch(a -> a.getId().equals(agent.getId()))) {
            throw RegleMetierException.conflit(agent.getNomAffichage()
                    + " n'est pas (ou plus) actif dans le service " + planning.getService().getNom() + ".");
        }
        String raison = raisonIndisponible(agent, creneau.getDebut(), creneau.getFin(), ctx);
        if (raison != null) {
            throw RegleMetierException.conflit(agent.getNomAffichage() + " : " + raison + ".");
        }

        Affectation a = new Affectation();
        a.setCreneau(creneau);
        a.setAgent(agent);
        a.setPosition(r.position());
        a.setDebut(creneau.getDebut());
        a.setFin(creneau.getFin());
        affectations.saveAndFlush(a); // la contrainte unique de la base reste le dernier verrou
    }

    @Transactional
    public void liberer(Long planningId, LiberationRequete r) {
        Planning planning = trouverPlanning(planningId);
        trouverPlage(planning, r.plageId());
        Creneau creneau = creneaux.findByPlageHoraireIdAndDateGarde(r.plageId(), r.date())
                .orElseThrow(() -> RegleMetierException.introuvable("Cette place est déjà libre."));
        Affectation a = affectations.findByCreneauId(creneau.getId()).stream()
                .filter(x -> x.getPosition() == r.position()).findFirst()
                .orElseThrow(() -> RegleMetierException.introuvable("Cette place est déjà libre."));
        affectations.delete(a);
    }

    @Transactional
    public void changerMode(Long planningId, ModeRequete r) {
        Planning planning = trouverPlanning(planningId);
        PlageHoraire plage = trouverPlage(planning, r.plageId());
        verifierDate(planning, r.date());
        Creneau creneau = trouverOuCreerCreneau(planning, plage, r.date());
        if (r.mode() == ModeAffectation.UNIQUE
                && affectations.findByCreneauId(creneau.getId()).stream().anyMatch(a -> a.getPosition() == 2)) {
            throw RegleMetierException.conflit("Libérez d'abord la deuxième place avant de repasser en Unique.");
        }
        creneau.setMode(r.mode());
    }

    // =====================================================================
    //  Astreintes
    // =====================================================================

    @Transactional(readOnly = true)
    public List<Candidat> candidatsAstreinte(Long planningId, LocalDate dateDebut) {
        Planning planning = trouverPlanning(planningId);
        Periode periode = trouverPeriode(planning, dateDebut);
        Contexte ctx = contexte(planning, periode.debut(), periode.fin());
        return ctx.agents.stream()
                .map(a -> {
                    String raison = raisonAstreinte(a, periode, ctx, planning.getId());
                    return new Candidat(a.getId(), a.getNomAffichage(), a.getPrenom() + " " + a.getNom(),
                            a.getProfession() != null ? a.getProfession().getLibelle() : null, a.getCategorie(),
                            raison == null, raison, ctx.gardesDuMois.getOrDefault(a.getId(), 0));
                })
                .sorted(Comparator.comparing((Candidat c) -> !c.disponible())
                        .thenComparing(Candidat::nomAffichage, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public void affecterAstreinte(Long planningId, AstreinteRequete r) {
        Planning planning = trouverPlanning(planningId);
        Periode periode = trouverPeriode(planning, r.dateDebut());
        astreintes.findByPlanningIdAndDateDebut(planningId, periode.debut()).ifPresent(a -> {
            throw RegleMetierException.conflit("Cette astreinte est déjà attribuée à "
                    + a.getAgent().getNomAffichage() + ". Libérez-la d'abord.");
        });
        Agent agent = agents.findById(r.agentId())
                .orElseThrow(() -> RegleMetierException.introuvable("Agent introuvable."));
        Contexte ctx = contexte(planning, periode.debut(), periode.fin());
        if (ctx.agents.stream().noneMatch(a -> a.getId().equals(agent.getId()))) {
            throw RegleMetierException.conflit(agent.getNomAffichage()
                    + " n'est pas (ou plus) actif dans le service " + planning.getService().getNom() + ".");
        }
        String raison = raisonAstreinte(agent, periode, ctx, planningId);
        if (raison != null) {
            throw RegleMetierException.conflit(agent.getNomAffichage() + " : " + raison + ".");
        }
        Astreinte a = new Astreinte();
        a.setPlanning(planning);
        a.setAgent(agent);
        a.setDateDebut(periode.debut());
        a.setDateFin(periode.fin());
        astreintes.saveAndFlush(a);
    }

    @Transactional
    public void libererAstreinte(Long planningId, LocalDate dateDebut) {
        Astreinte a = astreintes.findByPlanningIdAndDateDebut(planningId, dateDebut)
                .orElseThrow(() -> RegleMetierException.introuvable("Cette astreinte est déjà libre."));
        astreintes.delete(a);
    }

    // =====================================================================
    //  Disponibilité d'un agent
    // =====================================================================

    /** Données chargées une fois pour évaluer tous les agents sans requête par agent. */
    private record Contexte(List<Agent> agents, Map<Long, List<Stage>> stages, Map<Long, List<Conge>> conges,
            Map<Long, List<Affectation>> gardes, Map<Long, Integer> gardesDuMois) {
    }

    private Contexte contexte(Planning planning, LocalDate debut, LocalDate fin) {
        List<Agent> candidats = agents.findActifsDuService(planning.getService().getId());
        Map<Long, List<Stage>> st = stages.findChevauchant(debut, fin).stream()
                .collect(Collectors.groupingBy(s -> s.getAgent().getId()));
        Map<Long, List<Conge>> cg = conges.findChevauchant(debut, fin).stream()
                .collect(Collectors.groupingBy(c -> c.getAgent().getId()));
        Map<Long, List<Affectation>> gd = affectations
                .findDansPeriode(debut.atStartOfDay(), fin.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.groupingBy(a -> a.getAgent().getId()));
        Map<Long, Integer> duMois = new HashMap<>();
        for (Affectation a : affectations.findByPlanning(planning.getId())) {
            duMois.merge(a.getAgent().getId(), 1, Integer::sum);
        }
        return new Contexte(candidats, st, cg, gd, duMois);
    }

    /** Null si l'agent peut prendre la garde [debut, fin[, sinon la raison lisible. */
    private String raisonIndisponible(Agent a, LocalDateTime debut, LocalDateTime fin, Contexte ctx) {
        LocalDate jourDebut = debut.toLocalDate();
        // Dernier jour réellement travaillé (une garde 08H-08H finit le lendemain à 08H)
        LocalDate jourFin = fin.minusMinutes(1).toLocalDate();

        if (a.getCategorie() == CategorieAgent.STAGIAIRE) {
            boolean couvert = ctx.stages.getOrDefault(a.getId(), List.of()).stream()
                    .anyMatch(s -> !s.getDateDebut().isAfter(jourDebut) && !s.getDateFin().isBefore(jourDebut));
            if (!couvert) {
                return "hors de sa période de stage";
            }
        }
        for (Conge c : ctx.conges.getOrDefault(a.getId(), List.of())) {
            if (!c.getDateDebut().isAfter(jourFin) && !c.getDateFin().isBefore(jourDebut)) {
                return "en congé du " + c.getDateDebut().format(JJ_MM) + " au " + c.getDateFin().format(JJ_MM);
            }
        }
        for (Affectation g : ctx.gardes.getOrDefault(a.getId(), List.of())) {
            if (g.getDebut().isBefore(fin) && g.getFin().isAfter(debut)) {
                PlageHoraire p = g.getCreneau().getPlageHoraire();
                return "déjà de garde " + p.getSousService().getNom() + " " + p.getLibelle() + " le "
                        + g.getCreneau().getDateGarde().format(JJ_MM);
            }
        }
        return null;
    }

    private String raisonAstreinte(Agent a, Periode periode, Contexte ctx, Long planningId) {
        if (a.getCategorie() == CategorieAgent.STAGIAIRE) {
            boolean couvert = ctx.stages.getOrDefault(a.getId(), List.of()).stream()
                    .anyMatch(s -> !s.getDateDebut().isAfter(periode.debut())
                            && !s.getDateFin().isBefore(periode.fin()));
            if (!couvert) {
                return "hors de sa période de stage";
            }
        }
        for (Conge c : ctx.conges.getOrDefault(a.getId(), List.of())) {
            if (!c.getDateDebut().isAfter(periode.fin()) && !c.getDateFin().isBefore(periode.debut())) {
                return "en congé du " + c.getDateDebut().format(JJ_MM) + " au " + c.getDateFin().format(JJ_MM);
            }
        }
        boolean autreAstreinte = astreintes.findChevauchantes(a.getId(), periode.debut(), periode.fin()).stream()
                .anyMatch(x -> !Objects.equals(x.getPlanning().getId(), planningId)
                        || !x.getDateDebut().equals(periode.debut()));
        if (autreAstreinte) {
            return "déjà d'astreinte sur cette période";
        }
        return null;
    }

    // =====================================================================
    //  Outils
    // =====================================================================

    private Planning trouverPlanning(Long id) {
        Planning p = plannings.findById(id)
                .orElseThrow(() -> RegleMetierException.introuvable("Planning introuvable."));
        if (p.getType() != TypePlanning.GARDES) {
            throw RegleMetierException.conflit("Ce planning n'est pas un planning de gardes.");
        }
        return p;
    }

    private PlageHoraire trouverPlage(Planning planning, Long plageId) {
        PlageHoraire p = plages.findById(plageId)
                .orElseThrow(() -> RegleMetierException.introuvable("Colonne introuvable."));
        if (!p.getSousService().getService().getId().equals(planning.getService().getId())) {
            throw RegleMetierException.conflit("Cette colonne n'appartient pas au service du planning.");
        }
        return p;
    }

    private static void verifierDate(Planning p, LocalDate date) {
        if (date.isBefore(p.premierJour()) || date.isAfter(p.dernierJour())) {
            throw RegleMetierException.conflit("Cette date n'est pas dans le mois du planning.");
        }
    }

    private static Periode trouverPeriode(Planning p, LocalDate dateDebut) {
        return Periodes.astreintes(p.getAnnee(), p.getMois()).stream()
                .filter(x -> x.debut().equals(dateDebut)).findFirst()
                .orElseThrow(() -> RegleMetierException.introuvable("Période d'astreinte introuvable."));
    }

    private Creneau trouverOuCreerCreneau(Planning planning, PlageHoraire plage, LocalDate date) {
        return creneaux.findByPlageHoraireIdAndDateGarde(plage.getId(), date).orElseGet(() -> {
            Creneau c = new Creneau();
            c.setPlanning(planning);
            c.setPlageHoraire(plage);
            c.setDateGarde(date);
            c.setDebut(debut(plage, date));
            c.setFin(fin(plage, date));
            c.setMode(plage.getModeParDefaut());
            return creneaux.saveAndFlush(c);
        });
    }

    private static LocalDateTime debut(PlageHoraire p, LocalDate date) {
        return date.atTime(p.getHeureDebut());
    }

    private static LocalDateTime fin(PlageHoraire p, LocalDate date) {
        return (p.finitLeLendemain() ? date.plusDays(1) : date).atTime(p.getHeureFin());
    }

    /**
     * N° de référence du document : celui du planning, sinon celui du service
     * (utile si la référence a été renseignée dans « Services » après la création du planning).
     */
    private static String reference(Planning planning, sn.mediplan.parametrage.ServiceHospitalier service) {
        if (planning != null && planning.getNumeroReference() != null && !planning.getNumeroReference().isBlank()) {
            return planning.getNumeroReference();
        }
        return service.getReferenceDocument();
    }
}
