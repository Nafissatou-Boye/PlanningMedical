/**
 * Adresse de l'API : relative, car l'écran est servi par l'API elle-même (même adresse).
 * En développement, `ng serve` redirige /api vers http://localhost:8080 (proxy.conf.json).
 */
export const API_URL = '/api';

/** Format des erreurs renvoyées par l'API (cf. ApiErreur côté Java). */
export interface ApiErreur {
  statut: number;
  message: string;
  champs: Record<string, string>;
}

/** Extrait un message lisible d'une erreur HTTP. */
export function messageErreur(erreur: unknown): string {
  const e = erreur as { status?: number; error?: Partial<ApiErreur> };
  if (e?.status === 0) {
    return "Impossible de joindre le serveur MediPlan. Vérifiez que l'API est démarrée.";
  }
  return e?.error?.message ?? 'Une erreur inattendue est survenue.';
}
