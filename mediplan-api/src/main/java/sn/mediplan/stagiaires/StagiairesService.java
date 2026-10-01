package sn.mediplan.stagiaires;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.commun.RegleMetierException;
import sn.mediplan.conges.Conge;
import sn.mediplan.conges.CongeRepository;
import sn.mediplan.parametrage.PosteStage;
import sn.mediplan.parametrage.PosteStageRepository;
import sn.mediplan.parametrage.ServiceHospitalier;
import sn.mediplan.parametrage.ServiceHospitalierRepository;
import sn.mediplan.personnel.Agent;
import sn.mediplan.personnel.AgentRepository;
import sn.mediplan.personnel.CategorieAgent;
import sn.mediplan.personnel.Stage;
import sn.mediplan.personnel.StageRepository;
import sn.mediplan.planning.Periodes;
import sn.mediplan.planning.Planning;
import sn.mediplan.planning.PlanningDtos.Candidat;
import sn.mediplan.planning.PlanningDtos.CongeDuMois;
import sn.mediplan.planning.PlanningDtos.Place;
import sn.mediplan.planning.PlanningRepository;
import sn.mediplan.planning.StatutPlanning;
import sn.mediplan.planning.TypePlanning;
import sn.mediplan.stagiaires.StagiairesDtos.AffectationStageRequete;
import sn.mediplan.stagiaires.StagiairesDtos.CaseStage;
import sn.mediplan.stagiaires.StagiairesDtos.ColonnePoste;
import sn.mediplan.stagiaires.StagiairesDtos.GrilleStagiaires;
import sn.mediplan.stagiaires.StagiairesDtos.LiberationStageRequete;
import sn.mediplan.stagiaires.StagiairesDtos.PeriodeRequete;
import sn.mediplan.stagiaires.StagiairesDtos.Semaine;
import sn.mediplan.stagiaires.StagiairesDtos.StatistiquesStage;

/**
 * Organisation mensuelle des étudiants stagiaires : une ligne par semaine, une colonne par poste,
 * deux stagiaires au plus par case.
 *
 * Règles appliquées à chaque affectation :
 * 1. une place déjà attribuée est verrouillée : il faut la libérer avant de la donner à un autre stagiaire ;
 * 2. le stagiaire doit être en stage dans le service pendant la semaine, et ne pas être en congé ;
 * 3. un stagiaire n'a qu'un seul poste principal par semaine ; un poste « partiel » (ex. VPA le mardi et
 *    le mercredi) peut s'ajouter à son poste principal.
 */
@Service
public class StagiairesService {

    private static final DateTimeFormatter JJ = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter JJ_MM = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter JJ_MM_AA = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final PlanningRepository plannings;
    private final PeriodeStageRepository periodes;
    private final AffectationStageRepository affectations;
    private final PosteStageRepository postes;
    private final ServiceHospitalierRepository services;
    private final AgentRepository agents;
    private final StageRepository stages;
    private final CongeRepository conges;

    public StagiairesService(PlanningRepository plannings, PeriodeStageRepository periodes,
            AffectationStageRepository affectations, PosteStageRepository postes,
            ServiceHospitalierRepository services, AgentRepository agents, StageRepository stages,
            CongeRepository conges) {
        this.plannings = plannings;
        this.periodes = periodes;
        this.affectations = affectations;
        this.postes = postes;
        this.services = services;
        this.agents = agents;
        this.stages = stages;
        this.conges = conges;
    }

    // =====================================================================
    //  Lecture de la grille
    // =====================================================================

