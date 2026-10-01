package sn.mediplan.parametrage;

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

/** Signature en bas du PDF (ex. « Le Chef de Service »). */
@Entity
@Table(name = "signataire")
@Getter
@Setter
@NoArgsConstructor
public class Signataire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceHospitalier service;

    @Column(nullable = false, length = 100)
    private String fonction;

    /** Facultatif : nom imprimé sous la fonction. */
    @Column(length = 100)
    private String nom;

    @Column(name = "ordre_affichage", nullable = false)
    private int ordreAffichage;
}
