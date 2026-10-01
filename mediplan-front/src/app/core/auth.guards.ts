import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';

/** Où envoyer quelqu'un qui n'est pas connecté : Bienvenue (1re visite) ou Connexion. */
async function pageDEntree(auth: AuthService, router: Router): Promise<UrlTree> {
  try {
    return router.createUrlTree([(await auth.estInstallee()) ? '/connexion' : '/bienvenue']);
  } catch {
    // API injoignable : on montre la connexion, qui affichera le message d'erreur.
    return router.createUrlTree(['/connexion']);
  }
}

/** Pages de l'application : il faut être connecté. */
export const connecteGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (await firstValueFrom(auth.restaurerSession())) return true;
  return pageDEntree(auth, router);
};

/** Page Bienvenue : seulement tant que l'application n'est pas configurée. */
export const bienvenueGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (await firstValueFrom(auth.restaurerSession())) return router.createUrlTree(['/tableau-de-bord']);
  try {
    return (await auth.estInstallee()) ? router.createUrlTree(['/connexion']) : true;
  } catch {
    return true;
  }
};

/** Page Connexion : inutile si déjà connecté ; redirige vers Bienvenue à la 1re visite. */
export const connexionGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (await firstValueFrom(auth.restaurerSession())) return router.createUrlTree(['/tableau-de-bord']);
  try {
    return (await auth.estInstallee()) ? true : router.createUrlTree(['/bienvenue']);
  } catch {
    return true;
  }
};
