package sn.mediplan.personnel;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.commun.RegleMetierException;
import sn.mediplan.parametrage.ServiceHospitalier;
import sn.mediplan.parametrage.ServiceHospitalierRepository;
import sn.mediplan.personnel.PersonnelDtos.AgentDto;
import sn.mediplan.personnel.PersonnelDtos.AgentRequete;
import sn.mediplan.personnel.PersonnelDtos.ProfessionRequete;
import sn.mediplan.personnel.PersonnelDtos.ReferenceDto;
import sn.mediplan.personnel.PersonnelDtos.StageDto;
import sn.mediplan.personnel.PersonnelDtos.StageRequete;

@Service
public class PersonnelService {

    private final AgentRepository agents;
    private final StageRepository stages;
    private final ProfessionRepository professions;
    private final ServiceHospitalierRepository services;

    public PersonnelService(AgentRepository agents, StageRepository stages, ProfessionRepository professions,
            ServiceHospitalierRepository services) {
        this.agents = agents;
        this.stages = stages;
        this.professions = professions;
        this.services = services;
    }

    // ---------- Lecture ----------

    /** Liste complète (quelques centaines d'agents au plus) : le filtrage se fait à l'écran. */
    @Transactional(readOnly = true)
    public List<AgentDto> lister() {
        // Stage le plus récent de chaque agent
        Map<Long, Stage> dernierStage = new LinkedHashMap<>();
        for (Stage s : stages.findAllRecentsDabord()) {
            dernierStage.putIfAbsent(s.getAgent().getId(), s);
        }
        return agents.findAllComplets().stream().map(a -> versDto(a, dernierStage.get(a.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public AgentDto lire(Long id) {
        Agent a = trouver(id);
        Stage stage = stages.findByAgentIdOrderByDateDebutDesc(id).stream().findFirst().orElse(null);
        return versDto(a, stage);
    }

    private static AgentDto versDto(Agent a, Stage stage) {
        return new AgentDto(a.getId(), a.getMatricule(), a.getTitre(), a.getPrenom(), a.getNom(),
                a.getNomAffichage(), a.getEmail(), a.getTelephone(), a.getCategorie(),
                a.getProfession() != null ? a.getProfession().getId() : null,
                a.getProfession() != null ? a.getProfession().getLibelle() : null,
                a.getServices().stream().map(s -> new ReferenceDto(s.getId(), s.getNom())).toList(),
                a.isActif(),
                stage != null ? StageDto.de(stage) : null);
    }

    // ---------- Écriture ----------

    @Transactional
    public Long creer(AgentRequete r) {
        verifierMatricule(r.matricule(), null);
        Agent a = new Agent();
        appliquer(a, r);
        agents.save(a);
        enregistrerStage(a, r);
        return a.getId();
    }

    @Transactional
    public void modifier(Long id, AgentRequete r) {
        Agent a = trouver(id);
        verifierMatricule(r.matricule(), id);
        appliquer(a, r);
        if (r.actif() != null) {
            a.setActif(r.actif());
        }
        enregistrerStage(a, r);
    }

    private void appliquer(Agent a, AgentRequete r) {
        a.setTitre(nettoyer(r.titre()));
        a.setPrenom(r.prenom().trim());
        a.setNom(r.nom().trim());
        a.setMatricule(nettoyer(r.matricule()));
        a.setEmail(nettoyer(r.email()));
        a.setTelephone(nettoyer(r.telephone()));
        a.setCategorie(r.categorie());
        a.setNomAffichage(nettoyer(r.nomAffichage()) != null ? r.nomAffichage().trim() : nomAffichageParDefaut(r));

        a.setProfession(r.professionId() == null ? null
                : professions.findById(r.professionId())
                        .orElseThrow(() -> RegleMetierException.introuvable("Profession introuvable.")));

        List<ServiceHospitalier> choisis = services.findAllById(r.serviceIds());
        if (choisis.size() != new HashSet<>(r.serviceIds()).size()) {
            throw RegleMetierException.introuvable("Un des services choisis n'existe pas.");
        }
        a.getServices().clear();
        a.getServices().addAll(choisis);
    }

    /** « Dr » + « Bara » + « Aw » → « Dr Bara Aw » (tronqué à 60 caractères). */
    private static String nomAffichageParDefaut(AgentRequete r) {
        String titre = nettoyer(r.titre());
        String nom = (titre != null ? titre + " " : "") + r.prenom().trim() + " " + r.nom().trim();
        return nom.length() > 60 ? nom.substring(0, 60) : nom;
    }

    /** Un stagiaire doit avoir une période de stage ; on met à jour la plus récente. */
    private void enregistrerStage(Agent a, AgentRequete r) {
        if (r.categorie() != CategorieAgent.STAGIAIRE) {
            return;
        }
        StageRequete sr = r.stage();
        if (sr == null) {
            throw RegleMetierException.conflit("Indiquez les dates de début et de fin du stage.");
        }
        if (sr.dateFin().isBefore(sr.dateDebut())) {
            throw RegleMetierException.conflit("La fin du stage doit être après son début.");
        }
        Long serviceId = sr.serviceId() != null ? sr.serviceId() : r.serviceIds().get(0);
        ServiceHospitalier service = services.findById(serviceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Service d'accueil introuvable."));

        Stage stage = a.getId() == null ? null
                : stages.findByAgentIdOrderByDateDebutDesc(a.getId()).stream().findFirst().orElse(null);
        if (stage == null) {
            stage = new Stage();
            stage.setAgent(a);
        }
        stage.setService(service);
        stage.setNiveau(nettoyer(sr.niveau()));
        stage.setEtablissementOrigine(nettoyer(sr.etablissementOrigine()));
        stage.setDateDebut(sr.dateDebut());
        stage.setDateFin(sr.dateFin());
        stages.save(stage);
    }

    private void verifierMatricule(String matricule, Long id) {
        String m = nettoyer(matricule);
        if (m == null) {
            return;
        }
        boolean pris = id == null ? agents.existsByMatriculeIgnoreCase(m)
                : agents.existsByMatriculeIgnoreCaseAndIdNot(m, id);
        if (pris) {
            throw RegleMetierException.conflit("Le matricule « " + m + " » est déjà attribué à un autre agent.");
        }
    }

    private Agent trouver(Long id) {
        return agents.findById(id).orElseThrow(() -> RegleMetierException.introuvable("Agent introuvable."));
    }

    // ---------- Professions ----------

    @Transactional(readOnly = true)
    public List<ReferenceDto> professions() {
        return professions.findAllByOrderByLibelleAsc().stream()
                .map(p -> new ReferenceDto(p.getId(), p.getLibelle())).toList();
    }

    @Transactional
    public void creerProfession(ProfessionRequete r) {
        String libelle = r.libelle().trim();
        if (professions.existsByLibelleIgnoreCase(libelle)) {
            throw RegleMetierException.conflit("La profession « " + libelle + " » existe déjà.");
        }
        Profession p = new Profession();
        p.setLibelle(libelle);
        professions.save(p);
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
