package sn.mediplan.diffusion;

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
import sn.mediplan.planning.Planning;

/** Journal : chaque e-mail envoyé (ou en échec) avec le PDF du planning. */
@Entity
@Table(name = "envoi_pdf")
@Getter
@Setter
@NoArgsConstructor
public class EnvoiPdf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planning_id", nullable = false)
    private Planning planning;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(name = "email_destinataire", nullable = false, length = 150)
    private String emailDestinataire;

    @Column(name = "date_envoi", nullable = false)
    private LocalDateTime dateEnvoi;

    @Column(nullable = false, length = 12)
    private StatutEnvoi statut = StatutEnvoi.EN_ATTENTE;

    @Column(name = "message_erreur", length = 500)
    private String messageErreur;
}
