import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonToggleModule } from '@angular/material/button-toggle';

import { ReferentielApi } from '../../core/referentiel.api';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/api';
import { Service } from '../../core/modeles';
import { LigneAgent, Rapport, RapportsApi } from '../../core/rapports.api';

const MOIS_COURTS = ['Janv.', 'Févr.', 'Mars', 'Avr.', 'Mai', 'Juin', 'Juil.', 'Août', 'Sept.', 'Oct.', 'Nov.', 'Déc.'];
const MOIS_LONGS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];

type Filtre = 'tous' | 'PERSONNEL' | 'STAGIAIRE';

/** Rapport annuel : couverture mois par mois et équité de la répartition des gardes. */
@Component({
  selector: 'app-rapports',
  imports: [
    FormsModule,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatButtonToggleModule,
  ],
  templateUrl: './rapports.html',
  styleUrl: './rapports.scss',
})
export class Rapports {
  private readonly referentiel = inject(ReferentielApi);
  private readonly api = inject(RapportsApi);
  private readonly auth = inject(AuthService);

  readonly moisCourts = MOIS_COURTS;
  readonly moisLongs = MOIS_LONGS;
  readonly services = signal<Service[]>([]);
  readonly serviceId = signal<number | null>(null);
  readonly annee = signal(new Date().getFullYear());
  readonly rapport = signal<Rapport | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly filtre = signal<Filtre>('tous');

  readonly lignes = computed(() => {
    const f = this.filtre();
    return (this.rapport()?.agents ?? []).filter((a) => f === 'tous' || a.categorie === f);
  });

  /** Moyenne de gardes par agent actif ayant eu au moins une garde (sert à repérer les écarts). */
  readonly moyenne = computed(() => {
    const l = this.lignes().filter((a) => a.total > 0);
    return l.length ? l.reduce((n, a) => n + a.total, 0) / l.length : 0;
  });

  readonly maxCellule = computed(() => Math.max(1, ...this.lignes().flatMap((a) => a.parMois)));
  readonly maxTotal = computed(() => Math.max(1, ...this.lignes().map((a) => a.total)));

  readonly plusSollicite = computed(() => this.lignes().find((a) => a.total > 0) ?? null);
  readonly sansGarde = computed(() => this.lignes().filter((a) => a.total === 0 && a.actif).length);

  constructor() {
    this.referentiel.services().subscribe({
      next: (liste) => {
        const actifs = liste.filter((s) => s.actif);
        this.services.set(actifs);
        const s = actifs.find((x) => x.nom === this.auth.profil()?.service) ?? actifs[0];
        if (s) {
          this.serviceId.set(s.id);
          this.charger();
        } else {
          this.chargement.set(false);
        }
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  choisirService(id: number): void {
    this.serviceId.set(id);
    this.charger();
  }

  changerAnnee(delta: number): void {
    this.annee.set(this.annee() + delta);
    this.charger();
  }

  private charger(): void {
    const id = this.serviceId();
    if (!id) return;
    this.chargement.set(true);
    this.erreur.set(null);
    this.api.rapport(id, this.annee()).subscribe({
      next: (r) => {
        this.rapport.set(r);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  /** Intensité de la case (0 → fond clair, max → bleu médical) : une seule teinte, du clair au foncé. */
  intensite(n: number): string {
    if (!n) return 'var(--mp-fond)';
    const t = 0.18 + 0.82 * (n / this.maxCellule());
    return `color-mix(in srgb, var(--mp-bleu-medical) ${Math.round(t * 100)}%, var(--mp-bleu-ciel))`;
  }

  texteFonce(n: number): boolean {
    return n > 0 && n / this.maxCellule() > 0.55;
  }

  /** Écart à la moyenne, en %, pour signaler une charge très inégale. */
  ecart(a: LigneAgent): number | null {
    const m = this.moyenne();
    return m && a.total ? Math.round((100 * (a.total - m)) / m) : null;
  }

  // ---------- Export CSV (s'ouvre dans Excel) ----------

  exporter(): void {
    const r = this.rapport();
    if (!r) return;
    const sep = ';';
    const lignes: string[][] = [
      ['Agent', 'Catégorie', 'Profession', ...MOIS_COURTS, 'Total', 'Jour', 'Nuit', '24h', 'Week-end/férié', 'Astreintes', "Jours d'absence"],
      ...this.lignes().map((a) => [
        a.nomAffichage,
        a.categorie === 'STAGIAIRE' ? 'Stagiaire' : 'Personnel',
        a.profession ?? '',
        ...a.parMois.map(String),
        String(a.total), String(a.jour), String(a.nuit), String(a.h24), String(a.weekendFerie), String(a.astreintes), String(a.joursAbsence),
      ]),
      [],
      ['Couverture', '', '', ...r.mois.map((m) => (m.planningCree ? m.couverture + '%' : '')), r.couvertureMoyenne + '%'],
    ];
    const csv = '﻿' + lignes.map((l) => l.map((c) => `"${c.replace(/"/g, '""')}"`).join(sep)).join('\r\n');
    const lien = document.createElement('a');
    lien.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    lien.download = `rapport-gardes-${r.service.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-')}-${r.annee}.csv`;
    lien.click();
    setTimeout(() => URL.revokeObjectURL(lien.href), 60_000);
  }
}
