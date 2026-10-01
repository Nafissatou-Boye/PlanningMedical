package sn.mediplan.stagiaires;

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
import sn.mediplan.parametrage.PosteStage;
import sn.mediplan.personnel.Agent;

/** Un stagiaire à une place (1 ou 2) d'une case poste × période. La base refuse deux stagiaires sur la même place. */
@Entity
@Table(name = "affectation_stage")
@Getter
@Setter
@NoArgsConstructor
public class AffectationStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "periode_stage_id", nullable = false)
    private PeriodeStage periode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poste_stage_id", nullable = false)
    private PosteStage poste;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int position = 1;
}
