import { Component, computed, inject, signal } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { ReferentielApi } from '../../core/referentiel.api';
import { messageErreur } from '../../core/api';
import {
  CLASSES_TYPE_GARDE,
  LIBELLES_MODE,
  LIBELLES_TYPE_GARDE,
  Plage,
  PosteStage,
  Service,
  SousService,
} from '../../core/modeles';
import { heureCourte } from '../../core/dates';
import { DialogueService, DialogueSousService, DialoguePlage, DialoguePoste } from './services-dialogues';

@Component({
  selector: 'app-services',
  imports: [UpperCasePipe, MatButtonModule, MatIconModule, MatTooltipModule, MatSlideToggleModule, MatProgressBarModule],
  templateUrl: './services.html',
  styleUrl: './services.scss',
})
export class Services {
  private readonly api = inject(ReferentielApi);
  private readonly dialog = inject(MatDialog);

  readonly services = signal<Service[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly afficherArchives = signal(false);

  readonly visibles = computed(() =>
    this.afficherArchives() ? this.services() : this.services().filter((s) => s.actif),
  );

  readonly libellesType = LIBELLES_TYPE_GARDE;
  readonly classesType = CLASSES_TYPE_GARDE;
  readonly libellesMode = LIBELLES_MODE;
  readonly heure = heureCourte;

  constructor() {
    this.api.services().subscribe({
      next: (s) => {
        this.services.set(s);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  /** Éléments visibles selon le filtre « archivés ». */
  sousServices(s: Service): SousService[] {
    return this.afficherArchives() ? s.sousServices : s.sousServices.filter((ss) => ss.actif);
  }

  plages(ss: SousService): Plage[] {
    return this.afficherArchives() ? ss.plages : ss.plages.filter((p) => p.actif);
  }

  postes(s: Service): PosteStage[] {
    return this.afficherArchives() ? s.postesStage : s.postesStage.filter((p) => p.actif);
  }

  /** Colonnes actives uniquement : c'est ce qui apparaîtra dans le tableau et le PDF. */
  colonnesApercu(s: Service): { ss: SousService; plages: Plage[] }[] {
    return s.sousServices
      .filter((ss) => ss.actif)
      .map((ss) => ({ ss, plages: ss.plages.filter((p) => p.actif) }))
      .filter((c) => c.plages.length > 0);
  }

  nouveauService(): void {
    this.ouvrir(DialogueService, {});
  }

  modifierService(service: Service): void {
    this.ouvrir(DialogueService, { service });
  }

  nouveauSousService(service: Service): void {
    this.ouvrir(DialogueSousService, { serviceId: service.id });
  }

  modifierSousService(service: Service, sousService: SousService): void {
    this.ouvrir(DialogueSousService, { serviceId: service.id, sousService });
  }

  nouvellePlage(ss: SousService): void {
    this.ouvrir(DialoguePlage, { sousServiceId: ss.id, sousServiceNom: ss.nom });
  }

  modifierPlage(ss: SousService, plage: Plage): void {
    this.ouvrir(DialoguePlage, { sousServiceId: ss.id, sousServiceNom: ss.nom, plage });
  }

  nouveauPoste(service: Service): void {
    this.ouvrir(DialoguePoste, { serviceId: service.id });
  }

  modifierPoste(service: Service, poste: PosteStage): void {
    this.ouvrir(DialoguePoste, { serviceId: service.id, poste });
  }

  private ouvrir(composant: unknown, data: object): void {
    this.dialog
      .open(composant as never, { data, width: '560px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((resultat) => {
        if (Array.isArray(resultat)) this.services.set(resultat as Service[]);
      });
  }
}
