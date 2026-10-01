import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { API_URL } from './api';
import { DateIso } from './modeles';
import { Candidat, Destinataire, Place, ResultatEnvoi, StatutPlanning } from './planning.api';

/** Colonne : un poste de stage (PME/2nd VPA, Salle Prog.1, VPA…). */
export interface ColonnePoste {
  posteId: number;
  libelle: string;
  joursActifs: string | null;
  partiel: boolean;
  couleur: string | null;
}

/** Ligne : une semaine (« Du 31/08 au 06/09/26 »). */
export interface Semaine {
  periodeId: number | null;
  dateDebut: DateIso;
  dateFin: DateIso;
  libelle: string;
}

export interface CaseStage {
  periodeId: number;
  posteId: number;
  stagiaires: Place[];
}

export interface GrilleStagiaires {
  planningId: number | null;
  statut: StatutPlanning | null;
  serviceId: number;
  service: string;
  annee: number;
  mois: number;
  dateDocument: DateIso | null;
  numeroReference: string | null;
  postes: ColonnePoste[];
  semaines: Semaine[];
  cases: CaseStage[];
  conges: { agent: string; dateDebut: DateIso; dateFin: DateIso }[];
  statistiques: { casesTotales: number; casesRemplies: number; stagiairesActifs: number; stagiairesSansPoste: number };
}

@Injectable({ providedIn: 'root' })
export class StagiairesApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/plannings/stagiaires`;

  grille(serviceId: number, annee: number, mois: number) {
    return this.http.get<GrilleStagiaires>(this.base, { params: { serviceId, annee, mois } });
  }

  creer(serviceId: number, annee: number, mois: number) {
    return this.http.post<GrilleStagiaires>(this.base, { serviceId, annee, mois });
  }

  changerStatut(id: number, statut: StatutPlanning) {
    return this.http.put<GrilleStagiaires>(`${this.base}/${id}/statut`, { statut });
  }

  ajouterPeriode(id: number, d: { dateDebut: DateIso; dateFin: DateIso }) {
    return this.http.post<GrilleStagiaires>(`${this.base}/${id}/periodes`, d);
  }

  modifierPeriode(id: number, periodeId: number, d: { dateDebut: DateIso; dateFin: DateIso }) {
    return this.http.put<GrilleStagiaires>(`${this.base}/${id}/periodes/${periodeId}`, d);
  }

  supprimerPeriode(id: number, periodeId: number) {
    return this.http.delete<GrilleStagiaires>(`${this.base}/${id}/periodes/${periodeId}`);
  }

  candidats(id: number, periodeId: number, posteId: number) {
    return this.http.get<Candidat[]>(`${this.base}/${id}/candidats`, { params: { periodeId, posteId } });
  }

  affecter(id: number, d: { periodeId: number; posteId: number; position: number; agentId: number }) {
    return this.http.post<GrilleStagiaires>(`${this.base}/${id}/affectations`, d);
  }

  liberer(id: number, d: { periodeId: number; posteId: number; position: number }) {
    return this.http.post<GrilleStagiaires>(`${this.base}/${id}/liberations`, d);
  }

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
