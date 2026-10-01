import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ProfilService } from './profil.service';

/** Tant que le profil n'est pas créé, toutes les pages renvoient vers « Bienvenue ». */
export const profilRequisGuard: CanActivateFn = () => {
  const profil = inject(ProfilService);
  return profil.estConfigure() ? true : inject(Router).createUrlTree(['/bienvenue']);
};

/** Une fois le profil créé, la page « Bienvenue » n'est plus accessible. */
export const premiereVisiteGuard: CanActivateFn = () => {
  const profil = inject(ProfilService);
  return profil.estConfigure() ? inject(Router).createUrlTree(['/tableau-de-bord']) : true;
};
