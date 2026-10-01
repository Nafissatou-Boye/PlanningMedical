package sn.mediplan.parametrage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Service de l'hôpital (ex. Anesthésie – Réanimation).
 * Nommé « ServiceHospitalier » pour ne pas être confondu avec l'annotation Spring @Service.
 */
@Entity
@Table(name = "service")
@Getter
@Setter
@NoArgsConstructor
public class ServiceHospitalier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(length = 30)
    private String code;

    @Column(name = "reference_document", length = 100)
    private String referenceDocument;

    @Column(name = "ordre_affichage", nullable = false)
    private int ordreAffichage;

    @Column(nullable = false)
    private boolean actif = true;

    public ServiceHospitalier(String nom) {
        this.nom = nom;
    }
}
