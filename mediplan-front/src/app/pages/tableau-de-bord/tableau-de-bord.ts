import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AuthService } from '../../core/auth.service';
import { ReferentielApi } from '../../core/referentiel.api';
import { messageErreur } from '../../core/api';
import { TypeGarde } from '../../core/modeles';
import { versIso } from '../../core/dates';
import { Grille, PlanningApi } from '../../core/planning.api';
import { Indicateurs, MembreEquipe, StatutJour, TableauDeBordApi } from '../../core/tableau-de-bord.api';

const MOIS = [
  'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
  'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
];
const LIBELLES: Record<TypeGarde, string> = { JOUR: 'Jour', NUIT: 'Nuit', H24: '24h' };

/** Une pastille du calendrier : un type de garde avec ses places remplies / à pourvoir. */
interface Pastille {
  classe: string;
  libelle: string;
  remplies?: number;
  total?: number;
  noms?: string[];
}

interface CaseCalendrier {
  date: string;
  numero: number;
  horsMois: boolean;
  aujourdhui: boolean;
  weekend: boolean;
  ferie: string | null;
  pastilles: Pastille[];
}

const STATUTS: Record<StatutJour, { libelle: string; classe: string }> = {
  EN_GARDE: { libelle: 'En garde', classe: 'en-garde' },
  DE_GARDE_AUJOURDHUI: { libelle: "De garde aujourd'hui", classe: 'de-garde' },
  EN_CONGE: { libelle: 'En congé', classe: 'conge' },
  DISPONIBLE: { libelle: 'Disponible', classe: 'disponible' },
};

@Component({
  selector: 'app-tableau-de-bord',
  imports: [MatButtonModule, MatIconModule, MatButtonToggleModule, MatProgressBarModule, MatTooltipModule, RouterLink],
  templateUrl: './tableau-de-bord.html',
  styleUrl: './tableau-de-bord.scss',
})
export class TableauDeBord {
  private readonly auth = inject(AuthService);
  private readonly referentiel = inject(ReferentielApi);
  private readonly planning = inject(PlanningApi);
  private readonly tableau = inject(TableauDeBordApi);
  private readonly router = inject(Router);

  protected readonly profil = this.auth.profil;
  readonly statuts = STATUTS;

  readonly serviceId = signal<number | null>(null);
  readonly service = signal<string>('');
  readonly indicateurs = signal<Indicateurs | null>(null);
  /** Grilles chargées, par mois (« 2026-9 ») : une semaine peut être à cheval sur deux mois. */
  readonly grilles = signal<Map<string, Grille>>(new Map());
  readonly grille = computed(() => this.grilles().get(cleMois(this.mois())) ?? null);
  readonly mois = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  readonly vue = signal<'mois' | 'semaine'>('mois');
  /** Lundi de la semaine affichée en vue « Semaine ». */
  readonly lundi = signal(lundiDe(new Date()));
  readonly erreur = signal<string | null>(null);
  readonly chargement = signal(true);
  readonly equipeComplete = signal(false);

  readonly titreMois = computed(() => `${MOIS[this.mois().getMonth()]} ${this.mois().getFullYear()}`);
  /** « de septembre 2026 » / « d'octobre 2026 » */
  readonly deMois = computed(() => avecDe(this.titreMois().toLowerCase()));
  readonly deMoisTitre = computed(() => avecDe(this.titreMois()));
  readonly titreSemaine = computed(() => {
    const l = this.lundi();
    const d = new Date(l.getFullYear(), l.getMonth(), l.getDate() + 6);
    return `${l.getDate()} ${MOIS[l.getMonth()].slice(0, 3).toLowerCase()}. – ${d.getDate()} ${MOIS[d.getMonth()].slice(0, 3).toLowerCase()}.`;
  });

  // ---------- Indicateurs ----------

