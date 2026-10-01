import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog } from '@angular/material/dialog';

import { ReferentielApi } from '../../core/referentiel.api';
import { messageErreur } from '../../core/api';
import { Agent, CategorieAgent, Reference, Service } from '../../core/modeles';
import { formatFr } from '../../core/dates';
import { DialogueAgent, DonneesDialogueAgent } from './dialogue-agent';

type FiltreStatut = 'actifs' | 'inactifs' | 'tous';

/**
 * Liste du personnel. La même page sert pour « Stagiaires » (données de route : categorie = STAGIAIRE).
 */
@Component({
  selector: 'app-personnel',
  imports: [
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonToggleModule,
    MatTooltipModule,
    MatProgressBarModule,
  ],
  templateUrl: './personnel.html',
  styles: `
    .nom strong { display: block; }
    .services { display: flex; flex-wrap: wrap; gap: 4px; }
    .services span { padding: 2px 8px; border-radius: 6px; background: var(--mp-fond); font-size: 12px; }
    .compteur { margin-left: auto; font-size: 13px; color: var(--mp-texte-secondaire); }
  `,
})
export class Personnel {
  private readonly api = inject(ReferentielApi);
  private readonly dialog = inject(MatDialog);

  /** Renseigné par la route « stagiaires ». */
  readonly categorie = input<CategorieAgent | undefined>(undefined);
  readonly pageStagiaires = computed(() => this.categorie() === 'STAGIAIRE');
  /** Texte venu de la recherche globale (?q=Dr Ndaw) : pré-remplit le filtre. */
  readonly q = input<string | undefined>(undefined);
  private readonly appliquerQ = effect(() => {
    const q = this.q();
    if (q) {
      this.recherche.set(q);
      this.statut.set('tous');
    }
  });

  readonly agents = signal<Agent[]>([]);
  readonly services = signal<Service[]>([]);
  readonly professions = signal<Reference[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);

  // Filtres
  readonly recherche = signal('');
  readonly serviceId = signal<number | null>(null);
  readonly statut = signal<FiltreStatut>('actifs');
  readonly categorieFiltre = signal<CategorieAgent | 'TOUS'>('TOUS');

  readonly filtres = computed(() => {
    const texte = normaliser(this.recherche());
    const categorie = this.pageStagiaires() ? 'STAGIAIRE' : this.categorieFiltre();
    return this.agents().filter(
      (a) =>
        (categorie === 'TOUS' || a.categorie === categorie) &&
        (this.statut() === 'tous' || a.actif === (this.statut() === 'actifs')) &&
        (this.serviceId() === null || a.services.some((s) => s.id === this.serviceId())) &&
        (!texte ||
          normaliser(`${a.nomAffichage} ${a.prenom} ${a.nom} ${a.matricule ?? ''} ${a.profession ?? ''}`).includes(
            texte,
          )),
    );
  });

  readonly formatFr = formatFr;

  constructor() {
    forkJoin({ agents: this.api.agents(), services: this.api.services(), professions: this.api.professions() })
      .subscribe({
        next: (r) => {
          this.agents.set(r.agents);
          this.services.set(r.services);
          this.professions.set(r.professions);
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

  modifier(agent: Agent): void {
    this.ouvrir(agent);
  }

  private ouvrir(agent: Agent | undefined): void {
    if (this.services().length === 0) {
      this.erreur.set("Créez d'abord un service dans le menu « Services ».");
      return;
    }
    const data: DonneesDialogueAgent = {
      agent,
      categorie: this.pageStagiaires() ? 'STAGIAIRE' : 'PERSONNEL',
      services: this.services(),
      professions: this.professions(),
    };
    this.dialog
      .open(DialogueAgent, { data, width: '640px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((enregistre?: Agent) => {
        if (!enregistre) return;
        const liste = this.agents().filter((a) => a.id !== enregistre.id);
        this.agents.set(
          [...liste, enregistre].sort((x, y) => `${x.nom} ${x.prenom}`.localeCompare(`${y.nom} ${y.prenom}`, 'fr')),
        );
      });
  }
}

/** Minuscules sans accents, pour une recherche tolérante. */
function normaliser(texte: string): string {
  return texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
