package sn.mediplan.personnel;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PersonnelDtos {

    private PersonnelDtos() {
    }

    public record ReferenceDto(Long id, String libelle) {
    }

    public record StageDto(Long id, String niveau, String etablissementOrigine, Long serviceId, String service,
            LocalDate dateDebut, LocalDate dateFin, boolean enCours) {

        public static StageDto de(Stage s) {
            LocalDate aujourdhui = LocalDate.now();
            return new StageDto(s.getId(), s.getNiveau(), s.getEtablissementOrigine(), s.getService().getId(),
                    s.getService().getNom(), s.getDateDebut(), s.getDateFin(),
                    !aujourdhui.isBefore(s.getDateDebut()) && !aujourdhui.isAfter(s.getDateFin()));
        }
    }

    public record AgentDto(Long id, String matricule, String titre, String prenom, String nom, String nomAffichage,
            String email, String telephone, CategorieAgent categorie, Long professionId, String profession,
            List<ReferenceDto> services, boolean actif, StageDto stage) {
    }

    public record StageRequete(
            @Size(max = 50, message = "50 caractères maximum.") String niveau,
            @Size(max = 150, message = "150 caractères maximum.") String etablissementOrigine,
            Long serviceId,
            @NotNull(message = "La date de début du stage est obligatoire.") LocalDate dateDebut,
            @NotNull(message = "La date de fin du stage est obligatoire.") LocalDate dateFin) {
    }

    public record AgentRequete(
            @Size(max = 10, message = "10 caractères maximum.") String titre,
            @NotBlank(message = "Le prénom est obligatoire.") @Size(max = 80, message = "80 caractères maximum.")
            String prenom,
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 80, message = "80 caractères maximum.")
            String nom,
            @Size(max = 60, message = "60 caractères maximum.") String nomAffichage,
            @Size(max = 30, message = "30 caractères maximum.") String matricule,
            @Email(message = "Adresse e-mail invalide.") @Size(max = 150, message = "150 caractères maximum.")
            String email,
            @Size(max = 30, message = "30 caractères maximum.") String telephone,
            @NotNull(message = "La catégorie est obligatoire.") CategorieAgent categorie,
            Long professionId,
            @NotEmpty(message = "Rattachez l'agent à au moins un service.") List<Long> serviceIds,
            @Valid StageRequete stage,
            Boolean actif) {
    }

    public record ProfessionRequete(
            @NotBlank(message = "Le libellé est obligatoire.") @Size(max = 100, message = "100 caractères maximum.")
            String libelle) {
    }
}
