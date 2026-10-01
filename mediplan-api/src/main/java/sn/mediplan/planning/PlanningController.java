package sn.mediplan.planning;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sn.mediplan.planning.PlanningDtos.AffectationRequete;
import sn.mediplan.planning.PlanningDtos.AstreinteRequete;
import sn.mediplan.planning.PlanningDtos.Candidat;
import sn.mediplan.planning.PlanningDtos.CreationPlanning;
import sn.mediplan.planning.PlanningDtos.Grille;
import sn.mediplan.planning.PlanningDtos.LiberationRequete;
import sn.mediplan.planning.PlanningDtos.ModeRequete;
import sn.mediplan.planning.PlanningDtos.StatutRequete;

/** Chaque modification renvoie la grille à jour : l'écran se rafraîchit en un seul appel. */
@RestController
@RequestMapping("/api/plannings/gardes")
public class PlanningController {

    private final PlanningService plannings;

    public PlanningController(PlanningService plannings) {
        this.plannings = plannings;
    }

    /** Grille du mois (planningId = null si le planning n'a pas encore été créé). */
    @GetMapping
    public Grille grille(@RequestParam Long serviceId, @RequestParam int annee, @RequestParam int mois) {
        return plannings.grille(serviceId, annee, mois);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Grille creer(@Valid @RequestBody CreationPlanning r) {
        return plannings.grille(plannings.creer(r.serviceId(), r.annee(), r.mois()));
    }

    @PutMapping("/{id}/statut")
    public Grille statut(@PathVariable Long id, @Valid @RequestBody StatutRequete r) {
        plannings.changerStatut(id, r.statut());
        return plannings.grille(id);
    }

    @GetMapping("/{id}/candidats")
    public List<Candidat> candidats(@PathVariable Long id, @RequestParam Long plageId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return plannings.candidats(id, plageId, date);
    }

    @PostMapping("/{id}/affectations")
    public Grille affecter(@PathVariable Long id, @Valid @RequestBody AffectationRequete r) {
        plannings.affecter(id, r);
        return plannings.grille(id);
    }

    @PostMapping("/{id}/liberations")
    public Grille liberer(@PathVariable Long id, @Valid @RequestBody LiberationRequete r) {
        plannings.liberer(id, r);
        return plannings.grille(id);
    }

    @PutMapping("/{id}/mode")
    public Grille mode(@PathVariable Long id, @Valid @RequestBody ModeRequete r) {
        plannings.changerMode(id, r);
        return plannings.grille(id);
    }

    @GetMapping("/{id}/astreintes/candidats")
    public List<Candidat> candidatsAstreinte(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate dateDebut) {
        return plannings.candidatsAstreinte(id, dateDebut);
    }

    @PostMapping("/{id}/astreintes")
    public Grille affecterAstreinte(@PathVariable Long id, @Valid @RequestBody AstreinteRequete r) {
        plannings.affecterAstreinte(id, r);
        return plannings.grille(id);
    }

    @DeleteMapping("/{id}/astreintes")
    public Grille libererAstreinte(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate dateDebut) {
        plannings.libererAstreinte(id, dateDebut);
        return plannings.grille(id);
    }
}
