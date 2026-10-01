import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UpperCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTabsModule } from '@angular/material/tabs';
import { MatDialog } from '@angular/material/dialog';

import { ReferentielApi } from '../../core/referentiel.api';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/api';
import { Service } from '../../core/modeles';
import { depuisIso, formatFr } from '../../core/dates';
import { CaseStage, ColonnePoste, GrilleStagiaires, Semaine, StagiairesApi } from '../../core/stagiaires.api';
import { DialogueAffectation, DonneesAffectation } from '../planning/dialogue-affectation';
import { DialogueEnvoi, DonneesEnvoi } from '../planning/dialogue-envoi';
import { Personnel } from '../personnel/personnel';
import { DialoguePeriode, DonneesPeriode } from './dialogue-periode';

const MOIS = [
  'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
  'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
];
const JOURS: Record<string, string> = {
  LUNDI: 'Lundi', MARDI: 'Mardi', MERCREDI: 'Mercredi', JEUDI: 'Jeudi', VENDREDI: 'Vendredi', SAMEDI: 'Samedi', DIMANCHE: 'Dimanche',
};
const CLE_ONGLET = 'mediplan.stagiaires.onglet';

/**
 * Page « Stagiaires » : l'organisation mensuelle (une ligne par semaine, une colonne par poste,
 * deux stagiaires au plus par case) et la liste des stagiaires.
 */
@Component({
  selector: 'app-stagiaires',
  imports: [
    FormsModule,
    UpperCasePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatTabsModule,
    Personnel,
  ],
  templateUrl: './stagiaires.html',
  styleUrls: ['../planning/planning.scss', './stagiaires.scss'],
})
export class Stagiaires {
  private readonly referentiel = inject(ReferentielApi);
  private readonly api = inject(StagiairesApi);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  readonly onglet = signal(lireOnglet());
  /** Recherche globale (?q=nom) : ouvre la liste filtrée sur ce stagiaire. */
  readonly q = input<string | undefined>(undefined);
  private readonly appliquerQ = effect(() => {
    if (this.q()) this.onglet.set(1);
  });
  readonly services = signal<Service[]>([]);
  readonly serviceId = signal<number | null>(null);
  readonly mois = signal(prochainMoisParDefaut());
  readonly grille = signal<GrilleStagiaires | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  readonly pdfEnCours = signal(false);

  readonly titreMois = computed(() => `${MOIS[this.mois().getMonth()]} ${this.mois().getFullYear()}`);
  /** « de septembre 2026 » / « d'octobre 2026 » */
  readonly deMois = computed(() => avecDe(this.titreMois().toLowerCase()));
  readonly deMoisTitre = computed(() => avecDe(this.titreMois()));
  readonly formatFr = formatFr;

  /** « periodeId|posteId » → case. */
  readonly cases = computed(() => {
    const m = new Map<string, CaseStage>();
    for (const c of this.grille()?.cases ?? []) m.set(`${c.periodeId}|${c.posteId}`, c);
    return m;
  });

  readonly couverture = computed(() => {
    const s = this.grille()?.statistiques;
    return s && s.casesTotales ? Math.round((100 * s.casesRemplies) / s.casesTotales) : 0;
  });