  /** Évolution des gardes planifiées par rapport au mois précédent (en %). */
  readonly evolutionGardes = computed(() => {
    const i = this.indicateurs();
    if (!i || i.gardesMoisPrecedent === 0) return null;
    return Math.round((1000 * (i.gardesDuMois - i.gardesMoisPrecedent)) / i.gardesMoisPrecedent) / 10;
  });

  readonly evolutionCouverture = computed(() => {
    const i = this.indicateurs();
    return i && i.couverturePrecedente > 0 ? i.couverture - i.couverturePrecedente : null;
  });

  readonly equipeVisible = computed(() => {
    const e = this.indicateurs()?.equipe ?? [];
    return this.equipeComplete() ? e : e.slice(0, 6);
  });

  // ---------- Calendrier ----------

  /** Pastilles de chaque jour du mois chargé : « Jour 2/2 », « Nuit 1/2 », « Astreinte », « Férié ». */
  private readonly pastillesParJour = computed(() => {
    const m = new Map<string, Pastille[]>();
    for (const g of this.grilles().values()) this.ajouterPastilles(g, m);
    return m;
  });

  private ajouterPastilles(g: Grille, m: Map<string, Pastille[]>): void {
    if (!g.planningId) return; // planning pas encore créé : rien à compter
    {
    const cases = new Map(g.cases.map((c) => [`${c.plageId}|${c.date}`, c]));
    for (const j of g.jours) {
      const parType = new Map<TypeGarde, Pastille>();
      for (const ss of g.colonnes) {
        for (const p of ss.plages) {
          const c = cases.get(`${p.plageId}|${j.date}`);
          const places = (c?.mode ?? p.modeParDefaut) === 'BINOME' ? 2 : 1;
          const x = parType.get(p.typeGarde) ?? { classe: p.typeGarde.toLowerCase(), libelle: LIBELLES[p.typeGarde], remplies: 0, total: 0, noms: [] };
          x.total! += places;
          x.remplies! += c?.agents.length ?? 0;
          for (const a of c?.agents ?? []) x.noms!.push(`${ss.nom} ${p.libelle} : ${a.nomAffichage}`);
          parType.set(p.typeGarde, x);
        }
      }
      const liste = (['JOUR', 'NUIT', 'H24'] as TypeGarde[]).filter((t) => parType.has(t)).map((t) => parType.get(t)!);
      const ast = g.astreintes.find((a) => a.dateDebut <= j.date && j.date <= a.dateFin);
      if (ast?.nomAffichage && ast.dateDebut === j.date) {
        liste.push({ classe: 'astreinte', libelle: 'Astreinte', noms: [`Astreinte : ${ast.nomAffichage}`] });
      }
      m.set(j.date, liste);
    }
    }
  }

