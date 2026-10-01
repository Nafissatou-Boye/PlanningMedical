import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';

import { GrilleStagiaires, Semaine, StagiairesApi } from '../../core/stagiaires.api';
import { messageErreur } from '../../core/api';
import { depuisIso, versIso } from '../../core/dates';

export interface DonneesPeriode {
  planningId: number;
  /** Absente : ajout d'une nouvelle ligne. */
  semaine?: Semaine;
  /** Proposition pour une nouvelle ligne (lundi → dimanche après la dernière semaine). */
  proposition?: { debut: Date; fin: Date };
  /** Nombre de stagiaires déjà placés sur cette ligne (pour prévenir avant suppression). */
  places: number;
}

/** Ajouter, modifier ou supprimer une ligne « SEMAINE » (dates libres, lundi → dimanche par défaut). */
@Component({
  selector: 'app-dialogue-periode',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatIconModule,
  ],
  host: { class: 'mp-dialogue' },
  template: `
    <h2 mat-dialog-title>
      {{ d.semaine ? 'Modifier la semaine' : 'Ajouter une semaine' }}
      <small>{{ d.semaine?.libelle ?? 'Nouvelle ligne du planning' }}</small>
    </h2>
    <mat-dialog-content>
      @if (erreur()) {
        <p class="mp-erreur" role="alert"><mat-icon>error</mat-icon> {{ erreur() }}</p>
      }
      @if (confirmerSuppression()) {
        <p class="alerte">
          <mat-icon>warning</mat-icon>
          <span>
            Supprimer la ligne « {{ d.semaine?.libelle }} »
            @if (d.places) {
              et libérer les {{ d.places }} place{{ d.places > 1 ? 's' : '' }} déjà attribuée{{ d.places > 1 ? 's' : '' }}
            }
            ?
          </span>
        </p>
      } @else {
        <form [formGroup]="f" id="form-periode" (ngSubmit)="enregistrer()">
          <mat-form-field appearance="outline" class="pleine">
            <mat-label>Période</mat-label>
            <mat-date-range-input [rangePicker]="picker">
              <input matStartDate formControlName="debut" placeholder="Du" />
              <input matEndDate formControlName="fin" placeholder="Au" />
            </mat-date-range-input>
            <mat-datepicker-toggle matIconSuffix [for]="picker" />
            <mat-date-range-picker #picker />
            @if (nombreJours()) {
              <mat-hint>{{ nombreJours() }} jour{{ nombreJours()! > 1 ? 's' : '' }}</mat-hint>
            }
            <mat-error>Indiquez la date de début et de fin.</mat-error>
          </mat-form-field>
        </form>
        <p class="aide">Par défaut une semaine va du lundi au dimanche ; vous pouvez choisir d'autres dates.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (confirmerSuppression()) {
        <button mat-button type="button" (click)="confirmerSuppression.set(false)" [disabled]="envoi()">Annuler</button>
        <button mat-flat-button class="mp-bouton-danger" type="button" (click)="supprimer()" [disabled]="envoi()">
          <mat-icon>delete</mat-icon> Supprimer
        </button>
      } @else {
        @if (d.semaine) {
          <button mat-button type="button" class="supprimer" (click)="confirmerSuppression.set(true)" [disabled]="envoi()">
            <mat-icon>delete</mat-icon> Supprimer la ligne
          </button>
        }
        <button mat-button type="button" mat-dialog-close [disabled]="envoi()">Annuler</button>
        <button mat-flat-button class="mp-bouton-principal" type="submit" form="form-periode" [disabled]="envoi()">
          Enregistrer
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    h2 small { display: block; font-size: 13px; font-weight: 400; color: var(--mp-texte-secondaire); }
    .pleine { width: 100%; margin-top: 4px; }
    .aide { margin: 4px 0 0; font-size: 13px; color: var(--mp-texte-secondaire); }
    .alerte { display: flex; gap: 10px; align-items: flex-start; margin: 0; color: #b26a00; }
    .supprimer { margin-right: auto; color: #c62828; }
  `,
})
export class DialoguePeriode {
  private readonly api = inject(StagiairesApi);
  private readonly ref = inject(MatDialogRef<DialoguePeriode, GrilleStagiaires>);
  readonly d = inject<DonneesPeriode>(MAT_DIALOG_DATA);

  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly confirmerSuppression = signal(false);

  readonly f = inject(FormBuilder).group({
    debut: [
      this.d.semaine ? depuisIso(this.d.semaine.dateDebut) : (this.d.proposition?.debut ?? null),
      Validators.required,
    ],
    fin: [this.d.semaine ? depuisIso(this.d.semaine.dateFin) : (this.d.proposition?.fin ?? null), Validators.required],
  });

  private readonly v = toSignal(this.f.valueChanges, { initialValue: this.f.getRawValue() });

  readonly nombreJours = computed(() => {
    const { debut, fin } = this.v();
    if (!debut || !fin) return null;
    return Math.round((fin.getTime() - debut.getTime()) / 86_400_000) + 1;
  });

  enregistrer(): void {
    if (this.f.invalid || this.envoi()) {
      this.f.markAllAsTouched();
      return;
    }
    const dates = { dateDebut: versIso(this.f.value.debut!), dateFin: versIso(this.f.value.fin!) };
    const s = this.d.semaine;
    this.executer(
      s?.periodeId
        ? this.api.modifierPeriode(this.d.planningId, s.periodeId, dates)
        : this.api.ajouterPeriode(this.d.planningId, dates),
    );
  }

  supprimer(): void {
    const id = this.d.semaine?.periodeId;
    if (id) this.executer(this.api.supprimerPeriode(this.d.planningId, id));
  }

  private executer(appel: ReturnType<StagiairesApi['ajouterPeriode']>): void {
    this.envoi.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (g) => this.ref.close(g),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
        this.confirmerSuppression.set(false);
      },
    });
  }
}
