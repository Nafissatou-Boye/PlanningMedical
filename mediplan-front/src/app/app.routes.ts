import { Routes } from '@angular/router';
import { MainLayout } from './layout/main-layout/main-layout';
import { bienvenueGuard, connecteGuard, connexionGuard } from './core/auth.guards';

/**
 * - « Bienvenue » (première visite) et « Connexion » s'affichent seules, sans le layout.
 * - Toutes les autres pages s'affichent dans le layout (barre latérale + en-tête)
 *   et exigent d'être connecté.
 */
export const routes: Routes = [
  {
    path: 'bienvenue',
    title: 'Bienvenue | MediPlan',
    canActivate: [bienvenueGuard],
    loadComponent: () => import('./pages/bienvenue/bienvenue').then((m) => m.Bienvenue),
  },
  {
    path: 'connexion',
    title: 'Connexion | MediPlan',
    canActivate: [connexionGuard],
    loadComponent: () => import('./pages/connexion/connexion').then((m) => m.Connexion),
  },
  {
    path: '',
    component: MainLayout,
    canActivate: [connecteGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      {
        path: 'tableau-de-bord',
        title: 'Tableau de bord | MediPlan',
        loadComponent: () => import('./pages/tableau-de-bord/tableau-de-bord').then((m) => m.TableauDeBord),
      },
      {
        path: 'planning',
        title: 'Planning des gardes | MediPlan',
        loadComponent: () => import('./pages/planning/planning').then((m) => m.Planning),
      },
      {
        path: 'stagiaires',
        title: 'Stagiaires | MediPlan',
        loadComponent: () => import('./pages/stagiaires/stagiaires').then((m) => m.Stagiaires),
      },
      {
        path: 'personnel',
        title: 'Personnel | MediPlan',
        loadComponent: () => import('./pages/personnel/personnel').then((m) => m.Personnel),
      },
      {
        path: 'conges',
        title: 'Congés | MediPlan',
        loadComponent: () => import('./pages/conges/conges').then((m) => m.Conges),
      },
      {
        path: 'services',
        title: 'Services | MediPlan',
        loadComponent: () => import('./pages/services/services').then((m) => m.Services),
      },
      {
        path: 'rapports',
        title: 'Rapports | MediPlan',
        loadComponent: () => import('./pages/rapports/rapports').then((m) => m.Rapports),
      },
      {
        path: 'aide',
        title: "Centre d'aide | MediPlan",
        loadComponent: () => import('./pages/aide/aide').then((m) => m.Aide),
      },
      {
        path: 'parametres',
        title: 'Paramètres | MediPlan',
        loadComponent: () => import('./pages/parametres/parametres').then((m) => m.Parametres),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
