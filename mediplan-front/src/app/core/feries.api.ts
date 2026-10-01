import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API_URL } from './api';
import { DateIso } from './modeles';

export interface JourFerie {
  id: number;
  date: DateIso;
  libelle: string;
}

@Injectable({ providedIn: 'root' })
export class FeriesApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/jours-feries`;

  lister(annee: number) {
    return this.http.get<JourFerie[]>(this.base, { params: { annee } });
  }

  creer(d: { date: DateIso; libelle: string }) {
    return this.http.post<JourFerie>(this.base, d);
  }

  modifier(id: number, d: { date: DateIso; libelle: string }) {
    return this.http.put<JourFerie>(`${this.base}/${id}`, d);
  }

  supprimer(id: number) {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** Ajoute les fériés à date fixe et ceux liés à Pâques. */
  ajouterSenegal(annee: number) {
    return this.http.post<JourFerie[]>(`${this.base}/senegal/${annee}`, {});
  }
}
