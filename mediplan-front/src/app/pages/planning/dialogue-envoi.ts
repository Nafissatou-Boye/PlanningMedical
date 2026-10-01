import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { Destinataire, PlanningApi, ResultatEnvoi } from '../../core/planning.api';
import { API_URL, messageErreur } from '../../core/api';
import { StagiairesApi } from '../../core/stagiaires.api';

export interface DonneesEnvoi {
  planningId: number;
  titre: string;
  /** « stagiaires » : organisation des stagiaires ; sinon planning des gardes. */
  type?: 'gardes' | 'stagiaires';
}

@Component({
  selector: 'app-dialogue-envoi',
  imports: [
    FormsModule,
    DatePipe,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
    RouterLink,
  ],
  host: { class: 'mp-dialogue' },
  templateUrl: './dialogue-envoi.html',
  styleUrl: './dialogue-envoi.scss',
})
export class DialogueEnvoi {
  readonly d = inject<DonneesEnvoi>(MAT_DIALOG_DATA);
  readonly stagiaires = this.d.type === 'stagiaires';
  private readonly api: Pick<PlanningApi, 'destinataires' | 'envoyer'> = this.stagiaires
    ? inject(StagiairesApi)
    : inject(PlanningApi);

  readonly destinataires = signal<Destinataire[]>([]);
  readonly choisis = signal<Set<number>>(new Set());
  readonly message = signal('');
  readonly chargement = signal(true);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly resultats = signal<ResultatEnvoi[] | null>(null);
  /** false : aucun compte Gmail n'est réglé (Paramètres › Envoi des e-mails). */
  readonly emailConfigure = signal(true);

  readonly nbChoisis = computed(() => this.choisis().size);
  readonly avecEmail = computed(() => this.destinataires().filter((x) => !!x.email));
  readonly toutChoisi = computed(() => this.avecEmail().length > 0 && this.avecEmail().every((x) => this.choisis().has(x.agentId)));
  readonly nbEnvoyes = computed(() => (this.resultats() ?? []).filter((r) => r.statut === 'ENVOYE').length);
  /** Couleur du bilan : vert si tout est parti, orange si une partie, rouge si rien. */
  readonly etatBilan = computed(() => {
    const total = this.resultats()?.length ?? 0;
    return this.nbEnvoyes() === 0 ? 'echec' : this.nbEnvoyes() < total ? 'partiel' : 'ok';
  });

  constructor() {
    inject(HttpClient)
      .get<{ configure: boolean }>(`${API_URL}/configuration/email`)
      .subscribe({ next: (c) => this.emailConfigure.set(c.configure) });
    this.api.destinataires(this.d.planningId).subscribe({
      next: (liste) => {
        this.destinataires.set(liste);
        // Par défaut : tous ceux qui ont une garde ou une astreinte et une adresse e-mail
        this.choisis.set(new Set(liste.filter((x) => x.email && x.gardes + x.astreintes > 0).map((x) => x.agentId)));
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  basculer(id: number, coche: boolean): void {
    const s = new Set(this.choisis());
    if (coche) s.add(id);
    else s.delete(id);
    this.choisis.set(s);
  }

  toutBasculer(coche: boolean): void {
    this.choisis.set(coche ? new Set(this.avecEmail().map((x) => x.agentId)) : new Set());
  }

  envoyer(): void {
    if (!this.nbChoisis() || this.envoi()) return;
    this.envoi.set(true);
    this.erreur.set(null);
    this.api.envoyer(this.d.planningId, [...this.choisis()], this.message().trim() || null).subscribe({
      next: (r) => {
        this.resultats.set(r);
        this.envoi.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}
