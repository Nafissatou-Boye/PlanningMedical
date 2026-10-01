package sn.mediplan.parametrage;

import java.time.LocalTime;

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

/**
 * Colonne du tableau : une plage horaire d'un sous-service (ex. 08H-16H).
 * Si l'heure de fin est inférieure ou égale à l'heure de début, la plage se termine le lendemain.
 */
@Entity
@Table(name = "plage_horaire")
@Getter
@Setter
@NoArgsConstructor
public class PlageHoraire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sous_service_id", nullable = false)
    private SousService sousService;

    @Column(nullable = false, length = 30)
    private String libelle;

    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;

    @Column(name = "type_garde", nullable = false, length = 10)
    private TypeGarde typeGarde;

    @Column(name = "mode_par_defaut", nullable = false, length = 10)
    private ModeAffectation modeParDefaut = ModeAffectation.UNIQUE;

    @Column(name = "ordre_affichage", nullable = false)
    private int ordreAffichage;

    @Column(nullable = false)
    private boolean actif = true;

    /** Vrai si la plage se termine le lendemain (ex. 16H-08H, 08H-08H). */
    public boolean finitLeLendemain() {
        return !heureFin.isAfter(heureDebut);
    }
}
