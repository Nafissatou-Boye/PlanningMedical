package sn.mediplan.commun;

import org.springframework.http.HttpStatus;

/**
 * Violation d'une règle de gestion (ex. plage déjà attribuée, application déjà installée).
 * Le message est affiché tel quel à l'utilisateur.
 */
public class RegleMetierException extends RuntimeException {

    private final HttpStatus statut;

    public RegleMetierException(HttpStatus statut, String message) {
        super(message);
        this.statut = statut;
    }

    public static RegleMetierException conflit(String message) {
        return new RegleMetierException(HttpStatus.CONFLICT, message);
    }

    public static RegleMetierException nonAutorise(String message) {
        return new RegleMetierException(HttpStatus.UNAUTHORIZED, message);
    }

    public static RegleMetierException introuvable(String message) {
        return new RegleMetierException(HttpStatus.NOT_FOUND, message);
    }

    public HttpStatus getStatut() {
        return statut;
    }
}
