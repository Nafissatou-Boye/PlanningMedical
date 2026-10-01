package sn.mediplan.diffusion;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sn.mediplan.diffusion.DiffusionDtos.Destinataire;
import sn.mediplan.diffusion.DiffusionDtos.EnvoiRequete;
import sn.mediplan.diffusion.DiffusionDtos.LigneJournal;
import sn.mediplan.diffusion.DiffusionDtos.ResultatEnvoi;
import sn.mediplan.planning.PlanningService;

@RestController
@RequestMapping("/api/plannings/gardes/{id}")
public class DiffusionController {

    private final PdfPlanningService pdf;
    private final DiffusionService diffusion;
    private final PlanningService plannings;

    public DiffusionController(PdfPlanningService pdf, DiffusionService diffusion, PlanningService plannings) {
        this.pdf = pdf;
        this.diffusion = diffusion;
        this.plannings = plannings;
    }

    /** Le PDF officiel du planning (affiché dans le navigateur ou téléchargé). */
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] contenu = pdf.generer(id);
        String nom = pdf.nomFichier(plannings.grille(id));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nom).build().toString())
                .body(contenu);
    }

    @GetMapping("/destinataires")
    public List<Destinataire> destinataires(@PathVariable Long id) {
        return diffusion.destinataires(id);
    }

    @PostMapping("/envois")
    public List<ResultatEnvoi> envoyer(@PathVariable Long id, @Valid @RequestBody EnvoiRequete r,
            Authentication authentification) {
        return diffusion.envoyer(id, r.agentIds(), r.message(), authentification.getName());
    }

    @GetMapping("/envois")
    public List<LigneJournal> journal(@PathVariable Long id) {
        return diffusion.journal(id);
    }
}
