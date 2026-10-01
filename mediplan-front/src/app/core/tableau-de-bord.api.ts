import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API_URL } from './api';
import { CategorieAgent } from './modeles';

export type StatutJour = 'EN_GARDE' | 'DE_GARDE_AUJOURDHUI' | 'EN_CONGE' | 'DISPONIBLE';

export interface MembreEquipe {
  agentId: number;
  nomAffichage: string;
  profession: string | null;
  categorie: CategorieAgent;
  statut: StatutJour;
  detail: string | null;
}

export interface Indicateurs {
  /** false : le planning du mois en cours n'existe pas encore. */
  planningCree: boolean;
  personnelActif: number;
  stagiairesActifs: number;
  gardesDuMois: number;
  gardesMoisPrecedent: number;
  enGardeMaintenant: number;
  couverture: number;
  couverturePrecedente: number;
  placesLibres: number;
  equipe: MembreEquipe[];
}

@Injectable({ providedIn: 'root' })
export class TableauDeBordApi {
  private readonly http = inject(HttpClient);

  indicateurs(serviceId: number) {
    return this.http.get<Indicateurs>(`${API_URL}/tableau-de-bord`, { params: { serviceId } });
  }
}
