package sn.mediplan.conges;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.mediplan.commun.RegleMetierException;
import sn.mediplan.conges.CongeDtos.CongeDto;
import sn.mediplan.conges.CongeDtos.CongeRequete;
import sn.mediplan.personnel.Agent;
import sn.mediplan.personnel.AgentRepository;

@Service
public class CongeService {

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CongeRepository conges;
    private final AgentRepository agents;

    public CongeService(CongeRepository conges, AgentRepository agents) {
        this.conges = conges;
        this.agents = agents;
    }

    @Transactional(readOnly = true)
    public List<CongeDto> lister(LocalDate debut, LocalDate fin) {
        List<Conge> liste = (debut != null && fin != null)
                ? conges.findChevauchant(debut, fin)
                : conges.findTousRecentsDabord();
        return liste.stream().map(CongeDto::de).toList();
    }

    @Transactional
    public CongeDto creer(CongeRequete r) {
        Conge c = new Conge();
        appliquer(c, r, null);
        return CongeDto.de(conges.save(c));
    }

    @Transactional
    public CongeDto modifier(Long id, CongeRequete r) {
        Conge c = conges.findById(id).orElseThrow(() -> RegleMetierException.introuvable("Congé introuvable."));
        appliquer(c, r, id);
        return CongeDto.de(c);
    }

    @Transactional
    public void supprimer(Long id) {
        if (!conges.existsById(id)) {
            throw RegleMetierException.introuvable("Congé introuvable.");
        }
        conges.deleteById(id);
    }

    private void appliquer(Conge c, CongeRequete r, Long idExclu) {
        if (r.dateFin().isBefore(r.dateDebut())) {
            throw RegleMetierException.conflit("La date de fin doit être après la date de début.");
        }
        Agent agent = agents.findById(r.agentId())
                .orElseThrow(() -> RegleMetierException.introuvable("Agent introuvable."));
        if (conges.existeChevauchement(agent.getId(), r.dateDebut(), r.dateFin(), idExclu)) {
            throw RegleMetierException.conflit(agent.getNomAffichage() + " a déjà un congé ou une indisponibilité"
                    + " entre le " + r.dateDebut().format(FR) + " et le " + r.dateFin().format(FR) + ".");
        }
        c.setAgent(agent);
        c.setType(r.type());
        c.setDateDebut(r.dateDebut());
        c.setDateFin(r.dateFin());
        c.setMotif(r.motif() == null || r.motif().isBlank() ? null : r.motif().trim());
        c.setAfficheSurPdf(r.type() != TypeConge.INDISPONIBILITE);
    }
}
