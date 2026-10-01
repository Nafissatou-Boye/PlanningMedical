package sn.mediplan.commun;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Format unique des erreurs renvoyées au frontend.
 *
 * @param statut  code HTTP
 * @param message message lisible, en français
 * @param champs  erreurs de validation par champ (vide si non concerné)
 */
public record ApiErreur(int statut, String message, Map<String, String> champs, LocalDateTime date) {

    public static ApiErreur de(int statut, String message) {
        return new ApiErreur(statut, message, Map.of(), LocalDateTime.now());
    }

    public static ApiErreur de(int statut, String message, Map<String, String> champs) {
        return new ApiErreur(statut, message, champs, LocalDateTime.now());
    }
}
