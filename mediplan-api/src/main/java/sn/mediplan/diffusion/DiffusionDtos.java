package sn.mediplan.diffusion;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class DiffusionDtos {

    private DiffusionDtos() {
    }

    /** Agent du service, avec ses gardes du mois et son dernier envoi. */
    public record Destinataire(Long agentId, String nomAffichage, String email, int gardes, int astreintes,
            StatutEnvoi dernierStatut, LocalDateTime dernierEnvoi) {
    }

    public record EnvoiRequete(
            @NotEmpty(message = "Choisissez au moins un destinataire.") List<Long> agentIds,
            @Size(max = 1000, message = "1000 caractères maximum.") String message) {
    }

    public record ResultatEnvoi(Long agentId, String nomAffichage, String email, StatutEnvoi statut,
            String erreur) {
    }

    public record LigneJournal(String nomAffichage, String email, LocalDateTime dateEnvoi, StatutEnvoi statut,
            String erreur) {
    }
}
