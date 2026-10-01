import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UpperCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { ReferentielApi } from '../../core/referentiel.api';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/api';
import { Service } from '../../core/modeles';
import { formatFr } from '../../core/dates';
import { Case, ColonnePlage, ColonneSousService, Grille, Jour, PeriodeAstreinte, PlanningApi } from '../../core/planning.api';
import { DialogueAffectation, DonneesAffectation } from './dialogue-affectation';
import { DialogueEnvoi, DonneesEnvoi } from './dialogue-envoi';

const MOIS = [
  'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
  'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
];

@Component({
  selector: 'app-planning',
  imports: [
    FormsModule,
    UpperCasePipe,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './planning.html',
  styleUrl: './planning.scss',
})
export class Planning {
  private readonly referentiel = inject(ReferentielApi);
  private readonly api = inject(PlanningApi);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  readonly services = signal<Service[]>([]);
  readonly serviceId = signal<number | null>(null);
  readonly mois = signal(prochainMoisParDefaut());
  readonly grille = signal<Grille | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  readonly titreMois = computed(() => `${MOIS[this.mois().getMonth()]} ${this.mois().getFullYear()}`);
  /** « de septembre 2026 » / « d'octobre 2026 » */
  readonly deMois = computed(() => avecDe(this.titreMois().toLowerCase()));
  readonly deMoisTitre = computed(() => avecDe(this.titreMois()));
  readonly formatFr = formatFr;

  /** Accès rapide aux cases : « plageId|date » → case. */
  readonly cases = computed(() => {
    const m = new Map<string, Case>();
    for (const c of this.grille()?.cases ?? []) m.set(`${c.plageId}|${c.date}`, c);
    return m;
  });

  /** Astreinte qui commence ce jour-là (pour fusionner les cellules). */
  readonly astreintesParDebut = computed(() => {
    const m = new Map<string, { periode: PeriodeAstreinte; jours: number }>();
    for (const p of this.grille()?.astreintes ?? []) {
      const jours = (Date.parse(p.dateFin) - Date.parse(p.dateDebut)) / 86_400_000 + 1;
      m.set(p.dateDebut, { periode: p, jours });
    }
    return m;
  });

  readonly couverture = computed(() => {
    const s = this.grille()?.statistiques;
    return s && s.placesTotales ? Math.round((100 * s.placesRemplies) / s.placesTotales) : 0;
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
    this.envoi.set(true);
    this.api.creer(id, this.mois().getFullYear(), this.mois().getMonth() + 1).subscribe({
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

  publier(publie: boolean): void {
    const g = this.grille();
    if (!g?.planningId) return;
    this.envoi.set(true);
    this.api.changerStatut(g.planningId, publie ? 'PUBLIE' : 'BROUILLON').subscribe({
      next: (n) => {
        this.grille.set(n);
        this.envoi.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }

  // ---------- PDF et envoi ----------

  readonly pdfEnCours = signal(false);

  /** Ouvre le PDF dans un nouvel onglet (d'où l'on peut l'imprimer ou le télécharger). */
  ouvrirPdf(): void {
    const g = this.grille();
    if (!g?.planningId) return;
    // L'onglet est ouvert tout de suite (sinon le navigateur le bloque), puis rempli avec le PDF.
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

  /** Télécharge le PDF avec son nom de fichier. */
  telechargerPdf(): void {
    const g = this.grille();
    if (!g?.planningId) return;
    this.pdfEnCours.set(true);
    this.api.pdf(g.planningId).subscribe({
      next: (reponse) => {
        this.pdfEnCours.set(false);
        const nom =
          /filename="?([^";]+)"?/.exec(reponse.headers.get('Content-Disposition') ?? '')?.[1] ?? 'planning-gardes.pdf';
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
    const data: DonneesEnvoi = { planningId: g.planningId, titre: `${g.service} · ${this.titreMois()}` };
    this.dialog.open(DialogueEnvoi, { data, width: '600px', maxWidth: '95vw' });
  }

  // ---------- Cases ----------

  caseDe(p: ColonnePlage, j: Jour): Case | undefined {
    return this.cases().get(`${p.plageId}|${j.date}`);
  }

  places(p: ColonnePlage, j: Jour): number[] {
    const mode = this.caseDe(p, j)?.mode ?? p.modeParDefaut;
    return mode === 'BINOME' ? [1, 2] : [1];
  }

  occupant(p: ColonnePlage, j: Jour, position: number): string | null {
    return this.caseDe(p, j)?.agents.find((a) => a.position === position)?.nomAffichage ?? null;
  }

  ouvrirGarde(ss: ColonneSousService, p: ColonnePlage, j: Jour, position: number): void {
    const g = this.grille();
    if (!g?.planningId) return;
    const mode = this.caseDe(p, j)?.mode ?? p.modeParDefaut;
    this.ouvrir({
      planningId: g.planningId,
      titre: `${ss.nom} · ${p.libelle}`,
      sousTitre: `${j.libelle} ${this.titreMois()}${mode === 'BINOME' ? ` · place ${position}/2` : ''}`,
      garde: { plageId: p.plageId, date: j.date, position, mode, places: mode === 'BINOME' ? 2 : 1 },
      occupant: this.occupant(p, j, position),
    });
  }

  ouvrirAstreinte(periode: PeriodeAstreinte): void {
    const g = this.grille();
    if (!g?.planningId) return;
    this.ouvrir({
      planningId: g.planningId,
      titre: 'Astreinte',
      sousTitre: `Du ${formatFr(periode.dateDebut)} au ${formatFr(periode.dateFin)}`,
      astreinte: { dateDebut: periode.dateDebut },
      occupant: periode.nomAffichage,
    });
  }

  private ouvrir(data: DonneesAffectation): void {
    this.dialog
      .open(DialogueAffectation, { data, width: '520px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((g?: Grille) => {
        if (g) this.grille.set(g);
      });
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
