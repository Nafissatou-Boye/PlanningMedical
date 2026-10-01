package sn.mediplan.conges;

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
import sn.mediplan.personnel.Agent;

/** Congé ou indisponibilité : saisi une fois, appliqué à tous les plannings et au PDF. */
@Entity
@Table(name = "conge")
@Getter
@Setter
@NoArgsConstructor
public class Conge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false, length = 20)
    private TypeConge type;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(length = 255)
    private String motif;

    @Column(name = "affiche_sur_pdf", nullable = false)
    private boolean afficheSurPdf = true;
}
