import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';

import { ReferentielApi } from '../../core/referentiel.api';
import { messageErreur } from '../../core/api';
import { PosteStage, Plage, Service, SousService, TypeGarde } from '../../core/modeles';
import { heureCourte } from '../../core/dates';

const MODULES = [
  ReactiveFormsModule,
  MatDialogModule,
  MatButtonModule,
  MatFormFieldModule,
  MatInputModule,
  MatSelectModule,
  MatSlideToggleModule,
  MatButtonToggleModule,
  MatCheckboxModule,
  MatIconModule,
];

/** Comportement commun : envoi, affichage de l'erreur, fermeture avec l'arborescence à jour. */
abstract class DialogueBase {
  protected readonly api = inject(ReferentielApi);
  private readonly ref = inject(MatDialogRef<unknown, Service[]>);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);

  protected envoyer(appel: Observable<Service[]>): void {
    this.envoi.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (services) => this.ref.close(services),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}

const PIED = `
  <mat-dialog-actions align="end">
    <button mat-button type="button" mat-dialog-close [disabled]="envoi()">Annuler</button>
    <button mat-flat-button class="mp-bouton-principal" type="submit" [attr.form]="idFormulaire" [disabled]="envoi()">
      Enregistrer
    </button>
  </mat-dialog-actions>`;

const ERREUR = `
  @if (erreur()) {
    <p class="mp-erreur" role="alert"><mat-icon>error</mat-icon> {{ erreur() }}</p>
  }`;

// ===================================================================== Service

@Component({
  selector: 'app-dialogue-service',
  imports: MODULES,
  host: { class: 'mp-dialogue' },
  template: `
    <h2 mat-dialog-title>{{ d.service ? 'Modifier le service' : 'Nouveau service' }}</h2>
    <mat-dialog-content>
      ${ERREUR}
      <form [id]="idFormulaire" [formGroup]="f" (ngSubmit)="valider()" class="grille">
        <mat-form-field appearance="outline" class="pleine">
          <mat-label>Nom du service</mat-label>
          <input matInput formControlName="nom" placeholder="Anesthésie – Réanimation" cdkFocusInitial />
          <mat-error>Le nom est obligatoire.</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Code</mat-label>
          <input matInput formControlName="code" placeholder="ANESTH-REA" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Référence du document</mat-label>
          <input matInput formControlName="referenceDocument" placeholder="MSAS/CHNP/ANESTH-REA" />
          <mat-hint>Affichée en haut du PDF</mat-hint>
        </mat-form-field>
        @if (d.service) {
          <mat-slide-toggle formControlName="actif" class="pleine">Service actif</mat-slide-toggle>
        }
      </form>
    </mat-dialog-content>
    ${PIED}
  `,
})
export class DialogueService extends DialogueBase {
  readonly d = inject<{ service?: Service }>(MAT_DIALOG_DATA);
  readonly idFormulaire = 'form-service';
  readonly f = inject(FormBuilder).nonNullable.group({
    nom: [this.d.service?.nom ?? '', [Validators.required, Validators.maxLength(150)]],
    code: [this.d.service?.code ?? '', Validators.maxLength(30)],
    referenceDocument: [this.d.service?.referenceDocument ?? '', Validators.maxLength(100)],
    actif: [this.d.service?.actif ?? true],
  });

  valider(): void {
    if (this.f.invalid) return this.f.markAllAsTouched();
    const v = this.f.getRawValue();
    const donnees = { nom: v.nom, code: v.code || null, referenceDocument: v.referenceDocument || null };
    this.envoyer(
      this.d.service
        ? this.api.modifierService(this.d.service.id, { ...donnees, actif: v.actif })
        : this.api.creerService(donnees),
    );
  }
}

// ===================================================================== Sous-service

@Component({
  selector: 'app-dialogue-sous-service',
  imports: MODULES,
  host: { class: 'mp-dialogue' },
  template: `
    <h2 mat-dialog-title>{{ d.sousService ? 'Modifier le sous-service' : 'Nouveau sous-service' }}</h2>
    <mat-dialog-content>
      ${ERREUR}
      <p class="aide">Un sous-service devient un groupe de colonnes du tableau de garde (ex. REA, Bloc central).</p>
      <form [id]="idFormulaire" [formGroup]="f" (ngSubmit)="valider()" class="grille">
        <mat-form-field appearance="outline" class="pleine">
          <mat-label>Nom du sous-service</mat-label>
          <input matInput formControlName="nom" placeholder="Anesthésie – Bloc central" cdkFocusInitial />
          <mat-error>Le nom est obligatoire.</mat-error>
        </mat-form-field>
        @if (d.sousService) {
          <mat-slide-toggle formControlName="actif" class="pleine">Afficher dans le tableau</mat-slide-toggle>
        }
      </form>
    </mat-dialog-content>
    ${PIED}
  `,
  styles: `.aide { margin: 0 0 16px; color: var(--mp-texte-secondaire); }`,
})
export class DialogueSousService extends DialogueBase {
  readonly d = inject<{ serviceId: number; sousService?: SousService }>(MAT_DIALOG_DATA);
  readonly idFormulaire = 'form-sous-service';
  readonly f = inject(FormBuilder).nonNullable.group({
    nom: [this.d.sousService?.nom ?? '', [Validators.required, Validators.maxLength(150)]],
    actif: [this.d.sousService?.actif ?? true],
  });

