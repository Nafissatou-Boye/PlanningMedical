package sn.mediplan.stagiaires;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import sn.mediplan.diffusion.DiffusionDtos.Destinataire;
import sn.mediplan.diffusion.DiffusionDtos.EnvoiRequete;
import sn.mediplan.diffusion.DiffusionDtos.LigneJournal;
import sn.mediplan.diffusion.DiffusionDtos.ResultatEnvoi;
import sn.mediplan.diffusion.DiffusionService;
import sn.mediplan.diffusion.PdfStagiairesService;
import sn.mediplan.planning.PlanningDtos.Candidat;
import sn.mediplan.planning.PlanningDtos.CreationPlanning;
import sn.mediplan.planning.PlanningDtos.StatutRequete;
import sn.mediplan.stagiaires.StagiairesDtos.AffectationStageRequete;
import sn.mediplan.stagiaires.StagiairesDtos.GrilleStagiaires;
import sn.mediplan.stagiaires.StagiairesDtos.LiberationStageRequete;
import sn.mediplan.stagiaires.StagiairesDtos.PeriodeRequete;

/** Organisation des étudiants stagiaires. Chaque modification renvoie la grille à jour. */
@RestController
@RequestMapping("/api/plannings/stagiaires")
public class StagiairesController {

    private final StagiairesService stagiaires;
    private final PdfStagiairesService pdf;
    private final DiffusionService diffusion;

    public StagiairesController(StagiairesService stagiaires, PdfStagiairesService pdf, DiffusionService diffusion) {
        this.stagiaires = stagiaires;
        this.pdf = pdf;
        this.diffusion = diffusion;
    }

    /** Grille du mois (planningId = null tant que l'organisation n'est pas créée). */
    @GetMapping
    public GrilleStagiaires grille(@RequestParam Long serviceId, @RequestParam int annee, @RequestParam int mois) {
        return stagiaires.grille(serviceId, annee, mois);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrilleStagiaires creer(@Valid @RequestBody CreationPlanning r) {
        return stagiaires.grille(stagiaires.creer(r.serviceId(), r.annee(), r.mois()));
    }

    @PutMapping("/{id}/statut")
    public GrilleStagiaires statut(@PathVariable Long id, @Valid @RequestBody StatutRequete r) {
        stagiaires.changerStatut(id, r.statut());
        return stagiaires.grille(id);
    }

    // ---------- Semaines ----------

    @PostMapping("/{id}/periodes")
    public GrilleStagiaires ajouterPeriode(@PathVariable Long id, @Valid @RequestBody PeriodeRequete r) {
        stagiaires.ajouterPeriode(id, r);
        return stagiaires.grille(id);
    }

    @PutMapping("/{id}/periodes/{periodeId}")
    public GrilleStagiaires modifierPeriode(@PathVariable Long id, @PathVariable Long periodeId,
            @Valid @RequestBody PeriodeRequete r) {
        stagiaires.modifierPeriode(id, periodeId, r);
        return stagiaires.grille(id);
    }

    @DeleteMapping("/{id}/periodes/{periodeId}")
    public GrilleStagiaires supprimerPeriode(@PathVariable Long id, @PathVariable Long periodeId) {
        stagiaires.supprimerPeriode(id, periodeId);
        return stagiaires.grille(id);
    }

    // ---------- Affectations ----------

    @GetMapping("/{id}/candidats")
    public List<Candidat> candidats(@PathVariable Long id, @RequestParam Long periodeId, @RequestParam Long posteId) {
        return stagiaires.candidats(id, periodeId, posteId);
    }

    @PostMapping("/{id}/affectations")
    public GrilleStagiaires affecter(@PathVariable Long id, @Valid @RequestBody AffectationStageRequete r) {
        stagiaires.affecter(id, r);
        return stagiaires.grille(id);
    }

    @PostMapping("/{id}/liberations")
    public GrilleStagiaires liberer(@PathVariable Long id, @Valid @RequestBody LiberationStageRequete r) {
        stagiaires.liberer(id, r);
        return stagiaires.grille(id);
    }

    // ---------- PDF et envoi ----------

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] contenu = pdf.generer(id);
        String nom = pdf.nomFichier(stagiaires.grille(id));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nom).build().toString())
                .body(contenu);
    }

    @GetMapping("/{id}/destinataires")
    public List<Destinataire> destinataires(@PathVariable Long id) {
        return diffusion.destinatairesStagiaires(id);
    }

    @PostMapping("/{id}/envois")
    public List<ResultatEnvoi> envoyer(@PathVariable Long id, @Valid @RequestBody EnvoiRequete r,
            Authentication authentification) {
        return diffusion.envoyerStagiaires(id, r.agentIds(), r.message(), authentification.getName());
    }

    @GetMapping("/{id}/envois")
    public List<LigneJournal> journal(@PathVariable Long id) {
        return diffusion.journal(id);
    }
}
