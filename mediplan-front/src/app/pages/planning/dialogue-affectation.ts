import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { Candidat, PlanningApi } from '../../core/planning.api';
import { StagiairesApi } from '../../core/stagiaires.api';
import { messageErreur } from '../../core/api';
import { DateIso, ModeAffectation } from '../../core/modeles';

/** Une garde (plage + date + position), une astreinte (période) ou un poste de stagiaire (semaine + poste). */
export interface DonneesAffectation {
  planningId: number;
  titre: string;
  sousTitre: string;
  astreinte?: { dateDebut: DateIso };
  garde?: { plageId: number; date: DateIso; position: number; mode: ModeAffectation; places: number };
  stage?: { periodeId: number; posteId: number; position: number };
  occupant: string | null;
}

@Component({
  selector: 'app-dialogue-affectation',
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  host: { class: 'mp-dialogue' },
  templateUrl: './dialogue-affectation.html',
  styleUrl: './dialogue-affectation.scss',
})
export class DialogueAffectation {
  private readonly api = inject(PlanningApi);
  private readonly apiStagiaires = inject(StagiairesApi);
  /** Renvoie la grille à jour (gardes ou stagiaires) à la page qui a ouvert la fenêtre. */
  private readonly ref = inject(MatDialogRef<DialogueAffectation, unknown>);
  readonly d = inject<DonneesAffectation>(MAT_DIALOG_DATA);

  readonly candidats = signal<Candidat[]>([]);
  readonly chargement = signal(false);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly recherche = signal('');
  readonly afficherIndisponibles = signal(false);

  readonly filtres = computed(() => {
    const t = normaliser(this.recherche());
    return this.candidats().filter(
      (c) =>
        (this.afficherIndisponibles() || c.disponible) &&
        (!t || normaliser(`${c.nomAffichage} ${c.nomComplet} ${c.profession ?? ''}`).includes(t)),
    );
  });
  /** « 2 gardes ce mois » ou « 2 postes ce mois ». */
  readonly unite = this.d.stage ? 'poste' : 'garde';
  readonly nbIndisponibles = computed(() => this.candidats().filter((c) => !c.disponible).length);

  constructor() {
    if (!this.d.occupant) this.chargerCandidats();
  }

  private chargerCandidats(): void {
    this.chargement.set(true);
    const st = this.d.stage;
    const appel = st
      ? this.apiStagiaires.candidats(this.d.planningId, st.periodeId, st.posteId)
      : this.d.astreinte
        ? this.api.candidatsAstreinte(this.d.planningId, this.d.astreinte.dateDebut)
        : this.api.candidats(this.d.planningId, this.d.garde!.plageId, this.d.garde!.date);
    appel.subscribe({
      next: (c) => {
        this.candidats.set(c);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  choisir(c: Candidat): void {
    if (!c.disponible || this.envoi()) return;
    const g = this.d.garde;
    const st = this.d.stage;
    if (st) {
      this.executer(this.apiStagiaires.affecter(this.d.planningId, { ...st, agentId: c.agentId }));
      return;
    }
    this.executer(
      this.d.astreinte
        ? this.api.affecterAstreinte(this.d.planningId, { dateDebut: this.d.astreinte.dateDebut, agentId: c.agentId })
        : this.api.affecter(this.d.planningId, { plageId: g!.plageId, date: g!.date, position: g!.position, agentId: c.agentId }),
    );
  }

  liberer(): void {
    const g = this.d.garde;
    if (this.d.stage) {
      this.executer(this.apiStagiaires.liberer(this.d.planningId, this.d.stage));
      return;
    }
    this.executer(
      this.d.astreinte
        ? this.api.libererAstreinte(this.d.planningId, this.d.astreinte.dateDebut)
        : this.api.liberer(this.d.planningId, { plageId: g!.plageId, date: g!.date, position: g!.position }),
    );
  }

  changerMode(mode: ModeAffectation): void {
    const g = this.d.garde!;
    this.executer(this.api.changerMode(this.d.planningId, { plageId: g.plageId, date: g.date, mode }));
  }

  private executer(appel: Observable<unknown>): void {
    this.envoi.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (grille) => this.ref.close(grille),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}

function normaliser(t: string): string {
  return t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
