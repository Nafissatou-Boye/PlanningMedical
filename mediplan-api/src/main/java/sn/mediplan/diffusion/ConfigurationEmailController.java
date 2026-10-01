package sn.mediplan.diffusion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import sn.mediplan.diffusion.DiffusionService.ConfigurationEmail;

@RestController
public class ConfigurationEmailController {

    public record CompteEmail(
            @NotBlank(message = "Indiquez l'adresse Gmail.") @Email(message = "Adresse e-mail invalide.") String adresse,
            @NotBlank(message = "Indiquez le mot de passe d'application.") String motDePasse) {
    }

    private final DiffusionService diffusion;
    private final EnvoiEmail envoiEmail;

    public ConfigurationEmailController(DiffusionService diffusion, EnvoiEmail envoiEmail) {
        this.diffusion = diffusion;
        this.envoiEmail = envoiEmail;
    }

    /** Vérifie le compte Gmail (et envoie un e-mail de test à cette adresse), puis l'enregistre. */
    @PostMapping("/api/configuration/email")
    public ConfigurationEmail configurer(@Valid @RequestBody CompteEmail c) {
        envoiEmail.configurer(c.adresse(), c.motDePasse());
        return diffusion.configuration();
    }

    @GetMapping("/api/configuration/email")
    public ConfigurationEmail configuration() {
        return diffusion.configuration();
    }
}
