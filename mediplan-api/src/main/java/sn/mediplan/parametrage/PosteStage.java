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

/** Colonne du planning des stagiaires (ex. PME/2nd VPA, Salle Prog.1, VPA). */
@Entity
@Table(name = "poste_stage")
@Getter
@Setter
@NoArgsConstructor
public class PosteStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceHospitalier service;

    @Column(nullable = false, length = 80)
    private String libelle;

    /** Ex. « MARDI,MERCREDI » ; vide = toute la période. */
    @Column(name = "jours_actifs", length = 80)
    private String joursActifs;

    /** Poste tenu en plus d'un poste principal (ex. VPA). */
    @Column(nullable = false)
    private boolean partiel;

    @Column(length = 7)
    private String couleur;

    @Column(name = "ordre_affichage", nullable = false)
    private int ordreAffichage;

    @Column(nullable = false)
    private boolean actif = true;
}
