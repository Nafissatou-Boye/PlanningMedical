import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { API_URL } from '../../core/api';
import { AuthService } from '../../core/auth.service';
import { ReferentielApi } from '../../core/referentiel.api';
import { FeriesApi } from '../../core/feries.api';
import { PlanningApi } from '../../core/planning.api';
import { QUESTIONS, THEMES, Theme } from './contenu-aide';

interface Etape {
  titre: string;
  detail: string;
  fait: boolean;
  lien: string;
  action: string;
}

/** Centre d'aide : démarrage guidé (calculé sur les vraies données) et questions fréquentes. */
@Component({
  selector: 'app-aide',
  imports: [RouterLink, MatExpansionModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  templateUrl: './aide.html',
  styleUrl: './aide.scss',
})
export class Aide {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly referentiel = inject(ReferentielApi);
  private readonly feries = inject(FeriesApi);
  private readonly planning = inject(PlanningApi);

  readonly themes = THEMES;
  readonly theme = signal<Theme | null>(null);
  readonly recherche = signal('');
  readonly etapes = signal<Etape[] | null>(null);
  /** Question choisie dans la recherche globale (?q=…) : elle est filtrée et ouverte. */
  readonly q = input<string | undefined>(undefined);
  private readonly appliquerQ = effect(() => {
    const q = this.q();
    if (q) {
      this.theme.set(null);
      this.recherche.set(q);
    }
  });

  readonly nbFaites = computed(() => (this.etapes() ?? []).filter((e) => e.fait).length);

  readonly questions = computed(() => {
    const t = normaliser(this.recherche());
    return QUESTIONS.filter(
      (q) =>
        (!this.theme() || q.theme === this.theme()) &&
        (!t || normaliser(q.question + ' ' + q.reponse.replace(/<[^>]+>/g, '')).includes(t)),
    );
  });

  constructor() {
    const maintenant = new Date();
    const suivant = new Date(maintenant.getFullYear(), maintenant.getMonth() + 1, 1);
    const nomMois = suivant.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
    const sur = <T>(o: import('rxjs').Observable<T>, defaut: T) => o.pipe(catchError(() => of(defaut)));

    forkJoin({
      services: sur(this.referentiel.services(), []),
      agents: sur(this.referentiel.agents(), []),
      feries: sur(this.feries.lister(maintenant.getFullYear()), []),
      email: sur(this.http.get<{ configure: boolean; expediteur: string | null }>(`${API_URL}/configuration/email`), {
        configure: false,
        expediteur: null,
      }),
    }).subscribe(({ services, agents, feries, email }) => {
      const s = services.find((x) => x.actif && x.nom === this.auth.profil()?.service) ?? services.find((x) => x.actif);
      const plages = s ? s.sousServices.reduce((n, ss) => n + ss.plages.length, 0) : 0;
      const etapes: Etape[] = [
        {
          titre: 'Service et référence du document',
          detail: s?.referenceDocument ? `${s.nom} · ${s.referenceDocument}` : "Ajoutez la référence (ex. MSAS/CHNP/ANESTH-REA) pour l'en-tête du PDF",
          fait: !!s?.referenceDocument,
          lien: '/services',
          action: 'Services',
        },
        {
          titre: 'Colonnes du planning',
          detail: plages ? `${plages} plage${plages > 1 ? 's' : ''} horaire${plages > 1 ? 's' : ''}` : 'Ajoutez des sous-services et leurs plages horaires',
          fait: plages > 0,
          lien: '/services',
          action: 'Services',
        },
        {
          titre: 'Personnel',
          detail: agents.length ? `${agents.length} membre${agents.length > 1 ? 's' : ''} enregistré${agents.length > 1 ? 's' : ''}` : 'Ajoutez les médecins, infirmiers et stagiaires',
          fait: agents.length > 0,
          lien: '/personnel',
          action: 'Personnel',
        },
        {
          titre: `Jours fériés ${maintenant.getFullYear()}`,
          detail: feries.length ? `${feries.length} jour${feries.length > 1 ? 's' : ''} enregistré${feries.length > 1 ? 's' : ''}` : 'Ajoutez-les en un clic',
          fait: feries.length > 0,
          lien: '/parametres',
          action: 'Paramètres',
        },
        {
          titre: 'Envoi des e-mails',
          detail: email.configure ? `Envoi depuis ${email.expediteur}` : "Indiquez l'adresse Gmail qui enverra les plannings",
          fait: email.configure,
          lien: '/parametres',
          action: 'Paramètres',
        },
        {
          titre: `Planning ${/^[aeiouyéè]/i.test(nomMois) ? "d'" : 'de '}${nomMois}`,
          detail: 'À préparer avant le début du mois',
          fait: false,
          lien: '/planning',
          action: 'Planning',
        },
      ];
      this.etapes.set(etapes);
      if (s) {
        this.planning.grille(s.id, suivant.getFullYear(), suivant.getMonth() + 1).subscribe({
          next: (g) => {
            const l = [...(this.etapes() ?? [])];
            const e = l[l.length - 1];
            l[l.length - 1] = {
              ...e,
              fait: !!g.planningId,
              detail: g.planningId
                ? `Créé · ${g.statistiques.placesRemplies}/${g.statistiques.placesTotales} places remplies`
                : e.detail,
            };
            this.etapes.set(l);
          },
        });
      }
    });
  }

  choisirTheme(t: Theme): void {
    this.theme.set(this.theme() === t ? null : t);
  }
}

function normaliser(t: string): string {
  return t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
