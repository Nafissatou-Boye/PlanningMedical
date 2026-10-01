import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { API_URL } from './api';
import { AuthService } from './auth.service';

/**
 * Ajoute « Authorization: Bearer <jeton> » à chaque appel de l'API.
 * Si l'API répond 401 (jeton expiré), on déconnecte et on renvoie vers la connexion.
 */
export const jetonInterceptor: HttpInterceptorFn = (requete, suivant) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const jeton = auth.jeton();
  const versApi = requete.url.startsWith(API_URL);

  const envoyee =
    jeton && versApi ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : requete;

  return suivant(envoyee).pipe(
    catchError((erreur: HttpErrorResponse) => {
      const routePublique = /\/(auth\/connexion|installation)/.test(requete.url);
      if (erreur.status === 401 && versApi && !routePublique) {
        auth.deconnecter();
        router.navigate(['/connexion']);
      }
      return throwError(() => erreur);
    }),
  );
};
