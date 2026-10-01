package sn.mediplan.planning;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.mediplan.parametrage.ModeAffectation;
import sn.mediplan.parametrage.PlageHoraire;

/** Une case du tableau : une date + une plage horaire (colonne). */
@Entity
@Table(name = "creneau")
@Getter
@Setter
@NoArgsConstructor
public class Creneau {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_id", nullable = false)
    private Planning planning;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plage_horaire_id", nullable = false)
    private PlageHoraire plageHoraire;

    @Column(name = "date_garde", nullable = false)
    private LocalDate dateGarde;

    @Column(nullable = false)
    private LocalDateTime debut;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Column(nullable = false, length = 10)
    private ModeAffectation mode = ModeAffectation.UNIQUE;

    public int places() {
        return mode == ModeAffectation.BINOME ? 2 : 1;
    }
}
