import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';

import { ReferentielApi } from '../../core/referentiel.api';
import { messageErreur } from '../../core/api';
import { Agent, Conge, CongeRequete, LIBELLES_TYPE_CONGE, TypeConge } from '../../core/modeles';
import { depuisIso, versIso } from '../../core/dates';

export interface DonneesDialogueConge {
  conge?: Conge;
  agents: Agent[];
}

@Component({
  selector: 'app-dialogue-conge',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatDatepickerModule,
    MatIconModule,
  ],
  host: { class: 'mp-dialogue' },
  template: `
    <h2 mat-dialog-title>{{ d.conge ? 'Modifier le congé' : 'Ajouter un congé' }}</h2>
    <mat-dialog-content>
      @if (erreur()) {
        <p class="mp-erreur" role="alert"><mat-icon>error</mat-icon> {{ erreur() }}</p>
      }
      <form id="form-conge" [formGroup]="f" (ngSubmit)="valider()" class="grille">
        <mat-form-field appearance="outline" class="pleine">
          <mat-label>Agent</mat-label>
          <input
            matInput
            formControlName="agent"
            [matAutocomplete]="choixAgent"
            placeholder="Tapez un nom…"
            cdkFocusInitial
          />
          <mat-autocomplete #choixAgent [displayWith]="afficherAgent" autoActiveFirstOption>
            @for (a of propositions(); track a.id) {
              <mat-option [value]="a">
                {{ a.nomAffichage }}
                <small class="discret">{{ a.prenom }} {{ a.nom }}{{ a.categorie === 'STAGIAIRE' ? ' · stagiaire' : '' }}</small>
              </mat-option>
            }
          </mat-autocomplete>
          <mat-error>Choisissez un agent dans la liste.</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select formControlName="type">
            @for (t of types; track t) {
              <mat-option [value]="t">{{ libelles[t] }}</mat-option>
            }
          </mat-select>
          @if (f.controls.type.value === 'INDISPONIBILITE') {
            <mat-hint>Bloque l'agent, sans apparaître dans le bloc « CONGÉS » du PDF</mat-hint>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Période</mat-label>
          <mat-date-range-input [rangePicker]="periode">
            <input matStartDate formControlName="debut" placeholder="Du" />
            <input matEndDate formControlName="fin" placeholder="Au" />
          </mat-date-range-input>
          <mat-datepicker-toggle matIconSuffix [for]="periode" />
          <mat-date-range-picker #periode />
          @if (nombreJours()) {
            <mat-hint>{{ nombreJours() }} jour{{ nombreJours()! > 1 ? 's' : '' }}</mat-hint>
          }
          <mat-error>Indiquez la date de début et de fin.</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="pleine">
          <mat-label>Motif (facultatif)</mat-label>
          <input matInput formControlName="motif" maxlength="255" />
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="envoi()">Annuler</button>
      <button mat-flat-button class="mp-bouton-principal" type="submit" form="form-conge" [disabled]="envoi()">
        Enregistrer
      </button>
    </mat-dialog-actions>
  `,
  styles: `.discret { margin-left: 6px; color: var(--mp-texte-secondaire); }`,
})
export class DialogueConge {
  private readonly api = inject(ReferentielApi);
  private readonly ref = inject(MatDialogRef<DialogueConge, Conge>);
  readonly d = inject<DonneesDialogueConge>(MAT_DIALOG_DATA);

  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly types = Object.keys(LIBELLES_TYPE_CONGE) as TypeConge[];
  readonly libelles = LIBELLES_TYPE_CONGE;

  private readonly agentInitial = this.d.conge ? this.d.agents.find((a) => a.id === this.d.conge!.agentId) : undefined;

  readonly f = inject(FormBuilder).group({
    agent: [(this.agentInitial ?? null) as Agent | string | null, [Validators.required, agentChoisi]],
    type: [this.d.conge?.type ?? ('ANNUEL' as TypeConge), Validators.required],
    debut: [this.d.conge ? depuisIso(this.d.conge.dateDebut) : (null as Date | null), Validators.required],
    fin: [this.d.conge ? depuisIso(this.d.conge.dateFin) : (null as Date | null), Validators.required],
    motif: [this.d.conge?.motif ?? ''],
  });

  private readonly v = toSignal(this.f.valueChanges, { initialValue: this.f.getRawValue() });

  /** Agents actifs dont le nom contient le texte tapé. */
  readonly propositions = computed(() => {
    const saisie = this.v().agent;
    const texte = typeof saisie === 'string' ? normaliser(saisie) : '';
    return this.d.agents
      .filter((a) => a.actif)
      .filter((a) => !texte || normaliser(`${a.nomAffichage} ${a.prenom} ${a.nom}`).includes(texte))
      .slice(0, 50);
  });

  readonly nombreJours = computed(() => {
    const { debut, fin } = this.v();
    if (!debut || !fin) return null;
    return Math.round((fin.getTime() - debut.getTime()) / 86_400_000) + 1;
  });

  afficherAgent(a: Agent | string | null): string {
    return a && typeof a === 'object' ? a.nomAffichage : (a ?? '');
  }

  valider(): void {
    if (this.f.invalid) return this.f.markAllAsTouched();
    const v = this.f.getRawValue();
    const donnees: CongeRequete = {
      agentId: (v.agent as Agent).id,
      type: v.type!,
      dateDebut: versIso(v.debut!),
      dateFin: versIso(v.fin!),
      motif: v.motif?.trim() || null,
    };
    this.envoi.set(true);
    this.erreur.set(null);
    const appel = this.d.conge ? this.api.modifierConge(this.d.conge.id, donnees) : this.api.creerConge(donnees);
    appel.subscribe({
      next: (c) => this.ref.close(c),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}

/** Le champ doit contenir un agent choisi dans la liste, pas un simple texte. */
function agentChoisi(c: { value: unknown }) {
  return c.value && typeof c.value === 'object' ? null : { agentChoisi: true };
}

function normaliser(texte: string): string {
  return texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
