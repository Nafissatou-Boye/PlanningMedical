package sn.mediplan.stagiaires;

import java.time.LocalDate;

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
import sn.mediplan.planning.Planning;

/** Une ligne « SEMAINE » du planning des stagiaires (lundi → dimanche par défaut, dates modifiables). */
@Entity
@Table(name = "periode_stage")
@Getter
@Setter
@NoArgsConstructor
public class PeriodeStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_id", nullable = false)
    private Planning planning;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(nullable = false)
    private int ordre;
}
