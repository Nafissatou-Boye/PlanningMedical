package sn.mediplan.diffusion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import sn.mediplan.commun.RegleMetierException;
import sn.mediplan.diffusion.DiffusionDtos.Destinataire;
import sn.mediplan.diffusion.DiffusionDtos.LigneJournal;
import sn.mediplan.diffusion.DiffusionDtos.ResultatEnvoi;
import sn.mediplan.personnel.Agent;
import sn.mediplan.personnel.AgentRepository;
import sn.mediplan.personnel.CategorieAgent;
import sn.mediplan.planning.Planning;
import sn.mediplan.planning.PlanningDtos.ColonnePlage;
import sn.mediplan.planning.PlanningDtos.ColonneSousService;
import sn.mediplan.planning.PlanningDtos.Grille;
import sn.mediplan.planning.PlanningDtos.Jour;
import sn.mediplan.planning.PlanningDtos.PeriodeAstreinte;
import sn.mediplan.planning.PlanningRepository;
import sn.mediplan.planning.PlanningService;
import sn.mediplan.stagiaires.StagiairesDtos.GrilleStagiaires;
import sn.mediplan.stagiaires.StagiairesService;
import sn.mediplan.utilisateur.Utilisateur;
import sn.mediplan.utilisateur.UtilisateurRepository;

/**
 * Envoi du PDF par e-mail. Chaque agent reçoit un message personnalisé avec la liste de SES gardes
 * et le planning complet en pièce jointe. Chaque envoi est inscrit au journal.
 */
@Service
public class DiffusionService {

    private static final Logger LOG = LoggerFactory.getLogger(DiffusionService.class);
    private static final String[] MOIS = { "janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août",
            "septembre", "octobre", "novembre", "décembre" };
    private static final DateTimeFormatter JJ_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final PlanningService plannings;
    private final PlanningRepository planningRepository;
    private final PdfPlanningService pdf;
    private final AgentRepository agents;
    private final EnvoiPdfRepository envois;
    private final UtilisateurRepository utilisateurs;
    private final StagiairesService stagiaires;
    private final PdfStagiairesService pdfStagiaires;
    private final EnvoiEmail envoiEmail;

    public DiffusionService(PlanningService plannings, PlanningRepository planningRepository, PdfPlanningService pdf,
            AgentRepository agents, EnvoiPdfRepository envois, UtilisateurRepository utilisateurs,
            StagiairesService stagiaires, PdfStagiairesService pdfStagiaires,
            EnvoiEmail envoiEmail) {
        this.plannings = plannings;
        this.planningRepository = planningRepository;
        this.pdf = pdf;
        this.agents = agents;
        this.envois = envois;
        this.utilisateurs = utilisateurs;
        this.stagiaires = stagiaires;
        this.pdfStagiaires = pdfStagiaires;
        this.envoiEmail = envoiEmail;
    }

    /** État de la configuration e-mail, pour le Centre d'aide (sans jamais exposer le mot de passe). */
    public record ConfigurationEmail(boolean configure, String expediteur) {
    }

    public ConfigurationEmail configuration() {
        String a = envoiEmail.adresse();
        return new ConfigurationEmail(envoiEmail.estConfigure(), a.isBlank() ? null : masquer(a));
    }

    /** « boye.adji@gmail.com » → « bo•••@gmail.com » */
    private static String masquer(String email) {
        int a = email.indexOf('@');
        return a <= 2 ? "•••" + email.substring(Math.max(a, 0)) : email.substring(0, 2) + "•••" + email.substring(a);
    }

    // ---------- Destinataires ----------

