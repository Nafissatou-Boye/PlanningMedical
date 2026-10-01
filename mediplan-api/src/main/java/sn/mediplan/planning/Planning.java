package sn.mediplan.planning;

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
import sn.mediplan.parametrage.ServiceHospitalier;

/** Un planning par service, par type et par mois. */
@Entity
@Table(name = "planning")
@Getter
@Setter
@NoArgsConstructor
public class Planning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceHospitalier service;

    @Column(nullable = false, length = 12)
    private TypePlanning type;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int mois;

    @Column(nullable = false, columnDefinition = "smallint")
    private int annee;

    @Column(nullable = false, length = 12)
    private StatutPlanning statut = StatutPlanning.BROUILLON;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "date_document")
    private LocalDate dateDocument;

    @Column(name = "numero_reference", length = 100)
    private String numeroReference;

    public LocalDate premierJour() {
        return LocalDate.of(annee, mois, 1);
    }

    public LocalDate dernierJour() {
        return premierJour().withDayOfMonth(premierJour().lengthOfMonth());
    }
}
