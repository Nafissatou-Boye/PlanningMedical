package sn.mediplan.parametrage;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.mediplan.commun.RegleMetierException;

/**
 * Signatures en bas du PDF des gardes d'un service (ex. Le Chef de Service, Le Chef RH, Le Directeur).
 * La liste est remplacée d'un bloc : l'ordre envoyé est l'ordre d'affichage, de gauche à droite.
 */
@RestController
@RequestMapping("/api/services/{serviceId}/signataires")
public class SignatairesController {

    /** Utilisées tant que le service n'a pas ses propres signatures. */
    public static final List<String> PAR_DEFAUT = List.of("Le Chef de Service", "Le Chef RH", "Le Directeur");

    public record SignataireDto(
            @NotBlank(message = "Indiquez la fonction.") @Size(max = 100, message = "100 caractères maximum.") String fonction,
            @Size(max = 100, message = "100 caractères maximum.") String nom) {
    }

    public record Signatures(boolean parDefaut, List<SignataireDto> signataires) {
    }

    private final SignataireRepository signataires;
    private final ServiceHospitalierRepository services;

    public SignatairesController(SignataireRepository signataires, ServiceHospitalierRepository services) {
        this.signataires = signataires;
        this.services = services;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Signatures lister(@PathVariable Long serviceId) {
        List<Signataire> l = signataires.findByServiceIdOrderByOrdreAffichageAsc(serviceId);
        if (l.isEmpty()) {
            return new Signatures(true, PAR_DEFAUT.stream().map(f -> new SignataireDto(f, null)).toList());
        }
        return new Signatures(false, l.stream().map(s -> new SignataireDto(s.getFonction(), s.getNom())).toList());
    }

    @PutMapping
    @Transactional
    public Signatures remplacer(@PathVariable Long serviceId, @RequestBody List<@Valid SignataireDto> liste) {
        ServiceHospitalier service = services.findById(serviceId)
                .orElseThrow(() -> RegleMetierException.introuvable("Service introuvable."));
        if (liste.size() > 4) {
            throw RegleMetierException.conflit("Quatre signatures au maximum, pour qu'elles tiennent sous le tableau.");
        }
        signataires.deleteByServiceId(serviceId);
        signataires.flush();
        int ordre = 1;
        for (SignataireDto d : liste) {
            Signataire s = new Signataire();
            s.setService(service);
            s.setFonction(d.fonction().trim());
            s.setNom(d.nom() == null || d.nom().isBlank() ? null : d.nom().trim());
            s.setOrdreAffichage(ordre++);
            signataires.save(s);
        }
        // Liste vide envoyée = retour aux signatures par défaut
        return lister(serviceId);
    }
}
