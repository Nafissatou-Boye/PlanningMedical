import { Injectable, computed, signal } from '@angular/core';

/** Profil de l'administrateur, saisi lors de la première visite. */
export interface Profil {
  nom: string;
  prenom: string;
  nomUtilisateur: string;
  service: string;
  poste: string;
}

const CLE_STOCKAGE = 'mediplan.profil';

/**
 * Gère le profil de l'utilisateur unique de l'application.
 *
 * PROVISOIRE : en attendant le backend, le profil est gardé dans le navigateur
 * (localStorage). Il sera ensuite enregistré en base via l'API Spring Boot
 * (POST /api/installation), avec le mot de passe.
 */
@Injectable({ providedIn: 'root' })
export class ProfilService {
  private readonly _profil = signal<Profil | null>(this.lire());

  readonly profil = this._profil.asReadonly();
  readonly estConfigure = computed(() => this._profil() !== null);
  readonly nomComplet = computed(() => {
    const p = this._profil();
    return p ? `${p.prenom} ${p.nom}` : '';
  });

  enregistrer(profil: Profil): void {
    const propre: Profil = {
      nom: profil.nom.trim(),
      prenom: profil.prenom.trim(),
      nomUtilisateur: profil.nomUtilisateur.trim(),
      service: profil.service.trim(),
      poste: profil.poste.trim(),
    };
    try {
      localStorage.setItem(CLE_STOCKAGE, JSON.stringify(propre));
    } catch {
      // Stockage indisponible (navigation privée) : le profil reste en mémoire.
    }
    this._profil.set(propre);
  }

  private lire(): Profil | null {
    try {
      const brut = localStorage.getItem(CLE_STOCKAGE);
      return brut ? (JSON.parse(brut) as Profil) : null;
    } catch {
      return null;
    }
  }
}
