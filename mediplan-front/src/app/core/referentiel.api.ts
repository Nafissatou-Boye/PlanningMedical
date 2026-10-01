import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from './api';
import {
  Agent,
  AgentRequete,
  Conge,
  CongeRequete,
  DateIso,
  ModeAffectation,
  Reference,
  Service,
  TypeGarde,
} from './modeles';

/** Appels à l'API pour les services, le personnel et les congés. */
@Injectable({ providedIn: 'root' })
export class ReferentielApi {
  private readonly http = inject(HttpClient);

  // ---------- Services (chaque écriture renvoie l'arborescence à jour) ----------

  services(): Observable<Service[]> {
    return this.http.get<Service[]>(`${API_URL}/services`);
  }

  creerService(d: { nom: string; code: string | null; referenceDocument: string | null }) {
    return this.http.post<Service[]>(`${API_URL}/services`, d);
  }

  modifierService(id: number, d: { nom: string; code: string | null; referenceDocument: string | null; actif?: boolean }) {
    return this.http.put<Service[]>(`${API_URL}/services/${id}`, d);
  }

  creerSousService(serviceId: number, d: { nom: string }) {
    return this.http.post<Service[]>(`${API_URL}/services/${serviceId}/sous-services`, d);
  }

  modifierSousService(id: number, d: { nom: string; actif?: boolean }) {
    return this.http.put<Service[]>(`${API_URL}/sous-services/${id}`, d);
  }

  creerPlage(sousServiceId: number, d: DonneesPlage) {
    return this.http.post<Service[]>(`${API_URL}/sous-services/${sousServiceId}/plages`, d);
  }

  modifierPlage(id: number, d: DonneesPlage & { actif?: boolean }) {
    return this.http.put<Service[]>(`${API_URL}/plages/${id}`, d);
  }

  creerPoste(serviceId: number, d: DonneesPoste) {
    return this.http.post<Service[]>(`${API_URL}/services/${serviceId}/postes-stage`, d);
  }

  modifierPoste(id: number, d: DonneesPoste & { actif?: boolean }) {
    return this.http.put<Service[]>(`${API_URL}/postes-stage/${id}`, d);
  }

  // ---------- Personnel ----------

  agents(): Observable<Agent[]> {
    return this.http.get<Agent[]>(`${API_URL}/agents`);
  }

  creerAgent(d: AgentRequete) {
    return this.http.post<Agent>(`${API_URL}/agents`, d);
  }

  modifierAgent(id: number, d: AgentRequete) {
    return this.http.put<Agent>(`${API_URL}/agents/${id}`, d);
  }

  professions(): Observable<Reference[]> {
    return this.http.get<Reference[]>(`${API_URL}/professions`);
  }

  creerProfession(libelle: string) {
    return this.http.post<Reference[]>(`${API_URL}/professions`, { libelle });
  }

  // ---------- Congés ----------

  conges(debut?: DateIso, fin?: DateIso): Observable<Conge[]> {
    let params = new HttpParams();
    if (debut && fin) params = params.set('debut', debut).set('fin', fin);
    return this.http.get<Conge[]>(`${API_URL}/conges`, { params });
  }

  creerConge(d: CongeRequete) {
    return this.http.post<Conge>(`${API_URL}/conges`, d);
  }

  modifierConge(id: number, d: CongeRequete) {
    return this.http.put<Conge>(`${API_URL}/conges/${id}`, d);
  }

  supprimerConge(id: number) {
    return this.http.delete<void>(`${API_URL}/conges/${id}`);
  }
}

export interface DonneesPlage {
  libelle: string;
  heureDebut: string;
  heureFin: string;
  typeGarde: TypeGarde;
  modeParDefaut: ModeAffectation;
}

export interface DonneesPoste {
  libelle: string;
  joursActifs: string | null;
  partiel: boolean;
  couleur: string | null;
}
