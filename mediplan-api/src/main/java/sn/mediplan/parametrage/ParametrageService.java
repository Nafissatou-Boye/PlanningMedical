package sn.mediplan.parametrage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.commun.RegleMetierException;
import sn.mediplan.parametrage.ParametrageDtos.PlageDto;
import sn.mediplan.parametrage.ParametrageDtos.PlageRequete;
import sn.mediplan.parametrage.ParametrageDtos.PosteStageDto;
import sn.mediplan.parametrage.ParametrageDtos.PosteStageRequete;
import sn.mediplan.parametrage.ParametrageDtos.ServiceDto;
import sn.mediplan.parametrage.ParametrageDtos.ServiceRequete;
import sn.mediplan.parametrage.ParametrageDtos.SousServiceDto;
import sn.mediplan.parametrage.ParametrageDtos.SousServiceRequete;

/**
 * Services, sous-services, plages horaires et postes de stage.
 * Rien n'est supprimé : un élément retiré est archivé (actif = false) pour garder l'historique des plannings.
 */
@Service
public class ParametrageService {

    private final ServiceHospitalierRepository services;
    private final SousServiceRepository sousServices;
    private final PlageHoraireRepository plages;
    private final PosteStageRepository postes;

    public ParametrageService(ServiceHospitalierRepository services, SousServiceRepository sousServices,
            PlageHoraireRepository plages, PosteStageRepository postes) {
        this.services = services;
        this.sousServices = sousServices;
        this.plages = plages;
        this.postes = postes;
    }

    /** Arborescence complète en 4 requêtes, assemblée en mémoire. */
    @Transactional(readOnly = true)
    public List<ServiceDto> lister() {
        Map<Long, List<PlageHoraire>> plagesParSousService = plages.findAllOrdonnees().stream()
                .collect(Collectors.groupingBy(p -> p.getSousService().getId()));
        Map<Long, List<SousService>> sousParService = sousServices.findAllOrdonnes().stream()
                .collect(Collectors.groupingBy(s -> s.getService().getId()));
        Map<Long, List<PosteStage>> postesParService = postes.findAllOrdonnes().stream()
                .collect(Collectors.groupingBy(p -> p.getService().getId()));

        return services.findAllOrdonnes().stream()
                .map(s -> new ServiceDto(s.getId(), s.getNom(), s.getCode(), s.getReferenceDocument(),
                        s.getOrdreAffichage(), s.isActif(),
                        sousParService.getOrDefault(s.getId(), List.of()).stream()
                                .map(ss -> new SousServiceDto(ss.getId(), ss.getNom(), ss.getOrdreAffichage(),
                                        ss.isActif(),
                                        plagesParSousService.getOrDefault(ss.getId(), List.of()).stream()
                                                .map(PlageDto::de).toList()))
                                .toList(),
                        postesParService.getOrDefault(s.getId(), List.of()).stream()
                                .map(PosteStageDto::de).toList()))
                .toList();
    }

    // ---------- Services ----------

    @Transactional
    public void creerService(ServiceRequete r) {
        String nom = r.nom().trim();
        if (services.findByNomIgnoreCase(nom).isPresent()) {
            throw RegleMetierException.conflit("Un service porte déjà le nom « " + nom + " ».");
        }
        ServiceHospitalier s = new ServiceHospitalier(nom);
        appliquer(s, r);
        s.setOrdreAffichage(services.ordreMax() + 1);
        services.save(s);
    }

    @Transactional
    public void modifierService(Long id, ServiceRequete r) {
        ServiceHospitalier s = services.findById(id)
                .orElseThrow(() -> RegleMetierException.introuvable("Service introuvable."));
        String nom = r.nom().trim();
        services.findByNomIgnoreCase(nom)
                .filter(autre -> !autre.getId().equals(id))
                .ifPresent(autre -> {
                    throw RegleMetierException.conflit("Un service porte déjà le nom « " + nom + " ».");
                });
        s.setNom(nom);
        appliquer(s, r);
        if (r.actif() != null) {
            s.setActif(r.actif());
        }
    }

    private static void appliquer(ServiceHospitalier s, ServiceRequete r) {
        s.setCode(vide(r.code()) ? null : r.code().trim().toUpperCase());
        s.setReferenceDocument(vide(r.referenceDocument()) ? null : r.referenceDocument().trim());
    }

    // ---------- Sous-services ----------

