package sn.mediplan.planning;

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
import sn.mediplan.personnel.Agent;

/** Un agent à une place (1 ou 2) d'une case. La base refuse deux agents sur la même place. */
@Entity
@Table(name = "affectation")
@Getter
@Setter
@NoArgsConstructor
public class Affectation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creneau_id", nullable = false)
    private Creneau creneau;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int position = 1;

    @Column(nullable = false)
    private LocalDateTime debut;

    @Column(nullable = false)
    private LocalDateTime fin;
}
