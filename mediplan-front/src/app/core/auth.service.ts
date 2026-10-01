import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, firstValueFrom, map, of, tap } from 'rxjs';

import { API_URL } from './api';

/** Profil de l'administrateur (renvoyé par l'API, jamais de mot de passe). */
export interface Profil {
  id: number;
  nom: string;
  prenom: string;
  nomUtilisateur: string;
  service: string | null;
  poste: string;
}

export interface DonneesInstallation {
  nom: string;
  prenom: string;
  nomUtilisateur: string;
  service: string;
  poste: string;
  motDePasse: string;
}

interface ReponseConnexion {
  jeton: string;
  expireDansSecondes: number;
  profil: Profil;
}

const CLE_JETON = 'mediplan.jeton';

/**
 * Connexion de l'utilisateur unique.
 * Seul le jeton JWT est gardé dans le navigateur ; le profil est relu depuis l'API.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _profil = signal<Profil | null>(null);
  private readonly _jeton = signal<string | null>(this.lireJeton());
  private installee: boolean | null = null;

  readonly profil = this._profil.asReadonly();
  readonly jeton = this._jeton.asReadonly();
  readonly estConnecte = computed(() => this._profil() !== null);
  readonly nomComplet = computed(() => {
    const p = this._profil();
    return p ? `${p.prenom} ${p.nom}` : '';
  });

  /** Première visite ou pas ? (résultat mis en cache) */
  async estInstallee(): Promise<boolean> {
    if (this.installee === null) {
      const r = await firstValueFrom(this.http.get<{ installee: boolean }>(`${API_URL}/installation/statut`));
      this.installee = r.installee;
    }
    return this.installee;
  }

  installer(donnees: DonneesInstallation): Observable<Profil> {
    return this.http.post<ReponseConnexion>(`${API_URL}/installation`, donnees).pipe(
      tap(() => (this.installee = true)),
      map((r) => this.ouvrirSession(r)),
    );
  }

  connecter(nomUtilisateur: string, motDePasse: string): Observable<Profil> {
    return this.http
      .post<ReponseConnexion>(`${API_URL}/auth/connexion`, { nomUtilisateur, motDePasse })
      .pipe(map((r) => this.ouvrirSession(r)));
  }

  /** Au rechargement de la page : relit le profil si un jeton est présent. */
  restaurerSession(): Observable<boolean> {
    if (this._profil()) return of(true);
    if (!this._jeton()) return of(false);
    return this.http.get<Profil>(`${API_URL}/auth/moi`).pipe(
      tap((p) => this._profil.set(p)),
      map(() => true),
      catchError(() => {
        this.deconnecter();
        return of(false);
      }),
    );
  }

  deconnecter(): void {
    this._profil.set(null);
    this._jeton.set(null);
    try {
      localStorage.removeItem(CLE_JETON);
    } catch {
      /* stockage indisponible */
    }
  }

  private ouvrirSession(r: ReponseConnexion): Profil {
    this._jeton.set(r.jeton);
    this._profil.set(r.profil);
    try {
      localStorage.setItem(CLE_JETON, r.jeton);
    } catch {
      /* stockage indisponible : la session durera jusqu'au rechargement */
    }
    return r.profil;
  }

  private lireJeton(): string | null {
    try {
      return localStorage.getItem(CLE_JETON);
    } catch {
      return null;
    }
  }
}
