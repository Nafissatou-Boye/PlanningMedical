package sn.mediplan.commun;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Transforme les exceptions en réponses JSON claires, en français. */
@RestControllerAdvice
public class GestionnaireErreurs {

    private static final Logger LOG = LoggerFactory.getLogger(GestionnaireErreurs.class);

    @ExceptionHandler(RegleMetierException.class)
    public ResponseEntity<ApiErreur> regleMetier(RegleMetierException e) {
        return ResponseEntity.status(e.getStatut()).body(ApiErreur.de(e.getStatut().value(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErreur> validation(MethodArgumentNotValidException e) {
        Map<String, String> champs = new LinkedHashMap<>();
        for (FieldError erreur : e.getBindingResult().getFieldErrors()) {
            champs.putIfAbsent(erreur.getField(), erreur.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiErreur.de(400, "Certains champs sont invalides.", champs));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErreur> corpsIllisible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiErreur.de(400, "La requête envoyée est mal formée."));
    }

    /** Dernier filet de sécurité : une contrainte de la base (verrou) a refusé l'opération. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErreur> integrite(DataIntegrityViolationException e) {
        LOG.warn("Contrainte de base de données violée : {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErreur.de(409, "Opération refusée : elle entre en conflit avec une donnée existante."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErreur> inattendue(Exception e) {
        LOG.error("Erreur inattendue", e);
        return ResponseEntity.internalServerError()
                .body(ApiErreur.de(500, "Une erreur inattendue est survenue. Réessayez plus tard."));
    }
}
