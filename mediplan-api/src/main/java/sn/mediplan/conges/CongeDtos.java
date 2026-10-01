package sn.mediplan.conges;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.mediplan.personnel.CategorieAgent;

public final class CongeDtos {

    private CongeDtos() {
    }

    public record CongeDto(Long id, Long agentId, String agent, String agentNomComplet, CategorieAgent categorie,
            TypeConge type, LocalDate dateDebut, LocalDate dateFin, long nombreJours, String motif,
            boolean afficheSurPdf) {

        public static CongeDto de(Conge c) {
            var a = c.getAgent();
            return new CongeDto(c.getId(), a.getId(), a.getNomAffichage(), a.getPrenom() + " " + a.getNom(),
                    a.getCategorie(), c.getType(), c.getDateDebut(), c.getDateFin(),
                    ChronoUnit.DAYS.between(c.getDateDebut(), c.getDateFin()) + 1, c.getMotif(),
                    c.isAfficheSurPdf());
        }
    }

    public record CongeRequete(
            @NotNull(message = "Choisissez un agent.") Long agentId,
            @NotNull(message = "Choisissez un type.") TypeConge type,
            @NotNull(message = "La date de début est obligatoire.") LocalDate dateDebut,
            @NotNull(message = "La date de fin est obligatoire.") LocalDate dateFin,
            @Size(max = 255, message = "255 caractères maximum.") String motif) {
    }
}
