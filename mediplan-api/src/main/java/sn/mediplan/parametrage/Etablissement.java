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

/** En-tête officiel des PDF (une seule ligne en base). */
@Entity
@Table(name = "etablissement")
@Getter
@Setter
@NoArgsConstructor
public class Etablissement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String republique;

    @Column(nullable = false, length = 120)
    private String devise;

    @Column(nullable = false, length = 200)
    private String ministere;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(nullable = false, length = 100)
    private String ville;

    @Column(name = "logo_chemin", length = 255)
    private String logoChemin;
}
