import { AfterViewInit, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroupDirective, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';

import { FeriesApi, JourFerie } from '../../core/feries.api';
import { messageErreur } from '../../core/api';
import { depuisIso, versIso } from '../../core/dates';
import { Confirmation, DonneesConfirmation } from '../../shared/confirmation';
import { SignaturesPdf } from './signatures-pdf';
import { AccesTelephone } from './acces-telephone';
import { EnvoiEmail } from './envoi-email';

/** Fêtes dont la date dépend de la lune : saisies quand elles sont annoncées. */
const FETES_LUNAIRES = ['Korité', 'Tabaski', 'Tamkharit', 'Maouloud', 'Grand Magal de Touba'];
const JOURS = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];

@Component({
  selector: 'app-parametres',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatProgressBarModule,
    MatTooltipModule,
    SignaturesPdf,
    AccesTelephone,
    EnvoiEmail,
  ],
  templateUrl: './parametres.html',
  styleUrl: './parametres.scss',
})
export class Parametres implements AfterViewInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(FeriesApi);
  private readonly dialog = inject(MatDialog);

  readonly annee = signal(new Date().getMonth() >= 9 ? new Date().getFullYear() + 1 : new Date().getFullYear());
  readonly feries = signal<JourFerie[]>([]);
  readonly chargement = signal(true);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  /** Jour férié en cours de modification (null : ajout). */
  readonly edition = signal<JourFerie | null>(null);

  readonly fetesLunaires = FETES_LUNAIRES;
  /** Fêtes lunaires pas encore saisies pour l'année affichée. */
  readonly lunairesManquantes = computed(() =>
    FETES_LUNAIRES.filter((f) => !this.feries().some((x) => normaliser(x.libelle).includes(normaliser(f)))),
  );

  readonly f = inject(FormBuilder).nonNullable.group({
    date: [null as Date | null, Validators.required],
    libelle: ['', [Validators.required, Validators.maxLength(100)]],
  });

  constructor() {
    this.charger();
  }

  /** Lien direct vers une rubrique (ex. /parametres#envoi-email depuis la fenêtre d'envoi). */
  ngAfterViewInit(): void {
    const ancre = this.route.snapshot.fragment;
    if (ancre) setTimeout(() => document.getElementById(ancre)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 400);
  }

  changerAnnee(delta: number): void {
    this.annee.set(this.annee() + delta);
    this.annuler();
    this.charger();
  }

  private charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.api.lister(this.annee()).subscribe({
      next: (l) => {
        this.feries.set(l);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  ajouterSenegal(): void {
    const avant = this.feries().length;
    this.envoi.set(true);
    this.erreur.set(null);
    this.api.ajouterSenegal(this.annee()).subscribe({
      next: (l) => {
        this.feries.set(l);
        this.envoi.set(false);
        const n = l.length - avant;
        this.message.set(n > 0 ? `${n} jour${n > 1 ? 's' : ''} férié${n > 1 ? 's' : ''} ajouté${n > 1 ? 's' : ''}.` : 'Ils étaient déjà tous dans la liste.');
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }

  preparer(libelle: string): void {
    this.edition.set(null);
    this.f.reset({ date: null, libelle });
    this.message.set(null);
  }

  modifier(j: JourFerie): void {
    this.edition.set(j);
    this.f.reset({ date: depuisIso(j.date), libelle: j.libelle });
    this.message.set(null);
  }

  /** Vide le formulaire (resetForm efface aussi l'état « soumis », sinon les champs vides s'affichent en rouge). */
  annuler(fd?: FormGroupDirective): void {
    this.edition.set(null);
    if (fd) fd.resetForm({ date: null, libelle: '' });
    else this.f.reset({ date: null, libelle: '' });
  }

  enregistrer(fd: FormGroupDirective): void {
    if (this.f.invalid || this.envoi()) {
      this.f.markAllAsTouched();
      return;
    }
    const d = { date: versIso(this.f.value.date!), libelle: this.f.value.libelle!.trim() };
    const e = this.edition();
    this.envoi.set(true);
    this.erreur.set(null);
    (e ? this.api.modifier(e.id, d) : this.api.creer(d)).subscribe({
      next: () => {
        this.envoi.set(false);
        this.annuler(fd);
        // L'année affichée suit la date saisie
        const a = Number(d.date.slice(0, 4));
        if (a !== this.annee()) this.annee.set(a);
        this.charger();
      },
      error: (err) => {
        this.erreur.set(messageErreur(err));
        this.envoi.set(false);
      },
    });
  }

  supprimer(j: JourFerie): void {
    const data: DonneesConfirmation = {
      titre: 'Supprimer ce jour férié ?',
      message: `${j.libelle} — ${this.dateLongue(j.date)}.`,
      action: 'Supprimer',
      danger: true,
    };
    this.dialog
      .open(Confirmation, { data, width: '440px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) return;
        this.api.supprimer(j.id).subscribe({
          next: () => this.feries.set(this.feries().filter((x) => x.id !== j.id)),
          error: (e) => this.erreur.set(messageErreur(e)),
        });
      });
  }

  /** « samedi 4 avril » */
  dateLongue(iso: string): string {
    const d = depuisIso(iso);
    return `${JOURS[d.getDay()]} ${d.getDate()} ${d.toLocaleDateString('fr-FR', { month: 'long' })}`;
  }

  estWeekend(iso: string): boolean {
    const j = depuisIso(iso).getDay();
    return j === 0 || j === 6;
  }
}

function normaliser(t: string): string {
  return t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