    @Transactional
    public void creerSousService(Long serviceId, SousServiceRequete r) {
        ServiceHospitalier service = services.findById(serviceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Service introuvable."));
        String nom = r.nom().trim();
        if (sousServices.existsByServiceIdAndNomIgnoreCase(serviceId, nom)) {
            throw RegleMetierException.conflit("Ce service a déjà un sous-service « " + nom + " ».");
        }
        SousService ss = new SousService();
        ss.setService(service);
        ss.setNom(nom);
        ss.setOrdreAffichage(sousServices.ordreMax(serviceId) + 1);
        sousServices.save(ss);
    }

    @Transactional
    public void modifierSousService(Long id, SousServiceRequete r) {
        SousService ss = sousServices.findById(id)
                .orElseThrow(() -> RegleMetierException.introuvable("Sous-service introuvable."));
        String nom = r.nom().trim();
        if (!ss.getNom().equalsIgnoreCase(nom)
                && sousServices.existsByServiceIdAndNomIgnoreCase(ss.getService().getId(), nom)) {
            throw RegleMetierException.conflit("Ce service a déjà un sous-service « " + nom + " ».");
        }
        ss.setNom(nom);
        if (r.actif() != null) {
            ss.setActif(r.actif());
        }
    }

    // ---------- Plages horaires ----------

    @Transactional
    public void creerPlage(Long sousServiceId, PlageRequete r) {
        SousService ss = sousServices.findById(sousServiceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Sous-service introuvable."));
        String libelle = r.libelle().trim();
        if (plages.existsBySousServiceIdAndLibelleIgnoreCase(sousServiceId, libelle)) {
            throw RegleMetierException.conflit("Ce sous-service a déjà une plage « " + libelle + " ».");
        }
        PlageHoraire p = new PlageHoraire();
        p.setSousService(ss);
        appliquer(p, r, libelle);
        p.setOrdreAffichage(plages.ordreMax(sousServiceId) + 1);
        plages.save(p);
    }

    @Transactional
    public void modifierPlage(Long id, PlageRequete r) {
        PlageHoraire p = plages.findById(id)
                .orElseThrow(() -> RegleMetierException.introuvable("Plage horaire introuvable."));
        String libelle = r.libelle().trim();
        if (!p.getLibelle().equalsIgnoreCase(libelle)
                && plages.existsBySousServiceIdAndLibelleIgnoreCase(p.getSousService().getId(), libelle)) {
            throw RegleMetierException.conflit("Ce sous-service a déjà une plage « " + libelle + " ».");
        }
        appliquer(p, r, libelle);
        if (r.actif() != null) {
            p.setActif(r.actif());
        }
    }

    private static void appliquer(PlageHoraire p, PlageRequete r, String libelle) {
        p.setLibelle(libelle);
        p.setHeureDebut(r.heureDebut());
        p.setHeureFin(r.heureFin());
        p.setTypeGarde(r.typeGarde());
        p.setModeParDefaut(r.modeParDefaut());
    }

    // ---------- Postes de stage ----------

    @Transactional
    public void creerPoste(Long serviceId, PosteStageRequete r) {
        ServiceHospitalier service = services.findById(serviceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Service introuvable."));
        String libelle = r.libelle().trim();
        if (postes.existsByServiceIdAndLibelleIgnoreCase(serviceId, libelle)) {
            throw RegleMetierException.conflit("Ce service a déjà un poste « " + libelle + " ».");
        }
        PosteStage p = new PosteStage();
        p.setService(service);
        appliquer(p, r, libelle);
        p.setOrdreAffichage(postes.ordreMax(serviceId) + 1);
        postes.save(p);
    }

    @Transactional
    public void modifierPoste(Long id, PosteStageRequete r) {
        PosteStage p = postes.findById(id)
                .orElseThrow(() -> RegleMetierException.introuvable("Poste de stage introuvable."));
        String libelle = r.libelle().trim();
        if (!p.getLibelle().equalsIgnoreCase(libelle)
                && postes.existsByServiceIdAndLibelleIgnoreCase(p.getService().getId(), libelle)) {
            throw RegleMetierException.conflit("Ce service a déjà un poste « " + libelle + " ».");
        }
        appliquer(p, r, libelle);
        if (r.actif() != null) {
            p.setActif(r.actif());
        }
    }

    private static void appliquer(PosteStage p, PosteStageRequete r, String libelle) {
        p.setLibelle(libelle);
        p.setJoursActifs(vide(r.joursActifs()) ? null : r.joursActifs().trim().toUpperCase());
        p.setPartiel(r.partiel());
        p.setCouleur(vide(r.couleur()) ? null : r.couleur());
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
