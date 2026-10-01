import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';

import { routes } from './app.routes';
import { jetonInterceptor } from './core/jeton.interceptor';
import { FOURNISSEURS_DATES_FR } from './core/dates';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withRouterConfig({ canceledNavigationResolution: 'computed' })),
    provideHttpClient(withInterceptors([jetonInterceptor])),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    ...FOURNISSEURS_DATES_FR,
  ],
};
