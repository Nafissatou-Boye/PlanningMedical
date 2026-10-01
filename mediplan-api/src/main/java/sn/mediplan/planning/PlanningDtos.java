package sn.mediplan.planning;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import sn.mediplan.parametrage.ModeAffectation;
import sn.mediplan.parametrage.TypeGarde;
import sn.mediplan.personnel.CategorieAgent;

public final class PlanningDtos {

    private PlanningDtos() {
    }

    // ---------- Grille ----------

    public record ColonnePlage(Long plageId, String libelle, LocalTime heureDebut, LocalTime heureFin,
            TypeGarde typeGarde, ModeAffectation modeParDefaut) {
    }

    public record ColonneSousService(Long sousServiceId, String nom, List<ColonnePlage> plages) {
    }

    public record Jour(LocalDate date, String libelle, boolean weekend, String ferie) {
    }

    public record Place(int position, Long agentId, String nomAffichage) {
    }

    public record Case(Long plageId, LocalDate date, ModeAffectation mode, List<Place> agents) {
    }

    public record PeriodeAstreinte(LocalDate dateDebut, LocalDate dateFin, Long agentId, String nomAffichage) {
    }

    public record CongeDuMois(String agent, LocalDate dateDebut, LocalDate dateFin) {
    }

    public record Statistiques(int placesTotales, int placesRemplies, int astreintesTotales, int astreintesRemplies) {
    }

    public record Grille(Long planningId, StatutPlanning statut, Long serviceId, String service, int annee, int mois,
            LocalDate dateDocument, String numeroReference, List<ColonneSousService> colonnes, List<Jour> jours,
            List<Case> cases, List<PeriodeAstreinte> astreintes, List<CongeDuMois> conges,
            Statistiques statistiques) {
    }

    // ---------- Candidats ----------

    /** Agent proposé pour une place : disponible, ou non avec la raison. */
    public record Candidat(Long agentId, String nomAffichage, String nomComplet, String profession,
            CategorieAgent categorie, boolean disponible, String raison, int gardesDuMois) {
    }

    // ---------- Requêtes ----------

    public record CreationPlanning(
            @NotNull(message = "Choisissez un service.") Long serviceId,
            @NotNull @Min(2020) @Max(2100) Integer annee,
            @NotNull @Min(1) @Max(12) Integer mois) {
    }

    public record AffectationRequete(
            @NotNull(message = "Colonne manquante.") Long plageId,
            @NotNull(message = "Date manquante.") LocalDate date,
            @NotNull @Min(1) @Max(2) Integer position,
            @NotNull(message = "Choisissez un agent.") Long agentId) {
    }

    public record LiberationRequete(
            @NotNull Long plageId,
            @NotNull LocalDate date,
            @NotNull @Min(1) @Max(2) Integer position) {
    }

    public record ModeRequete(
            @NotNull Long plageId,
            @NotNull LocalDate date,
            @NotNull ModeAffectation mode) {
    }

    public record AstreinteRequete(
            @NotNull(message = "Période manquante.") LocalDate dateDebut,
            @NotNull(message = "Choisissez un agent.") Long agentId) {
    }

    public record StatutRequete(@NotNull StatutPlanning statut) {
    }
}
