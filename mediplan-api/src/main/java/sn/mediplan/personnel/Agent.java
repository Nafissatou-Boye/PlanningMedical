package sn.mediplan.personnel;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.mediplan.parametrage.ServiceHospitalier;

/** Membre du personnel ou stagiaire. Jamais supprimé : désactivé pour garder l'historique. */
@Entity
@Table(name = "agent")
@Getter
@Setter
@NoArgsConstructor
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 30)
    private String matricule;

    @Column(length = 10)
    private String titre;

    @Column(nullable = false, length = 80)
    private String prenom;

    @Column(nullable = false, length = 80)
    private String nom;

    /** Tel qu'affiché dans le PDF : « Dr Bara Aw », « Adama », « M.Ndiaye »... */
    @Column(name = "nom_affichage", nullable = false, length = 60)
    private String nomAffichage;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String telephone;

    @Column(nullable = false, length = 12)
    private CategorieAgent categorie = CategorieAgent.PERSONNEL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profession_id")
    private Profession profession;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "agent_service",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<ServiceHospitalier> services = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean actif = true;
}