  valider(): void {
    if (this.f.invalid) return this.f.markAllAsTouched();
    const v = this.f.getRawValue();
    this.envoyer(
      this.d.sousService
        ? this.api.modifierSousService(this.d.sousService.id, v)
        : this.api.creerSousService(this.d.serviceId, { nom: v.nom }),
    );
  }
}

// ===================================================================== Plage horaire

@Component({
  selector: 'app-dialogue-plage',
  imports: MODULES,
  host: { class: 'mp-dialogue' },
  template: `
    <h2 mat-dialog-title>{{ d.plage ? 'Modifier la plage horaire' : 'Nouvelle plage horaire' }}</h2>
    <mat-dialog-content>
      ${ERREUR}
      <p class="aide">Sous-service : <strong>{{ d.sousServiceNom }}</strong>. Chaque plage devient une colonne : ajoutez-en autant que nécessaire (jour, nuit…).</p>
      <form [id]="idFormulaire" [formGroup]="f" (ngSubmit)="valider()" class="grille">
        <mat-form-field appearance="outline">
          <mat-label>Début</mat-label>
          <input matInput type="time" formControlName="heureDebut" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Fin</mat-label>
          <input matInput type="time" formControlName="heureFin" />
          @if (lendemain()) {
            <mat-hint>Se termine le lendemain</mat-hint>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="pleine">
          <mat-label>Libellé de la colonne</mat-label>
          <input matInput formControlName="libelle" [placeholder]="libelleAuto()" />
          <mat-hint>Laissez vide pour « {{ libelleAuto() }} »</mat-hint>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Type de garde</mat-label>
          <mat-select formControlName="typeGarde">
            <mat-option value="JOUR">Jour</mat-option>
            <mat-option value="NUIT">Nuit</mat-option>
            <mat-option value="H24">24h</mat-option>
          </mat-select>
          @if (incoherence()) {
            <mat-hint class="alerte">{{ incoherence() }}</mat-hint>
          }
        </mat-form-field>
        <div class="mode">
          <span>Places par défaut</span>
          <mat-button-toggle-group formControlName="modeParDefaut" aria-label="Mode par défaut">
            <mat-button-toggle value="UNIQUE">Unique</mat-button-toggle>
            <mat-button-toggle value="BINOME">Binôme</mat-button-toggle>
          </mat-button-toggle-group>
        </div>
        @if (d.plage) {
          <mat-slide-toggle formControlName="actif" class="pleine">Afficher dans le tableau</mat-slide-toggle>
        }
      </form>
    </mat-dialog-content>
    ${PIED}
  `,
  styles: `
    .aide { margin: 0 0 16px; color: var(--mp-texte-secondaire); }
    .mode { display: flex; flex-direction: column; gap: 6px; font-size: 12px; color: var(--mp-texte-secondaire); }
    .alerte { color: #b3261e; }
  `,
})
export class DialoguePlage extends DialogueBase {
  readonly d = inject<{ sousServiceId: number; sousServiceNom: string; plage?: Plage }>(MAT_DIALOG_DATA);
  readonly idFormulaire = 'form-plage';
  readonly f = inject(FormBuilder).nonNullable.group({
    heureDebut: [this.d.plage?.heureDebut.slice(0, 5) ?? '08:00', Validators.required],
    heureFin: [this.d.plage?.heureFin.slice(0, 5) ?? '16:00', Validators.required],
    libelle: [this.d.plage?.libelle ?? '', Validators.maxLength(30)],
    typeGarde: [this.d.plage?.typeGarde ?? 'JOUR', Validators.required],
    modeParDefaut: [this.d.plage?.modeParDefaut ?? 'UNIQUE', Validators.required],
    actif: [this.d.plage?.actif ?? true],
  });

  private readonly valeurs = toSignal(this.f.valueChanges, { initialValue: this.f.getRawValue() });
  readonly libelleAuto = computed(() => {
    const v = this.valeurs();
    return v.heureDebut && v.heureFin ? `${heureCourte(v.heureDebut)}-${heureCourte(v.heureFin)}` : '08H-16H';
  });
  readonly lendemain = computed(() => {
    const v = this.valeurs();
    return !!v.heureDebut && !!v.heureFin && v.heureFin <= v.heureDebut;
  });

  /** Type déduit des horaires : même heure de début et de fin = 24h, fin le lendemain = Nuit, sinon Jour. */
  readonly typeDeduit = computed<TypeGarde>(() => {
    const v = this.valeurs();
    if (!v.heureDebut || !v.heureFin) return 'JOUR';
    if (v.heureDebut === v.heureFin) return 'H24';
    return v.heureFin < v.heureDebut ? 'NUIT' : 'JOUR';
  });

