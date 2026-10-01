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
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';

import { ReferentielApi } from '../../core/referentiel.api';
import { messageErreur } from '../../core/api';
import { Agent, AgentRequete, CategorieAgent, Reference, Service } from '../../core/modeles';
import { depuisIso, versIso } from '../../core/dates';

export interface DonneesDialogueAgent {
  agent?: Agent;
  categorie: CategorieAgent;
  services: Service[];
  professions: Reference[];
}

@Component({
  selector: 'app-dialogue-agent',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatButtonToggleModule,
    MatDatepickerModule,
    MatIconModule,
  ],
  host: { class: 'mp-dialogue' },
  templateUrl: './dialogue-agent.html',
  styles: `
    .categorie { grid-column: 1 / -1; margin-bottom: 16px; }
    .categorie mat-button-toggle-group { width: 100%; }
    .categorie mat-button-toggle { flex: 1; }
  `,
})
export class DialogueAgent {
  private readonly api = inject(ReferentielApi);
  private readonly ref = inject(MatDialogRef<DialogueAgent, Agent>);
  readonly d = inject<DonneesDialogueAgent>(MAT_DIALOG_DATA);

  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly titres = ['Dr', 'Pr', 'M.', 'Mme', 'Mlle'];
  readonly niveaux = ['DES', 'Interne', 'Externe', 'Élève infirmier(ère)', 'Élève sage-femme', 'Autre'];
  readonly servicesActifs = this.d.services.filter((s) => s.actif || this.d.agent?.services.some((r) => r.id === s.id));

  private readonly a = this.d.agent;
  readonly f = inject(FormBuilder).group({
    categorie: [this.a?.categorie ?? this.d.categorie, Validators.required],
    titre: [this.a?.titre ?? ''],
    prenom: [this.a?.prenom ?? '', [Validators.required, Validators.maxLength(80)]],
    nom: [this.a?.nom ?? '', [Validators.required, Validators.maxLength(80)]],
    nomAffichage: [this.a?.nomAffichage ?? '', Validators.maxLength(60)],
    professionId: [this.a?.professionId ?? (null as number | null)],
    serviceIds: [
      this.a?.services.map((s) => s.id) ?? (this.servicesActifs.length === 1 ? [this.servicesActifs[0].id] : []),
      Validators.required,
    ],
    matricule: [this.a?.matricule ?? '', Validators.maxLength(30)],
    telephone: [this.a?.telephone ?? '', Validators.maxLength(30)],
    email: [this.a?.email ?? '', [Validators.email, Validators.maxLength(150)]],
    niveau: [this.a?.stage?.niveau ?? ''],
    etablissementOrigine: [this.a?.stage?.etablissementOrigine ?? '', Validators.maxLength(150)],
    debutStage: [this.a?.stage ? depuisIso(this.a.stage.dateDebut) : (null as Date | null)],
    finStage: [this.a?.stage ? depuisIso(this.a.stage.dateFin) : (null as Date | null)],
    actif: [this.a?.actif ?? true],
  });

  private readonly v = toSignal(this.f.valueChanges, { initialValue: this.f.getRawValue() });
  readonly estStagiaire = computed(() => this.v().categorie === 'STAGIAIRE');
  /** Proposition de nom affiché : « Dr Bara Aw ». */
  readonly nomAffichageAuto = computed(() => {
    const v = this.v();
    return [v.titre, v.prenom, v.nom].map((x) => (x ?? '').trim()).filter(Boolean).join(' ');
  });

  valider(): void {
    const v = this.f.getRawValue();
    const stageIncomplet = this.estStagiaire() && (!v.debutStage || !v.finStage);
    if (this.f.invalid || stageIncomplet) {
      this.f.markAllAsTouched();
      if (stageIncomplet) this.erreur.set('Indiquez les dates de début et de fin du stage.');
      return;
    }
    const texte = (x: string | null | undefined) => (x && x.trim() ? x.trim() : null);
    const donnees: AgentRequete = {
      titre: texte(v.titre),
      prenom: v.prenom!.trim(),
      nom: v.nom!.trim(),
      nomAffichage: texte(v.nomAffichage),
      matricule: texte(v.matricule),
      email: texte(v.email),
      telephone: texte(v.telephone),
      categorie: v.categorie!,
      professionId: v.professionId ?? null,
      serviceIds: v.serviceIds ?? [],
      stage: this.estStagiaire()
        ? {
            niveau: texte(v.niveau),
            etablissementOrigine: texte(v.etablissementOrigine),
            serviceId: v.serviceIds?.[0] ?? null,
            dateDebut: versIso(v.debutStage!),
            dateFin: versIso(v.finStage!),
          }
        : null,
      ...(this.a ? { actif: v.actif ?? true } : {}),
    };

    this.envoi.set(true);
    this.erreur.set(null);
    const appel = this.a ? this.api.modifierAgent(this.a.id, donnees) : this.api.creerAgent(donnees);
    appel.subscribe({
      next: (agent) => this.ref.close(agent),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}