  readonly casesMois = computed<CaseCalendrier[]>(() => {
    const debut = lundiDe(this.mois());
    const fin = new Date(this.mois().getFullYear(), this.mois().getMonth() + 1, 0);
    const jours: CaseCalendrier[] = [];
    for (let d = debut; d <= fin || d.getDay() !== 1; d = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1)) {
      jours.push(this.caseDe(d));
    }
    return jours;
  });

  readonly casesSemaine = computed<CaseCalendrier[]>(() => {
    const l = this.lundi();
    return Array.from({ length: 7 }, (_, i) => this.caseDe(new Date(l.getFullYear(), l.getMonth(), l.getDate() + i)));
  });

  private caseDe(d: Date): CaseCalendrier {
    const iso = versIso(d);
    const g = this.grilles().get(cleMois(d));
    const jour = g?.jours.find((j) => j.date === iso);
    // En vue « Semaine », tous les jours sont affichés, même ceux du mois voisin
    const horsMois = this.vue() === 'mois' && d.getMonth() !== this.mois().getMonth();
    return {
      date: iso,
      numero: d.getDate(),
      horsMois,
      aujourdhui: iso === versIso(new Date()),
      weekend: d.getDay() === 0 || d.getDay() === 6,
      ferie: jour?.ferie ?? null,
      pastilles: horsMois ? [] : (this.pastillesParJour().get(iso) ?? []),
    };
  }

  constructor() {
    this.referentiel.services().subscribe({
      next: (liste) => {
        const actifs = liste.filter((s) => s.actif);
        const s = actifs.find((x) => x.nom === this.profil()?.service) ?? actifs[0];
        if (!s) {
          this.chargement.set(false);
          return;
        }
        this.serviceId.set(s.id);
        this.service.set(s.nom);
        this.tableau.indicateurs(s.id).subscribe({
          next: (i) => this.indicateurs.set(i),
          error: (e) => this.erreur.set(messageErreur(e)),
        });
        this.chargerGrille(this.mois());
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  /** Charge la grille d'un mois (une seule fois). */
  private chargerGrille(mois: Date): void {
    const id = this.serviceId();
    if (!id || this.grilles().has(cleMois(mois))) return;
    this.chargement.set(true);
    this.planning.grille(id, mois.getFullYear(), mois.getMonth() + 1).subscribe({
      next: (g) => {
        this.grilles.set(new Map(this.grilles()).set(cleMois(mois), g));
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  choisirVue(v: 'mois' | 'semaine'): void {
    this.vue.set(v);
    if (v === 'semaine') {
      // La semaine affichée : celle d'aujourd'hui si elle est dans le mois, sinon la première du mois
      const auj = new Date();
      const ref = auj.getMonth() === this.mois().getMonth() && auj.getFullYear() === this.mois().getFullYear() ? auj : this.mois();
      const l = lundiDe(ref);
      this.lundi.set(l);
      const jeudi = new Date(l.getFullYear(), l.getMonth(), l.getDate() + 3);
      this.mois.set(new Date(jeudi.getFullYear(), jeudi.getMonth(), 1));
      this.chargerSemaine();
    }
  }

  /** Les deux mois d'une semaine à cheval (ex. 28 sept. – 4 oct.). */
  private chargerSemaine(): void {
    const l = this.lundi();
    this.chargerGrille(l);
    this.chargerGrille(new Date(l.getFullYear(), l.getMonth(), l.getDate() + 6));
  }

  precedent(): void {
    this.deplacer(-1);
  }

  suivant(): void {
    this.deplacer(1);
  }

  private deplacer(sens: number): void {
    if (this.vue() === 'mois') {
      const m = this.mois();
      this.mois.set(new Date(m.getFullYear(), m.getMonth() + sens, 1));
      this.chargerGrille(this.mois());
      return;
    }
    const l = this.lundi();
    const n = new Date(l.getFullYear(), l.getMonth(), l.getDate() + 7 * sens);
    this.lundi.set(n);
    // Le mois chargé suit le jeudi de la semaine (celui qui porte la majorité des jours)
    const jeudi = new Date(n.getFullYear(), n.getMonth(), n.getDate() + 3);
    this.mois.set(new Date(jeudi.getFullYear(), jeudi.getMonth(), 1));
    this.chargerSemaine();
  }

  ouvrirPlanning(): void {
    this.router.navigate(['/planning']);
  }

  initiales(m: MembreEquipe): string {
    const mots = m.nomAffichage.replace(/^(Dr|Pr|M\.|Mme|Mlle)\s+/i, '').split(/\s+/).filter(Boolean);
    return ((mots[0]?.[0] ?? '') + (mots[1]?.[0] ?? '')).toUpperCase();
  }

  nbAPourvoir(c: CaseCalendrier): number {
    return c.pastilles.reduce((n, p) => n + ((p.total ?? 0) - (p.remplies ?? 0)), 0);
  }
}

function cleMois(d: Date): string {
  return `${d.getFullYear()}-${d.getMonth() + 1}`;
}

function lundiDe(d: Date): Date {
  const decalage = (d.getDay() + 6) % 7;
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - decalage);
}

function avecDe(mois: string): string {
  return /^[aeiouyéèAEIOUY]/.test(mois) ? `d'${mois}` : `de ${mois}`;
}