    @Transactional(readOnly = true)
    public GrilleStagiaires grille(Long serviceId, int annee, int mois) {
        ServiceHospitalier service = services.findById(serviceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Service introuvable."));
        Optional<Planning> planning = plannings.findByServiceIdAndTypeAndAnneeAndMois(serviceId,
                TypePlanning.STAGIAIRES, annee, mois);
        return construire(service, annee, mois, planning.orElse(null));
    }

    @Transactional(readOnly = true)
    public GrilleStagiaires grille(Long planningId) {
        Planning p = trouverPlanning(planningId);
        return construire(p.getService(), p.getAnnee(), p.getMois(), p);
    }

    private GrilleStagiaires construire(ServiceHospitalier service, int annee, int mois, Planning planning) {
        LocalDate premier = LocalDate.of(annee, mois, 1);
        LocalDate dernier = premier.withDayOfMonth(premier.lengthOfMonth());

        List<PeriodeStage> ps = planning == null ? List.of()
                : periodes.findByPlanningIdOrderByDateDebutAsc(planning.getId());
        List<AffectationStage> aff = planning == null ? List.of() : affectations.findByPlanning(planning.getId());

        // Colonnes : postes actifs du service (+ postes archivés déjà utilisés ce mois-ci)
        Set<Long> postesUtilises = aff.stream().map(a -> a.getPoste().getId()).collect(Collectors.toSet());
        List<ColonnePoste> colonnes = postes.findAllOrdonnes().stream()
                .filter(p -> p.getService().getId().equals(service.getId()))
                .filter(p -> p.isActif() || postesUtilises.contains(p.getId()))
                .map(p -> new ColonnePoste(p.getId(), p.getLibelle(), p.getJoursActifs(), p.isPartiel(),
                        p.getCouleur()))
                .toList();

        // Lignes : les semaines (les semaines par défaut sont proposées tant que le planning n'existe pas)
        List<Semaine> semaines = planning == null
                ? Periodes.semaines(annee, mois).stream()
                        .map(p -> new Semaine(null, p.debut(), p.fin(), libelle(p.debut(), p.fin()))).toList()
                : ps.stream().map(p -> new Semaine(p.getId(), p.getDateDebut(), p.getDateFin(),
                        libelle(p.getDateDebut(), p.getDateFin()))).toList();

        // Cases remplies
        Map<String, List<AffectationStage>> parCase = aff.stream()
                .collect(Collectors.groupingBy(a -> cle(a.getPeriode().getId(), a.getPoste().getId())));
        List<CaseStage> cases = parCase.values().stream()
                .map(l -> new CaseStage(l.get(0).getPeriode().getId(), l.get(0).getPoste().getId(),
                        l.stream().sorted(Comparator.comparingInt(AffectationStage::getPosition))
                                .map(a -> new Place(a.getPosition(), a.getAgent().getId(),
                                        a.getAgent().getNomAffichage()))
                                .toList()))
                .toList();

        // Bornes réelles du planning (la 1re semaine peut commencer le mois précédent)
        LocalDate debutGrille = semaines.stream().map(Semaine::dateDebut).min(Comparator.naturalOrder())
                .orElse(premier);
        LocalDate finGrille = semaines.stream().map(Semaine::dateFin).max(Comparator.naturalOrder())
                .orElse(dernier);

        // Stagiaires du service en stage pendant le planning
        List<Agent> stagiaires = stagiairesDuService(service.getId());
        Set<Long> enStage = stages.findChevauchant(debutGrille, finGrille).stream()
                .filter(s -> s.getService().getId().equals(service.getId()))
                .map(s -> s.getAgent().getId()).collect(Collectors.toSet());
        Set<Long> ids = stagiaires.stream().map(Agent::getId).filter(enStage::contains).collect(Collectors.toSet());
        Set<Long> avecPoste = aff.stream().map(a -> a.getAgent().getId()).collect(Collectors.toSet());

        // Congés des stagiaires qui touchent le planning
        List<CongeDuMois> congesDuMois = conges.findChevauchant(debutGrille, finGrille).stream()
                .filter(Conge::isAfficheSurPdf)
                .filter(c -> ids.contains(c.getAgent().getId()))
                .map(c -> new CongeDuMois(c.getAgent().getNomAffichage(), c.getDateDebut(), c.getDateFin()))
                .toList();

        int casesTotales = colonnes.size() * semaines.size();
        int casesRemplies = cases.size();
        int sansPoste = (int) ids.stream().filter(id -> !avecPoste.contains(id)).count();

        return new GrilleStagiaires(planning == null ? null : planning.getId(),
                planning == null ? null : planning.getStatut(),
                service.getId(), service.getNom(), annee, mois,
                planning == null ? null : planning.getDateDocument(),
                reference(planning, service),
                colonnes, semaines, cases, congesDuMois,
                new StatistiquesStage(casesTotales, casesRemplies, ids.size(), sansPoste));
    }

    /** « Du 31/08 au 06/09/26 » ou « Du 07 au 13/09/26 » (même présentation que le document officiel). */
    public static String libelle(LocalDate debut, LocalDate fin) {
        String d = debut.getMonth() == fin.getMonth() && debut.getYear() == fin.getYear() ? debut.format(JJ)
                : debut.format(JJ_MM);
        return "Du " + d + " au " + fin.format(JJ_MM_AA);
    }

    private static String cle(Long periodeId, Long posteId) {
        return periodeId + "|" + posteId;
    }

    // =====================================================================
    //  Création, statut et semaines
    // =====================================================================

    @Transactional
    public Long creer(Long serviceId, int annee, int mois) {
        ServiceHospitalier service = services.findById(serviceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Service introuvable."));
        if (plannings.findByServiceIdAndTypeAndAnneeAndMois(serviceId, TypePlanning.STAGIAIRES, annee, mois)
                .isPresent()) {
            throw RegleMetierException.conflit("L'organisation des stagiaires de ce mois existe déjà.");
        }
        Planning p = new Planning();
        p.setService(service);
        p.setType(TypePlanning.STAGIAIRES);
        p.setAnnee(annee);
        p.setMois(mois);
        p.setStatut(StatutPlanning.BROUILLON);
        p.setDateDocument(LocalDate.now());
        p.setNumeroReference(service.getReferenceDocument());
        plannings.save(p);

        int ordre = 1;
        for (Periodes.Periode s : Periodes.semaines(annee, mois)) {
            PeriodeStage ps = new PeriodeStage();
            ps.setPlanning(p);
            ps.setDateDebut(s.debut());
            ps.setDateFin(s.fin());
            ps.setOrdre(ordre++);
            periodes.save(ps);
        }
        return p.getId();
    }

    @Transactional
    public void changerStatut(Long planningId, StatutPlanning statut) {
        Planning p = trouverPlanning(planningId);
        if (statut == StatutPlanning.PUBLIE && p.getStatut() != StatutPlanning.PUBLIE) {
            p.setDateDocument(LocalDate.now());
        }
        p.setStatut(statut);
    }

    @Transactional
    public void ajouterPeriode(Long planningId, PeriodeRequete r) {
        Planning planning = trouverPlanning(planningId);
        verifierDates(planning, null, r.dateDebut(), r.dateFin());
        PeriodeStage ps = new PeriodeStage();
        ps.setPlanning(planning);
        ps.setDateDebut(r.dateDebut());
        ps.setDateFin(r.dateFin());
        periodes.saveAndFlush(ps);
        renumeroter(planningId);
    }

    @Transactional
    public void modifierPeriode(Long planningId, Long periodeId, PeriodeRequete r) {
        Planning planning = trouverPlanning(planningId);
        PeriodeStage ps = trouverPeriode(planning, periodeId);
        verifierDates(planning, periodeId, r.dateDebut(), r.dateFin());

        // Les stagiaires déjà placés doivent rester disponibles sur les nouvelles dates
        List<AffectationStage> placees = affectations.findByPeriode(periodeId);
        if (!placees.isEmpty()) {
            Contexte ctx = contexte(planning, r.dateDebut(), r.dateFin());
            for (AffectationStage a : placees) {
                String raison = raisonIndisponible(a.getAgent(), a.getPoste(), r.dateDebut(), r.dateFin(), ctx,
                        periodeId);
                if (raison != null) {
                    throw RegleMetierException.conflit("Impossible de changer ces dates : "
                            + a.getAgent().getNomAffichage() + " (" + a.getPoste().getLibelle() + ") serait "
                            + raison + ". Libérez sa place d'abord.");
                }
            }
        }
        ps.setDateDebut(r.dateDebut());
        ps.setDateFin(r.dateFin());
        periodes.flush();
        renumeroter(planningId);
    }

    @Transactional
    public void supprimerPeriode(Long planningId, Long periodeId) {
        Planning planning = trouverPlanning(planningId);
        PeriodeStage ps = trouverPeriode(planning, periodeId);
        affectations.deleteAll(affectations.findByPeriode(periodeId));
        periodes.delete(ps);
        periodes.flush();
        renumeroter(planningId);
    }

    private void verifierDates(Planning planning, Long periodeId, LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) {
            throw RegleMetierException.conflit("La date de fin doit être après la date de début.");
        }
        if (ChronoUnit.DAYS.between(debut, fin) > 30) {
            throw RegleMetierException.conflit("Une semaine de stage ne peut pas dépasser 31 jours.");
        }
        if (fin.isBefore(planning.premierJour()) || debut.isAfter(planning.dernierJour())) {
            throw RegleMetierException.conflit("Cette période doit toucher le mois du planning.");
        }
        for (PeriodeStage autre : periodes.findByPlanningIdOrderByDateDebutAsc(planning.getId())) {
            if (!Objects.equals(autre.getId(), periodeId) && !autre.getDateDebut().isAfter(fin)
                    && !autre.getDateFin().isBefore(debut)) {
                throw RegleMetierException.conflit("Ces dates chevauchent la ligne « "
                        + libelle(autre.getDateDebut(), autre.getDateFin()) + " ».");
            }
        }
    }

    private void renumeroter(Long planningId) {
        int ordre = 1;
        for (PeriodeStage p : periodes.findByPlanningIdOrderByDateDebutAsc(planningId)) {
            p.setOrdre(ordre++);
        }
    }

    // =====================================================================
    //  Candidats, affecter, libérer
    // =====================================================================

    /** Tous les stagiaires du service, disponibles d'abord, avec la raison quand ils ne le sont pas. */
    @Transactional(readOnly = true)
    public List<Candidat> candidats(Long planningId, Long periodeId, Long posteId) {
        Planning planning = trouverPlanning(planningId);
        PeriodeStage periode = trouverPeriode(planning, periodeId);
        PosteStage poste = trouverPoste(planning, posteId);
        Contexte ctx = contexte(planning, periode.getDateDebut(), periode.getDateFin());

        Set<Long> dansLaCase = affectations.findByPeriode(periodeId).stream()
                .filter(a -> a.getPoste().getId().equals(posteId))
                .map(a -> a.getAgent().getId()).collect(Collectors.toSet());

        return ctx.stagiaires.stream()
                .map(a -> {
                    String raison = dansLaCase.contains(a.getId()) ? "Déjà dans cette case"
                            : raisonIndisponible(a, poste, periode.getDateDebut(), periode.getDateFin(), ctx,
                                    null);
                    return new Candidat(a.getId(), a.getNomAffichage(), a.getPrenom() + " " + a.getNom(),
                            a.getProfession() != null ? a.getProfession().getLibelle() : null, a.getCategorie(),
                            raison == null, raison, ctx.postesDuMois.getOrDefault(a.getId(), 0));
                })
                .sorted(Comparator.comparing((Candidat c) -> !c.disponible())
                        .thenComparingInt(Candidat::gardesDuMois)
                        .thenComparing(Candidat::nomAffichage, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public void affecter(Long planningId, AffectationStageRequete r) {
        Planning planning = trouverPlanning(planningId);
        PeriodeStage periode = trouverPeriode(planning, r.periodeId());
        PosteStage poste = trouverPoste(planning, r.posteId());

        List<AffectationStage> dansLaCase = affectations.findByPeriode(periode.getId()).stream()
                .filter(a -> a.getPoste().getId().equals(poste.getId())).toList();
        dansLaCase.stream().filter(a -> a.getPosition() == r.position()).findFirst().ifPresent(a -> {
            throw RegleMetierException.conflit("Cette place est déjà attribuée à " + a.getAgent().getNomAffichage()
                    + ". Libérez-la d'abord pour la donner à quelqu'un d'autre.");
        });
        if (dansLaCase.stream().anyMatch(a -> a.getAgent().getId().equals(r.agentId()))) {
            throw RegleMetierException.conflit("Ce stagiaire est déjà dans cette case.");
        }

        Agent agent = agents.findById(r.agentId())
                .orElseThrow(() -> RegleMetierException.introuvable("Stagiaire introuvable."));
        Contexte ctx = contexte(planning, periode.getDateDebut(), periode.getDateFin());
        if (ctx.stagiaires.stream().noneMatch(a -> a.getId().equals(agent.getId()))) {
            throw RegleMetierException.conflit(agent.getNomAffichage()
                    + " n'est pas un stagiaire actif du service " + planning.getService().getNom() + ".");
        }
        String raison = raisonIndisponible(agent, poste, periode.getDateDebut(), periode.getDateFin(), ctx, null);
        if (raison != null) {
            throw RegleMetierException.conflit(agent.getNomAffichage() + " : " + raison + ".");
        }

        AffectationStage a = new AffectationStage();
        a.setPeriode(periode);
        a.setPoste(poste);
        a.setAgent(agent);
        a.setPosition(r.position());
        affectations.saveAndFlush(a); // la contrainte unique de la base reste le dernier verrou
    }

    @Transactional
    public void liberer(Long planningId, LiberationStageRequete r) {
        Planning planning = trouverPlanning(planningId);
        trouverPeriode(planning, r.periodeId());
        AffectationStage a = affectations.findByPeriode(r.periodeId()).stream()
                .filter(x -> x.getPoste().getId().equals(r.posteId()) && x.getPosition() == r.position())
                .findFirst()
                .orElseThrow(() -> RegleMetierException.introuvable("Cette place est déjà libre."));
        affectations.delete(a);
    }

    // =====================================================================
    //  Disponibilité d'un stagiaire
    // =====================================================================

    private record Contexte(Long serviceId, List<Agent> stagiaires, Map<Long, List<Stage>> stages,
            Map<Long, List<Conge>> conges, Map<Long, List<AffectationStage>> postes,
            Map<Long, Integer> postesDuMois) {
    }

    private Contexte contexte(Planning planning, LocalDate debut, LocalDate fin) {
        Long serviceId = planning.getService().getId();
        Map<Long, List<Stage>> st = stages.findChevauchant(debut, fin).stream()
                .filter(s -> s.getService().getId().equals(serviceId))
                .collect(Collectors.groupingBy(s -> s.getAgent().getId()));
        Map<Long, List<Conge>> cg = conges.findChevauchant(debut, fin).stream()
                .collect(Collectors.groupingBy(c -> c.getAgent().getId()));
        Map<Long, List<AffectationStage>> ps = affectations.findDansPeriode(debut, fin).stream()
                .collect(Collectors.groupingBy(a -> a.getAgent().getId()));
        Map<Long, Integer> duMois = new HashMap<>();
        for (AffectationStage a : affectations.findByPlanning(planning.getId())) {
            duMois.merge(a.getAgent().getId(), 1, Integer::sum);
        }
        return new Contexte(serviceId, stagiairesDuService(serviceId), st, cg, ps, duMois);
    }

    /**
     * Null si le stagiaire peut tenir ce poste du {@code debut} au {@code fin}, sinon la raison lisible.
     * {@code periodeIgnoree} : semaine dont on change les dates (ses propres affectations ne comptent pas).
     */
    private String raisonIndisponible(Agent a, PosteStage poste, LocalDate debut, LocalDate fin, Contexte ctx,
            Long periodeIgnoree) {
        boolean enStage = ctx.stages.getOrDefault(a.getId(), List.of()).stream()
                .anyMatch(s -> !s.getDateDebut().isAfter(fin) && !s.getDateFin().isBefore(debut));
        if (!enStage) {
            return "hors de sa période de stage";
        }
        for (Conge c : ctx.conges.getOrDefault(a.getId(), List.of())) {
            if (!c.getDateDebut().isAfter(fin) && !c.getDateFin().isBefore(debut)) {
                return "en congé du " + c.getDateDebut().format(JJ_MM) + " au " + c.getDateFin().format(JJ_MM);
            }
        }
        if (!poste.isPartiel()) {
            for (AffectationStage x : ctx.postes.getOrDefault(a.getId(), List.of())) {
                boolean memePeriode = Objects.equals(x.getPeriode().getId(), periodeIgnoree);
                boolean chevauche = !x.getPeriode().getDateDebut().isAfter(fin)
                        && !x.getPeriode().getDateFin().isBefore(debut);
                if (!memePeriode && chevauche && !x.getPoste().isPartiel()
                        && !x.getPoste().getId().equals(poste.getId())) {
                    return "déjà en " + x.getPoste().getLibelle() + " ("
                            + libelle(x.getPeriode().getDateDebut(), x.getPeriode().getDateFin()).toLowerCase()
                            + ")";
                }
            }
        }
        return null;
    }

    private List<Agent> stagiairesDuService(Long serviceId) {
        return agents.findActifsDuService(serviceId).stream()
                .filter(a -> a.getCategorie() == CategorieAgent.STAGIAIRE).toList();
    }

    // =====================================================================
    //  Outils
    // =====================================================================

    public Planning trouverPlanning(Long id) {
        Planning p = plannings.findById(id)
                .orElseThrow(() -> RegleMetierException.introuvable("Planning introuvable."));
        if (p.getType() != TypePlanning.STAGIAIRES) {
            throw RegleMetierException.conflit("Ce planning n'est pas une organisation des stagiaires.");
        }
        return p;
    }

    private PeriodeStage trouverPeriode(Planning planning, Long periodeId) {
        PeriodeStage p = periodes.findById(periodeId)
                .orElseThrow(() -> RegleMetierException.introuvable("Semaine introuvable."));
        if (!p.getPlanning().getId().equals(planning.getId())) {
            throw RegleMetierException.conflit("Cette semaine n'appartient pas à ce planning.");
        }
        return p;
    }

    private PosteStage trouverPoste(Planning planning, Long posteId) {
        PosteStage p = postes.findById(posteId)
                .orElseThrow(() -> RegleMetierException.introuvable("Poste introuvable."));
        if (!p.getService().getId().equals(planning.getService().getId())) {
            throw RegleMetierException.conflit("Ce poste n'appartient pas au service du planning.");
        }
        return p;
    }

    /** Pour le courriel : « Du 31/08 au 06/09/26 : Salle Prog.1 » pour chaque poste du stagiaire. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> postesParStagiaire(Long planningId) {
        Map<Long, List<String>> m = new HashMap<>();
        affectations.findByPlanning(planningId).stream()
                .sorted(Comparator.comparing((AffectationStage a) -> a.getPeriode().getDateDebut())
                        .thenComparingInt(a -> a.getPoste().getOrdreAffichage()))
                .forEach(a -> m.computeIfAbsent(a.getAgent().getId(), k -> new ArrayList<>())
                        .add(libelle(a.getPeriode().getDateDebut(), a.getPeriode().getDateFin()) + " : "
                                + a.getPoste().getLibelle()
                                + (a.getPoste().getJoursActifs() != null && !a.getPoste().getJoursActifs().isBlank()
                                        ? " (" + joursLisibles(a.getPoste().getJoursActifs()) + ")"
                                        : "")));
        return m;
    }

    /** « MARDI,MERCREDI » → « Mardi-Mercredi » */
    public static String joursLisibles(String jours) {
        if (jours == null || jours.isBlank()) {
            return "";
        }
        Set<String> vus = new HashSet<>();
        List<String> l = new ArrayList<>();
        for (String j : jours.split("[,;\\s]+")) {
            String t = j.trim().toLowerCase(java.util.Locale.FRENCH);
            if (!t.isEmpty() && vus.add(t)) {
                l.add(Character.toUpperCase(t.charAt(0)) + t.substring(1));
            }
        }
        return String.join("-", l);
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