  constructor() {
    this.referentiel.services().subscribe({
      next: (liste) => {
        const actifs = liste.filter((s) => s.actif);
        this.services.set(actifs);
        const profil = this.auth.profil();
        const parDefaut = actifs.find((s) => s.nom === profil?.service) ?? actifs[0];
        if (parDefaut) {
          this.serviceId.set(parDefaut.id);
          this.charger();
        } else {
          this.chargement.set(false);
        }
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  choisirOnglet(i: number): void {
    this.onglet.set(i);
    try {
      localStorage.setItem(CLE_ONGLET, String(i));
    } catch {
      /* stockage indisponible : sans importance */
    }
  }

  choisirService(id: number): void {
    this.serviceId.set(id);
    this.charger();
  }

  changerMois(delta: number): void {
    const m = this.mois();
    this.mois.set(new Date(m.getFullYear(), m.getMonth() + delta, 1));
    this.charger();
  }

  private charger(): void {
    const id = this.serviceId();
    if (!id) return;
    this.chargement.set(true);
    this.erreur.set(null);
    this.api.grille(id, this.mois().getFullYear(), this.mois().getMonth() + 1).subscribe({
      next: (g) => {
        this.grille.set(g);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  creer(): void {
    const id = this.serviceId();
    if (!id) return;
    this.executer(this.api.creer(id, this.mois().getFullYear(), this.mois().getMonth() + 1));
  }

  publier(publie: boolean): void {
    const g = this.grille();
    if (g?.planningId) this.executer(this.api.changerStatut(g.planningId, publie ? 'PUBLIE' : 'BROUILLON'));
  }

  private executer(appel: ReturnType<StagiairesApi['creer']>): void {
    this.envoi.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (g) => {
        this.grille.set(g);
        this.envoi.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }

  // ---------- Cases ----------

  occupant(s: Semaine, p: ColonnePoste, position: number): string | null {
    return this.cases().get(`${s.periodeId}|${p.posteId}`)?.stagiaires.find((x) => x.position === position)?.nomAffichage ?? null;
  }

  /** La 2e place n'apparaît que lorsque la 1re est prise (ex. « Dr Ndaw / Dr Lo »). */
  places(s: Semaine, p: ColonnePoste): number[] {
    const c = this.cases().get(`${s.periodeId}|${p.posteId}`);
    return c && c.stagiaires.length > 0 ? [1, 2] : [1];
  }

  jours(p: ColonnePoste): string {
    if (!p.joursActifs) return '';
    return p.joursActifs
      .split(/[,;\s]+/)
      .filter(Boolean)
      .map((j) => JOURS[j.toUpperCase()] ?? j)
      .join('-');
  }

  ouvrirCase(s: Semaine, p: ColonnePoste, position: number): void {
    const g = this.grille();
    if (!g?.planningId || !s.periodeId) return;
    const data: DonneesAffectation = {
      planningId: g.planningId,
      titre: p.libelle + (p.joursActifs ? ` (${this.jours(p)})` : ''),
      sousTitre: `${s.libelle}${position === 2 ? ' · 2e stagiaire' : ''}`,
      stage: { periodeId: s.periodeId, posteId: p.posteId, position },
      occupant: this.occupant(s, p, position),
    };
    this.dialog
      .open(DialogueAffectation, { data, width: '520px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((n?: unknown) => {
        if (n) this.grille.set(n as GrilleStagiaires);
      });
  }

  // ---------- Semaines ----------

  modifierSemaine(s: Semaine): void {
    const g = this.grille();
    if (!g?.planningId) return;
    const places = (g.cases ?? []).filter((c) => c.periodeId === s.periodeId).reduce((n, c) => n + c.stagiaires.length, 0);
    this.ouvrirPeriode({ planningId: g.planningId, semaine: s, places });
  }

  ajouterSemaine(): void {
    const g = this.grille();
    if (!g?.planningId) return;
    const derniere = g.semaines.at(-1);
    const debut = derniere
      ? new Date(depuisIso(derniere.dateFin).getTime() + 86_400_000)
      : new Date(g.annee, g.mois - 1, 1);
    const fin = new Date(debut.getFullYear(), debut.getMonth(), debut.getDate() + 6);
    this.ouvrirPeriode({ planningId: g.planningId, proposition: { debut, fin }, places: 0 });
  }

  private ouvrirPeriode(data: DonneesPeriode): void {
    this.dialog
      .open(DialoguePeriode, { data, width: '440px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((g?: GrilleStagiaires) => {
        if (g) this.grille.set(g);
      });
  }

  // ---------- PDF et envoi ----------

  ouvrirPdf(): void {
    const g = this.grille();
    if (!g?.planningId) return;
    const onglet = window.open('', '_blank');
    this.pdfEnCours.set(true);
    this.api.pdf(g.planningId).subscribe({
      next: (reponse) => {
        this.pdfEnCours.set(false);
        const url = URL.createObjectURL(reponse.body!);
        if (onglet) onglet.location.href = url;
        else window.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (e) => {
        onglet?.close();
        this.pdfEnCours.set(false);
        this.erreur.set(messageErreur(e));
      },
    });
  }

  telechargerPdf(): void {
    const g = this.grille();
    if (!g?.planningId) return;
    this.pdfEnCours.set(true);
    this.api.pdf(g.planningId).subscribe({
      next: (reponse) => {
        this.pdfEnCours.set(false);
        const nom =
          /filename="?([^";]+)"?/.exec(reponse.headers.get('Content-Disposition') ?? '')?.[1] ??
          'organisation-stagiaires.pdf';
        const lien = document.createElement('a');
        lien.href = URL.createObjectURL(reponse.body!);
        lien.download = nom;
        lien.click();
        setTimeout(() => URL.revokeObjectURL(lien.href), 60_000);
      },
      error: (e) => {
        this.pdfEnCours.set(false);
        this.erreur.set(messageErreur(e));
      },
    });
  }

  envoyer(): void {
    const g = this.grille();
    if (!g?.planningId) return;
    const data: DonneesEnvoi = { planningId: g.planningId, titre: `${g.service} · ${this.titreMois()}`, type: 'stagiaires' };
    this.dialog.open(DialogueEnvoi, { data, width: '600px', maxWidth: '95vw' });
  }
}

function lireOnglet(): number {
  try {
    return localStorage.getItem(CLE_ONGLET) === '1' ? 1 : 0;
  } catch {
    return 0;
  }
}

/** Par défaut on prépare le mois suivant (sauf en début de mois). */
function prochainMoisParDefaut(): Date {
  const a = new Date();
  return a.getDate() <= 7 ? new Date(a.getFullYear(), a.getMonth(), 1) : new Date(a.getFullYear(), a.getMonth() + 1, 1);
}

function avecDe(mois: string): string {
  return /^[aeiouyéèAEIOUY]/.test(mois) ? `d'${mois}` : `de ${mois}`;
}
