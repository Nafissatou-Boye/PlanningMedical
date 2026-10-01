package sn.mediplan.utilisateur;

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
import sn.mediplan.parametrage.ServiceHospitalier;

/** Compte de l'administrateur, créé lors de la première visite. */
@Entity
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false, length = 80)
    private String prenom;

    @Column(name = "nom_utilisateur", nullable = false, length = 30)
    private String nomUtilisateur;

    @Column(name = "mot_de_passe_hache", nullable = false, length = 100)
    private String motDePasseHache;

    @Column(length = 150)
    private String email;

    @Column(nullable = false, length = 80)
    private String poste;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceHospitalier service;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    /** « Dr   Nafisa » → « Dr Nafisa » : espaces en trop retirés. */
    public static String normaliserNomUtilisateur(String brut) {
        return brut == null ? null : brut.trim().replaceAll("\\s+", " ");
    }
}