  /** Alerte si le type choisi ne correspond pas aux horaires (ex. « 24h » pour 08H-16H). */
  readonly incoherence = computed(() => {
    const choisi = this.valeurs().typeGarde;
    const deduit = this.typeDeduit();
    if (choisi === 'H24' && deduit !== 'H24') return 'Une garde de 24h commence et finit à la même heure (ex. 08H-08H).';
    if (choisi !== 'H24' && deduit === 'H24') return 'Même heure de début et de fin : c\'est une garde de 24h.';
    return null;
  });

  constructor() {
    super();
    // En création, le type suit les horaires tant que l'utilisateur ne l'a pas choisi lui-même.
    if (!this.d.plage) {
      this.f.controls.heureDebut.valueChanges.subscribe(() => this.proposerType());
      this.f.controls.heureFin.valueChanges.subscribe(() => this.proposerType());
    }
  }

  private proposerType(): void {
    if (!this.f.controls.typeGarde.dirty) this.f.controls.typeGarde.setValue(this.typeDeduit());
  }

  valider(): void {
    if (this.f.invalid) return this.f.markAllAsTouched();
    const v = this.f.getRawValue();
    const donnees = {
      libelle: v.libelle.trim() || this.libelleAuto(),
      heureDebut: v.heureDebut,
      heureFin: v.heureFin,
      typeGarde: v.typeGarde,
      modeParDefaut: v.modeParDefaut,
    };
    this.envoyer(
      this.d.plage
        ? this.api.modifierPlage(this.d.plage.id, { ...donnees, actif: v.actif })
        : this.api.creerPlage(this.d.sousServiceId, donnees),
    );
  }
}

// ===================================================================== Poste de stage

const JOURS = ['LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI', 'DIMANCHE'];

@Component({
  selector: 'app-dialogue-poste',
  imports: MODULES,
  host: { class: 'mp-dialogue' },
  template: `
    <h2 mat-dialog-title>{{ d.poste ? 'Modifier le poste de stage' : 'Nouveau poste de stage' }}</h2>
    <mat-dialog-content>
      ${ERREUR}
      <form [id]="idFormulaire" [formGroup]="f" (ngSubmit)="valider()" class="grille">
        <mat-form-field appearance="outline" class="pleine">
          <mat-label>Libellé de la colonne</mat-label>
          <input matInput formControlName="libelle" placeholder="Salle Prog.1" cdkFocusInitial />
          <mat-error>Le libellé est obligatoire.</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline" class="pleine">
          <mat-label>Jours concernés</mat-label>
          <mat-select formControlName="jours" multiple>
            @for (j of jours; track j) {
              <mat-option [value]="j">{{ j.charAt(0) + j.slice(1).toLowerCase() }}</mat-option>
            }
          </mat-select>
          <mat-hint>Laissez vide si le poste couvre toute la période</mat-hint>
        </mat-form-field>
        <mat-checkbox formControlName="partiel" class="pleine">
          Poste tenu en plus d'un autre poste (ex. VPA)
        </mat-checkbox>
        <mat-checkbox formControlName="colore" class="pleine">Colonne colorée dans le PDF</mat-checkbox>
        @if (f.controls.colore.value) {
          <label class="couleur pleine">Couleur <input type="color" formControlName="couleur" /></label>
        }
        @if (d.poste) {
          <mat-slide-toggle formControlName="actif" class="pleine">Afficher dans le planning</mat-slide-toggle>
        }
      </form>
    </mat-dialog-content>
    ${PIED}
  `,
  styles: `.couleur { display: flex; align-items: center; gap: 12px; margin: 8px 0 12px; }`,
})
export class DialoguePoste extends DialogueBase {
  readonly d = inject<{ serviceId: number; poste?: PosteStage }>(MAT_DIALOG_DATA);
  readonly idFormulaire = 'form-poste';
  readonly jours = JOURS;
  readonly f = inject(FormBuilder).nonNullable.group({
    libelle: [this.d.poste?.libelle ?? '', [Validators.required, Validators.maxLength(80)]],
    jours: [this.d.poste?.joursActifs ? this.d.poste.joursActifs.split(',') : ([] as string[])],
    partiel: [this.d.poste?.partiel ?? false],
    colore: [!!this.d.poste?.couleur],
    couleur: [this.d.poste?.couleur ?? '#F9E0D2'],
    actif: [this.d.poste?.actif ?? true],
  });

  valider(): void {
    if (this.f.invalid) return this.f.markAllAsTouched();
    const v = this.f.getRawValue();
    const joursTries = JOURS.filter((j) => v.jours.includes(j));
    const donnees = {
      libelle: v.libelle,
      // Aucun jour ou tous les jours = toute la période
      joursActifs: joursTries.length && joursTries.length < JOURS.length ? joursTries.join(',') : null,
      partiel: v.partiel,
      couleur: v.colore ? v.couleur.toUpperCase() : null,
    };
    this.envoyer(
      this.d.poste
        ? this.api.modifierPoste(this.d.poste.id, { ...donnees, actif: v.actif })
        : this.api.creerPoste(this.d.serviceId, donnees),
    );
  }
}
