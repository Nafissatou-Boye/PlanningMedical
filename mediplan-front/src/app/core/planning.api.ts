import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API_URL } from './api';
import { CategorieAgent, DateIso, ModeAffectation, TypeGarde } from './modeles';

export type StatutPlanning = 'BROUILLON' | 'VALIDE' | 'PUBLIE';

export interface ColonnePlage {
  plageId: number;
  libelle: string;
  heureDebut: string;
  heureFin: string;
  typeGarde: TypeGarde;
  modeParDefaut: ModeAffectation;
}

export interface ColonneSousService {
  sousServiceId: number;
  nom: string;
  plages: ColonnePlage[];
}

export interface Jour {
  date: DateIso;
  libelle: string;
  weekend: boolean;
  ferie: string | null;
}

export interface Place {
  position: number;
  agentId: number;
  nomAffichage: string;
}

export interface Case {
  plageId: number;
  date: DateIso;
  mode: ModeAffectation;
  agents: Place[];
}

export interface PeriodeAstreinte {
  dateDebut: DateIso;
  dateFin: DateIso;
  agentId: number | null;
  nomAffichage: string | null;
}

export interface Grille {
  planningId: number | null;
  statut: StatutPlanning | null;
  serviceId: number;
  service: string;
  annee: number;
  mois: number;
  dateDocument: DateIso | null;
  numeroReference: string | null;
  colonnes: ColonneSousService[];
  jours: Jour[];
  cases: Case[];
  astreintes: PeriodeAstreinte[];
  conges: { agent: string; dateDebut: DateIso; dateFin: DateIso }[];
  statistiques: { placesTotales: number; placesRemplies: number; astreintesTotales: number; astreintesRemplies: number };
}

export interface Candidat {
  agentId: number;
  nomAffichage: string;
  nomComplet: string;
  profession: string | null;
  categorie: CategorieAgent;
  disponible: boolean;
  raison: string | null;
  gardesDuMois: number;
}

export type StatutEnvoi = 'EN_ATTENTE' | 'ENVOYE' | 'ECHEC';

export interface Destinataire {
  agentId: number;
  nomAffichage: string;
  email: string | null;
  gardes: number;
  astreintes: number;
  dernierStatut: StatutEnvoi | null;
  dernierEnvoi: string | null;
}

export interface ResultatEnvoi {
  agentId: number;
  nomAffichage: string;
  email: string | null;
  statut: StatutEnvoi;
  erreur: string | null;
}

@Injectable({ providedIn: 'root' })
export class PlanningApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/plannings/gardes`;

  grille(serviceId: number, annee: number, mois: number) {
    return this.http.get<Grille>(this.base, { params: { serviceId, annee, mois } });
  }

  creer(serviceId: number, annee: number, mois: number) {
    return this.http.post<Grille>(this.base, { serviceId, annee, mois });
  }

  changerStatut(id: number, statut: StatutPlanning) {
    return this.http.put<Grille>(`${this.base}/${id}/statut`, { statut });
  }

  candidats(id: number, plageId: number, date: DateIso) {
    return this.http.get<Candidat[]>(`${this.base}/${id}/candidats`, { params: { plageId, date } });
  }

  affecter(id: number, d: { plageId: number; date: DateIso; position: number; agentId: number }) {
    return this.http.post<Grille>(`${this.base}/${id}/affectations`, d);
  }

  liberer(id: number, d: { plageId: number; date: DateIso; position: number }) {
    return this.http.post<Grille>(`${this.base}/${id}/liberations`, d);
  }

  changerMode(id: number, d: { plageId: number; date: DateIso; mode: ModeAffectation }) {
    return this.http.put<Grille>(`${this.base}/${id}/mode`, d);
  }

  candidatsAstreinte(id: number, dateDebut: DateIso) {
    return this.http.get<Candidat[]>(`${this.base}/${id}/astreintes/candidats`, { params: { dateDebut } });
  }

  affecterAstreinte(id: number, d: { dateDebut: DateIso; agentId: number }) {
    return this.http.post<Grille>(`${this.base}/${id}/astreintes`, d);
  }

  libererAstreinte(id: number, dateDebut: DateIso) {
    return this.http.delete<Grille>(`${this.base}/${id}/astreintes`, { params: { dateDebut } });
  }

  // ---------- PDF et envoi ----------

  pdf(id: number) {
    return this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob', observe: 'response' });
  }

  destinataires(id: number) {
    return this.http.get<Destinataire[]>(`${this.base}/${id}/destinataires`);
  }

  envoyer(id: number, agentIds: number[], message: string | null) {
    return this.http.post<ResultatEnvoi[]>(`${this.base}/${id}/envois`, { agentIds, message });
  }
}
