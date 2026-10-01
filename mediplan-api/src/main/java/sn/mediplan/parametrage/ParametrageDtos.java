package sn.mediplan.parametrage;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Objets échangés avec le frontend pour le paramétrage des services. */
public final class ParametrageDtos {

    private ParametrageDtos() {
    }

    // ---------- Lecture ----------

    public record PlageDto(Long id, String libelle, LocalTime heureDebut, LocalTime heureFin,
            TypeGarde typeGarde, ModeAffectation modeParDefaut, int ordreAffichage, boolean actif,
            boolean finitLeLendemain) {

        public static PlageDto de(PlageHoraire p) {
            return new PlageDto(p.getId(), p.getLibelle(), p.getHeureDebut(), p.getHeureFin(), p.getTypeGarde(),
                    p.getModeParDefaut(), p.getOrdreAffichage(), p.isActif(), p.finitLeLendemain());
        }
    }

    public record SousServiceDto(Long id, String nom, int ordreAffichage, boolean actif, List<PlageDto> plages) {
    }

    public record PosteStageDto(Long id, String libelle, String joursActifs, boolean partiel, String couleur,
            int ordreAffichage, boolean actif) {

        public static PosteStageDto de(PosteStage p) {
            return new PosteStageDto(p.getId(), p.getLibelle(), p.getJoursActifs(), p.isPartiel(), p.getCouleur(),
                    p.getOrdreAffichage(), p.isActif());
        }
    }

    public record ServiceDto(Long id, String nom, String code, String referenceDocument, int ordreAffichage,
            boolean actif, List<SousServiceDto> sousServices, List<PosteStageDto> postesStage) {
    }

    // ---------- Écriture ----------

    public record ServiceRequete(
            @NotBlank(message = "Le nom du service est obligatoire.")
            @Size(max = 150, message = "150 caractères maximum.") String nom,
            @Size(max = 30, message = "30 caractères maximum.") String code,
            @Size(max = 100, message = "100 caractères maximum.") String referenceDocument,
            Boolean actif) {
    }

    public record SousServiceRequete(
            @NotBlank(message = "Le nom du sous-service est obligatoire.")
            @Size(max = 150, message = "150 caractères maximum.") String nom,
            Boolean actif) {
    }

    public record PlageRequete(
            @NotBlank(message = "Le libellé est obligatoire (ex. 08H-16H).")
            @Size(max = 30, message = "30 caractères maximum.") String libelle,
            @NotNull(message = "L'heure de début est obligatoire.") LocalTime heureDebut,
            @NotNull(message = "L'heure de fin est obligatoire.") LocalTime heureFin,
            @NotNull(message = "Le type de garde est obligatoire.") TypeGarde typeGarde,
            @NotNull(message = "Le mode est obligatoire.") ModeAffectation modeParDefaut,
            Boolean actif) {
    }

    public record PosteStageRequete(
            @NotBlank(message = "Le libellé du poste est obligatoire.")
            @Size(max = 80, message = "80 caractères maximum.") String libelle,
            @Size(max = 80, message = "80 caractères maximum.") String joursActifs,
            boolean partiel,
            @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "Couleur au format #RRGGBB.") String couleur,
            Boolean actif) {
    }
}
