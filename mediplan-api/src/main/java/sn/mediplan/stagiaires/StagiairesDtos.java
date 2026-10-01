package sn.mediplan.stagiaires;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import sn.mediplan.planning.PlanningDtos.CongeDuMois;
import sn.mediplan.planning.PlanningDtos.Place;
import sn.mediplan.planning.StatutPlanning;

public final class StagiairesDtos {

    private StagiairesDtos() {
    }

    /** Nombre de stagiaires possibles par case (ex. « Dr Ndaw / Dr Lo »). */
    public static final int PLACES_PAR_CASE = 2;

    // ---------- Grille ----------

    /** Colonne : un poste de stage (PME/2nd VPA, Salle Prog.1, VPA…). */
    public record ColonnePoste(Long posteId, String libelle, String joursActifs, boolean partiel, String couleur) {
    }

    /** Ligne : une semaine, avec son libellé officiel « Du 31/08 au 06/09/26 ». */
    public record Semaine(Long periodeId, LocalDate dateDebut, LocalDate dateFin, String libelle) {
    }

    public record CaseStage(Long periodeId, Long posteId, List<Place> stagiaires) {
    }

    public record StatistiquesStage(int casesTotales, int casesRemplies, int stagiairesActifs,
            int stagiairesSansPoste) {
    }

    public record GrilleStagiaires(Long planningId, StatutPlanning statut, Long serviceId, String service, int annee,
            int mois, LocalDate dateDocument, String numeroReference, List<ColonnePoste> postes,
            List<Semaine> semaines, List<CaseStage> cases, List<CongeDuMois> conges,
            StatistiquesStage statistiques) {
    }

    // ---------- Requêtes ----------

    public record PeriodeRequete(
            @NotNull(message = "Date de début manquante.") LocalDate dateDebut,
            @NotNull(message = "Date de fin manquante.") LocalDate dateFin) {
    }

    public record AffectationStageRequete(
            @NotNull(message = "Semaine manquante.") Long periodeId,
            @NotNull(message = "Poste manquant.") Long posteId,
            @NotNull @Min(1) @Max(2) Integer position,
            @NotNull(message = "Choisissez un stagiaire.") Long agentId) {
    }

    public record LiberationStageRequete(
            @NotNull Long periodeId,
            @NotNull Long posteId,
            @NotNull @Min(1) @Max(2) Integer position) {
    }
}
