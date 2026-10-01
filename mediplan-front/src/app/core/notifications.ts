import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { switchMap } from 'rxjs';

import { API_URL } from './api';
import { AuthService } from './auth.service';
import { ReferentielApi } from './referentiel.api';

export type NiveauNotification = 'URGENT' | 'ATTENTION' | 'INFO';

export interface Notification {
  cle: string;
  niveau: NiveauNotification;
  icone: string;
  titre: string;
  detail: string;
  lien: string;
}

const CLE_LUES = 'mediplan.notifications.lues';
const INTERVALLE_MIN = 30_000;

/**
 * Notifications calculées par l'API (congé tombant sur une garde, planning à créer, places à pourvoir…).
 * Les notifications lues sont retenues dans le navigateur.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsCentre {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly referentiel = inject(ReferentielApi);

  readonly liste = signal<Notification[]>([]);
  private readonly lues = signal<Set<string>>(lire());
  private serviceId: number | null = null;
  private dernierChargement = 0;

  readonly nonLues = computed(() => this.liste().filter((n) => !this.lues().has(n.cle)).length);
  readonly urgentes = computed(() => this.liste().filter((n) => n.niveau === 'URGENT' && !this.lues().has(n.cle)).length);

  estLue(n: Notification): boolean {
    return this.lues().has(n.cle);
  }

  /** Recharge (au plus toutes les 30 secondes, sauf si « forcer »). */
  charger(forcer = false): void {
    if (!this.auth.profil()) return;
    if (!forcer && Date.now() - this.dernierChargement < INTERVALLE_MIN) return;
    this.dernierChargement = Date.now();
    const appel = (id: number) => this.http.get<Notification[]>(`${API_URL}/notifications`, { params: { serviceId: id } });
    const obs = this.serviceId
      ? appel(this.serviceId)
      : this.referentiel.services().pipe(
          switchMap((services) => {
            const actifs = services.filter((s) => s.actif);
            const s = actifs.find((x) => x.nom === this.auth.profil()?.service) ?? actifs[0];
            this.serviceId = s?.id ?? null;
            return s ? appel(s.id) : [];
          }),
        );
    obs.subscribe({
      next: (l) => {
        this.liste.set(l);
        // On oublie les clés lues qui n'existent plus (la liste reste courte)
        const cles = new Set(l.map((n) => n.cle));
        this.enregistrer(new Set([...this.lues()].filter((c) => cles.has(c))));
      },
      error: () => this.liste.set([]),
    });
  }

  marquerLue(n: Notification): void {
    this.enregistrer(new Set(this.lues()).add(n.cle));
  }

  toutLire(): void {
    this.enregistrer(new Set(this.liste().map((n) => n.cle)));
  }

  private enregistrer(s: Set<string>): void {
    this.lues.set(s);
    try {
      localStorage.setItem(CLE_LUES, JSON.stringify([...s]));
    } catch {
      /* stockage indisponible : les notifications redeviendront non lues, sans gravité */
    }
  }
}

function lire(): Set<string> {
  try {
    return new Set(JSON.parse(localStorage.getItem(CLE_LUES) ?? '[]'));
  } catch {
    return new Set();
  }
}
