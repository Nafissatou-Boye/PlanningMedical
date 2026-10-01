package sn.mediplan.installation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Données saisies sur la page « Bienvenue » lors de la première visite. */
public record InstallationRequete(
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 80, message = "80 caractères maximum.")
        String nom,

        @NotBlank(message = "Le prénom est obligatoire.")
        @Size(max = 80, message = "80 caractères maximum.")
        String prenom,

        @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
        @Size(min = 3, max = 30, message = "Entre 3 et 30 caractères.")
        // Lettres (accents compris), chiffres, espace, point, tiret, apostrophe. Ex. : « Dr Nafisa »
        @Pattern(regexp = "^[\\p{L}\\p{N}][\\p{L}\\p{N} ._'-]*[\\p{L}\\p{N}.]$",
                message = "Commencez et terminez par une lettre ou un chiffre.")
        String nomUtilisateur,

        @NotBlank(message = "Le service est obligatoire.")
        @Size(max = 150, message = "150 caractères maximum.")
        String service,

        @NotBlank(message = "Le poste est obligatoire.")
        @Size(max = 80, message = "80 caractères maximum.")
        String poste,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 72, message = "Entre 8 et 72 caractères.")
        String motDePasse) {
}
