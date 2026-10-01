import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API_URL } from './api';
import { CategorieAgent } from './modeles';

export interface MoisRapport {
  mois: number;
  planningCree: boolean;
  placesTotales: number;
  placesRemplies: number;
  couverture: number;
}

export interface LigneAgent {
  agentId: number;
  nomAffichage: string;
  profession: string | null;
  categorie: CategorieAgent | null;
  actif: boolean;
  parMois: number[];
  total: number;
  jour: number;
  nuit: number;
  h24: number;
  weekendFerie: number;
  astreintes: number;
  joursAbsence: number;
}

export interface Rapport {
  serviceId: number;
  service: string;
  annee: number;
  mois: MoisRapport[];
  agents: LigneAgent[];
  totalGardes: number;
  couvertureMoyenne: number;
  moisCrees: number;
}

@Injectable({ providedIn: 'root' })
export class RapportsApi {
  private readonly http = inject(HttpClient);

  rapport(serviceId: number, annee: number) {
    return this.http.get<Rapport>(`${API_URL}/rapports`, { params: { serviceId, annee } });
  }
}
