import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';

import { ReferentielApi } from '../../core/referentiel.api';
import { messageErreur } from '../../core/api';
import { Agent, Conge, LIBELLES_TYPE_CONGE } from '../../core/modeles';
import { formatFr, versIso } from '../../core/dates';
import { Confirmation, DonneesConfirmation } from '../../shared/confirmation';
import { DialogueConge, DonneesDialogueConge } from './dialogue-conge';

const MOIS = [
  'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
  'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
];

@Component({
  selector: 'app-conges',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule, MatButtonToggleModule],
  templateUrl: './conges.html',
  styles: `
    .navigation { display: flex; align-items: center; gap: 4px; }
    .navigation strong { min-width: 150px; text-align: center; font-size: 16px; }
    .type { white-space: nowrap; }
    .type.annuel { background: var(--mp-nuit); }
    .type.maladie { background: var(--mp-ferie); }
    .type.maternite { background: var(--mp-weekend); }
    .type.formation { background: var(--mp-jour); }
    .type.indisponibilite { background: #eef2f5; }
    .type.autre { background: var(--mp-astreinte); }
    .pdf { color: var(--mp-vert-texte); }
  `,
})
export class Conges {
  private readonly api = inject(ReferentielApi);
  private readonly dialog = inject(MatDialog);

  readonly libelles = LIBELLES_TYPE_CONGE;
  readonly formatFr = formatFr;

  readonly mois = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  readonly toutAfficher = signal(false);
  readonly conges = signal<Conge[]>([]);
  readonly agents = signal<Agent[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);

  readonly titreMois = computed(() => `${MOIS[this.mois().getMonth()]} ${this.mois().getFullYear()}`);

  constructor() {
    this.api.agents().subscribe({ next: (a) => this.agents.set(a) });
    this.charger();
  }

  changerMois(delta: number): void {
    const m = this.mois();
    this.mois.set(new Date(m.getFullYear(), m.getMonth() + delta, 1));
    this.charger();
  }

  basculerTout(tout: boolean): void {
    this.toutAfficher.set(tout);
    this.charger();
  }

  private charger(): void {
    const m = this.mois();
    const debut = versIso(m);
    const fin = versIso(new Date(m.getFullYear(), m.getMonth() + 1, 0));
    this.chargement.set(true);
    const appel = this.toutAfficher() ? this.api.conges() : this.api.conges(debut, fin);
    appel.subscribe({
      next: (c) => {
        this.conges.set(c);
        this.erreur.set(null);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  ajouter(): void {
    this.ouvrir(undefined);
  }

  modifier(conge: Conge): void {
    this.ouvrir(conge);
  }

  supprimer(conge: Conge): void {
    const data: DonneesConfirmation = {
      titre: 'Supprimer ce congé ?',
      message: `${conge.agent} — du ${formatFr(conge.dateDebut)} au ${formatFr(conge.dateFin)}. L'agent sera de nouveau proposé sur cette période.`,
      action: 'Supprimer',
      danger: true,
    };
    this.dialog
      .open(Confirmation, { data, width: '440px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) return;
        this.api.supprimerConge(conge.id).subscribe({
          next: () => this.conges.set(this.conges().filter((c) => c.id !== conge.id)),
          error: (e) => this.erreur.set(messageErreur(e)),
        });
      });
  }

  private ouvrir(conge: Conge | undefined): void {
    if (this.agents().length === 0) {
      this.erreur.set("Ajoutez d'abord du personnel dans le menu « Personnel ».");
      return;
    }
    const data: DonneesDialogueConge = { conge, agents: this.agents() };
    this.dialog
      .open(DialogueConge, { data, width: '600px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((enregistre?: Conge) => {
        if (enregistre) this.charger();
      });
  }
}