    @Transactional(readOnly = true)
    public List<Destinataire> destinataires(Long planningId) {
        Grille g = plannings.grille(planningId);
        Map<Long, int[]> compteurs = compter(g);

        Map<Long, EnvoiPdf> dernier = derniersEnvois(planningId);
        return agents.findActifsDuService(g.serviceId()).stream()
                .map(a -> {
                    int[] c = compteurs.getOrDefault(a.getId(), new int[2]);
                    EnvoiPdf e = dernier.get(a.getId());
                    return new Destinataire(a.getId(), a.getNomAffichage(), a.getEmail(), c[0], c[1],
                            e == null ? null : e.getStatut(), e == null ? null : e.getDateEnvoi());
                })
                .sorted(Comparator.comparing((Destinataire d) -> d.gardes() + d.astreintes() == 0)
                        .thenComparing(Destinataire::nomAffichage, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LigneJournal> journal(Long planningId) {
        return envois.findByPlanning(planningId).stream()
                .map(e -> new LigneJournal(e.getAgent() != null ? e.getAgent().getNomAffichage() : "—",
                        e.getEmailDestinataire(), e.getDateEnvoi(), e.getStatut(), e.getMessageErreur()))
                .toList();
    }

    // ---------- Envoi ----------

    @Transactional
    public List<ResultatEnvoi> envoyer(Long planningId, List<Long> agentIds, String messagePerso,
            String nomUtilisateurConnecte) {
        JavaMailSender mail = verifierConfiguration();
        Grille g = plannings.grille(planningId);
        Map<Long, List<String>> gardesParAgent = detailGardes(g);
        String periode = MOIS[g.mois() - 1] + " " + g.annee();
        Utilisateur auteur = utilisateurs.findByNomUtilisateurIgnoreCase(nomUtilisateurConnecte).orElse(null);
        return envoyerA(mail, planningId, agentIds, pdf.generer(planningId), pdf.nomFichier(g), g.service(),
                "Planning des gardes " + g.service() + " – " + periode,
                a -> corps(a, "le planning des gardes", g.service(), periode,
                        gardesParAgent.getOrDefault(a.getId(), List.of()), "Vos gardes et astreintes :",
                        "Vous n'avez aucune garde ni astreinte ce mois-ci.", messagePerso, auteur));
    }

    // ---------- Organisation des stagiaires ----------

    @Transactional(readOnly = true)
    public List<Destinataire> destinatairesStagiaires(Long planningId) {
        GrilleStagiaires g = stagiaires.grille(planningId);
        Map<Long, Integer> postes = new HashMap<>();
        g.cases().forEach(c -> c.stagiaires().forEach(p -> postes.merge(p.agentId(), 1, Integer::sum)));
        Map<Long, EnvoiPdf> dernier = derniersEnvois(planningId);
        return agents.findActifsDuService(g.serviceId()).stream()
                .filter(a -> a.getCategorie() == CategorieAgent.STAGIAIRE)
                .map(a -> {
                    EnvoiPdf e = dernier.get(a.getId());
                    return new Destinataire(a.getId(), a.getNomAffichage(), a.getEmail(),
                            postes.getOrDefault(a.getId(), 0), 0,
                            e == null ? null : e.getStatut(), e == null ? null : e.getDateEnvoi());
                })
                .sorted(Comparator.comparing((Destinataire d) -> d.gardes() == 0)
                        .thenComparing(Destinataire::nomAffichage, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public List<ResultatEnvoi> envoyerStagiaires(Long planningId, List<Long> agentIds, String messagePerso,
            String nomUtilisateurConnecte) {
        JavaMailSender mail = verifierConfiguration();
        GrilleStagiaires g = stagiaires.grille(planningId);
        Map<Long, List<String>> postesParAgent = stagiaires.postesParStagiaire(planningId);
        String periode = MOIS[g.mois() - 1] + " " + g.annee();
        Utilisateur auteur = utilisateurs.findByNomUtilisateurIgnoreCase(nomUtilisateurConnecte).orElse(null);
        return envoyerA(mail, planningId, agentIds, pdfStagiaires.generer(planningId),
                pdfStagiaires.nomFichier(g), g.service(),
                "Organisation des stagiaires " + g.service() + " – " + periode,
                a -> corps(a, "l'organisation des étudiants stagiaires", g.service(), periode,
                        postesParAgent.getOrDefault(a.getId(), List.of()), "Vos postes :",
                        "Aucun poste ne vous est attribué ce mois-ci.", messagePerso, auteur));
    }

    // ---------- Envoi commun ----------

    private JavaMailSender verifierConfiguration() {
        if (!envoiEmail.estConfigure()) {
            throw RegleMetierException.conflit("L'envoi des e-mails n'est pas encore configuré : "
                    + "allez dans Paramètres › Envoi des e-mails pour indiquer l'adresse Gmail qui enverra les plannings.");
        }
        return envoiEmail.expediteur();
    }

    private List<ResultatEnvoi> envoyerA(JavaMailSender mail, Long planningId, List<Long> agentIds, byte[] fichier,
            String nomFichier, String service, String sujet, Function<Agent, String> corps) {
        Planning planning = planningRepository.findById(planningId)
                .orElseThrow(() -> RegleMetierException.introuvable("Planning introuvable."));
        List<ResultatEnvoi> resultats = new ArrayList<>();
        for (Agent a : agents.findAllById(agentIds)) {
            if (a.getEmail() == null || a.getEmail().isBlank()) {
                resultats.add(new ResultatEnvoi(a.getId(), a.getNomAffichage(), null, StatutEnvoi.ECHEC,
                        "Pas d'adresse e-mail dans sa fiche."));
                continue;
            }
            EnvoiPdf trace = new EnvoiPdf();
            trace.setPlanning(planning);
            trace.setAgent(a);
            trace.setEmailDestinataire(a.getEmail());
            trace.setDateEnvoi(LocalDateTime.now());
            try {
                MimeMessage message = mail.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(message, true, "UTF-8");
                h.setFrom(envoiEmail.adresse(), "MediPlan – " + service);
                h.setTo(a.getEmail());
                h.setSubject(sujet);
                h.setText(corps.apply(a), false);
                h.addAttachment(nomFichier, new ByteArrayResource(fichier), "application/pdf");
                mail.send(message);
                trace.setStatut(StatutEnvoi.ENVOYE);
                resultats.add(new ResultatEnvoi(a.getId(), a.getNomAffichage(), a.getEmail(), StatutEnvoi.ENVOYE,
                        null));
            } catch (Exception e) {
                LOG.warn("Échec d'envoi à {} : {}", a.getEmail(), e.getMessage());
                String erreur = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                trace.setStatut(StatutEnvoi.ECHEC);
                trace.setMessageErreur(erreur.length() > 500 ? erreur.substring(0, 500) : erreur);
                resultats.add(new ResultatEnvoi(a.getId(), a.getNomAffichage(), a.getEmail(), StatutEnvoi.ECHEC,
                        "L'envoi a échoué : " + trace.getMessageErreur()));
            }
            envois.save(trace);
        }
        return resultats;
    }

    private Map<Long, EnvoiPdf> derniersEnvois(Long planningId) {
        Map<Long, EnvoiPdf> dernier = new HashMap<>();
        for (EnvoiPdf e : envois.findByPlanning(planningId)) { // plus récents d'abord
            if (e.getAgent() != null) {
                dernier.putIfAbsent(e.getAgent().getId(), e);
            }
        }
        return dernier;
    }

    private static String corps(Agent a, String quoi, String service, String periode, List<String> lignes,
            String titreLignes, String aucun, String messagePerso, Utilisateur auteur) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bonjour ").append(a.getNomAffichage()).append(",\n\n");
        sb.append("Veuillez trouver ci-joint ").append(quoi).append(" du service ").append(service)
                .append(" pour ").append(periode).append(".\n\n");
        if (messagePerso != null && !messagePerso.isBlank()) {
            sb.append(messagePerso.trim()).append("\n\n");
        }
        if (lignes.isEmpty()) {
            sb.append(aucun).append('\n');
        } else {
            sb.append(titreLignes).append('\n');
            lignes.forEach(x -> sb.append("  • ").append(x).append('\n'));
        }
        sb.append("\nCordialement,\n");
        if (auteur != null) {
            sb.append(auteur.getPrenom()).append(' ').append(auteur.getNom()).append('\n').append(auteur.getPoste())
                    .append('\n');
        }
        sb.append("Service ").append(service).append('\n');
        return sb.toString();
    }

    // ---------- Outils ----------

    /** agentId → [nombre de gardes, nombre d'astreintes] */
    private static Map<Long, int[]> compter(Grille g) {
        Map<Long, int[]> m = new HashMap<>();
        g.cases().forEach(c -> c.agents().forEach(p -> m.computeIfAbsent(p.agentId(), k -> new int[2])[0]++));
        g.astreintes().stream().filter(a -> a.agentId() != null)
                .forEach(a -> m.computeIfAbsent(a.agentId(), k -> new int[2])[1]++);
        return m;
    }

    /** agentId → « Jeudi 01 : REA 08H-08H », dans l'ordre des jours puis des colonnes. */
    private static Map<Long, List<String>> detailGardes(Grille g) {
        Map<Long, ColonnePlage> plages = new LinkedHashMap<>();
        Map<Long, String> sousService = new HashMap<>();
        for (ColonneSousService ss : g.colonnes()) {
            for (ColonnePlage p : ss.plages()) {
                plages.put(p.plageId(), p);
                sousService.put(p.plageId(), ss.nom());
            }
        }
        Map<LocalDate, String> libelles = new HashMap<>();
        for (Jour j : g.jours()) {
            libelles.put(j.date(), j.libelle());
        }
        Map<Long, List<String>> m = new HashMap<>();
        g.cases().stream()
                .sorted(Comparator.comparing((sn.mediplan.planning.PlanningDtos.Case c) -> c.date()))
                .forEach(c -> c.agents().forEach(p -> {
                    ColonnePlage pl = plages.get(c.plageId());
                    String texte = libelles.get(c.date()) + " : " + sousService.get(c.plageId()) + " "
                            + (pl != null ? pl.libelle() : "");
                    m.computeIfAbsent(p.agentId(), k -> new ArrayList<>()).add(texte);
                }));
        for (PeriodeAstreinte a : g.astreintes()) {
            if (a.agentId() != null) {
                m.computeIfAbsent(a.agentId(), k -> new ArrayList<>())
                        .add("Astreinte du " + a.dateDebut().format(JJ_MM) + " au " + a.dateFin().format(JJ_MM));
            }
        }
        return m;
    }
}
