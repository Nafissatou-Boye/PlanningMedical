import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { API_URL, messageErreur } from '../../core/api';

interface Configuration {
  configure: boolean;
  expediteur: string | null;
}

/** Réglage du compte Gmail qui envoie les plannings (vérifié auprès de Gmail avant d'être enregistré). */
@Component({
  selector: 'app-envoi-email',
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  template: `
    <section class="mp-carte carte" id="envoi-email">
      <h2><mat-icon>forward_to_inbox</mat-icon> Envoi des e-mails</h2>
      <p class="sous-titre">Le compte Gmail qui envoie les plannings au personnel.</p>

      @if (config(); as c) {
        @if (c.configure && !modification()) {
          <div class="etat ok">
            <mat-icon>check_circle</mat-icon>
            <span>Les plannings partent de <strong>{{ c.expediteur }}</strong>.</span>
            <button mat-button (click)="modification.set(true)">Changer de compte</button>
          </div>
        } @else {
          @if (!c.configure) {
            <div class="etat attente"><mat-icon>info</mat-icon><span>Aucun compte n'est encore configuré : les plannings ne peuvent pas être envoyés.</span></div>
          }
          <form [formGroup]="f" (ngSubmit)="enregistrer()" class="formulaire">
            <mat-form-field appearance="outline">
              <mat-label>Adresse Gmail</mat-label>
              <input matInput type="email" formControlName="adresse" autocomplete="off" placeholder="service.anesthesie@gmail.com" />
              <mat-error>Indiquez une adresse e-mail valide.</mat-error>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Mot de passe d'application</mat-label>
              <input matInput [type]="visible() ? 'text' : 'password'" formControlName="motDePasse" autocomplete="new-password" placeholder="16 lettres" />
              <button mat-icon-button matSuffix type="button" (click)="visible.set(!visible())" [attr.aria-label]="visible() ? 'Masquer' : 'Afficher'">
                <mat-icon>{{ visible() ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              <mat-hint>Pas le mot de passe du compte Gmail : voir ci-dessous.</mat-hint>
              <mat-error>Indiquez le mot de passe d'application.</mat-error>
            </mat-form-field>
            <div class="actions">
              @if (c.configure) {
                <button mat-button type="button" (click)="modification.set(false)" [disabled]="envoi()">Annuler</button>
              }
              <button mat-flat-button class="mp-bouton-principal" type="submit" [disabled]="envoi()">
                <mat-icon>verified</mat-icon> {{ envoi() ? 'Vérification auprès de Gmail…' : 'Vérifier et enregistrer' }}
              </button>
            </div>
          </form>
          @if (envoi()) {
            <mat-progress-bar mode="indeterminate" />
          }
          @if (erreur()) {
            <p class="mp-erreur" role="alert"><mat-icon>error</mat-icon> {{ erreur() }}</p>
          }
          <div class="aide">
            <strong>Obtenir un mot de passe d'application Gmail (une seule fois) :</strong>
            <ol>
              <li>Connectez-vous au compte Google sur <em>myaccount.google.com</em>, onglet <strong>Sécurité</strong>.</li>
              <li>Activez la <strong>validation en deux étapes</strong> si ce n'est pas déjà fait.</li>
              <li>Cherchez <strong>« Mots de passe des applications »</strong>, créez-en un nommé « MediPlan ».</li>
              <li>Copiez les 16 lettres affichées et collez-les ici. Ne les communiquez à personne.</li>
            </ol>
          </div>
        }
      }
      @if (message()) {
        <p class="succes"><mat-icon>mark_email_read</mat-icon> {{ message() }}</p>
      }
    </section>
  `,
  styles: `
    .carte { padding: 24px; max-width: 860px; margin-top: 24px; }
    h2 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 18px; }
    .sous-titre { margin: 4px 0 14px; color: var(--mp-texte-secondaire); }
    .etat { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-radius: var(--mp-rayon-petit); margin-bottom: 14px; flex-wrap: wrap; }
    .etat span { flex: 1; }
    .etat.ok { background: var(--mp-vert-fond); color: var(--mp-vert-texte); }
    .etat.attente { background: #fdf0dd; color: #8a5300; }
    .formulaire { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 14px; }
    .actions { grid-column: 1 / -1; display: flex; justify-content: flex-end; gap: 8px; margin-top: 6px; }
    .aide { margin-top: 14px; padding: 12px 14px; border-radius: var(--mp-rayon-petit); background: var(--mp-fond); font-size: 13px; }
    .aide ol { margin: 6px 0 0; padding-left: 20px; }
    .aide li { margin-bottom: 3px; }
    .succes { display: flex; align-items: center; gap: 8px; margin: 12px 0 0; color: var(--mp-vert-texte); }
    @media (max-width: 767.98px) { .formulaire { grid-template-columns: 1fr; } }
  `,
})
export class EnvoiEmail {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/configuration/email`;

  readonly config = signal<Configuration | null>(null);
  readonly modification = signal(false);
  readonly visible = signal(false);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly message = signal<string | null>(null);

  readonly f = inject(FormBuilder).nonNullable.group({
    adresse: ['', [Validators.required, Validators.email]],
    motDePasse: ['', Validators.required],
  });

  constructor() {
    this.http.get<Configuration>(this.url).subscribe({
      next: (c) => this.config.set(c),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  enregistrer(): void {
    if (this.f.invalid || this.envoi()) {
      this.f.markAllAsTouched();
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.message.set(null);
    this.http.post<Configuration>(this.url, this.f.getRawValue()).subscribe({
      next: (c) => {
        const adresse = this.f.getRawValue().adresse;
        this.config.set(c);
        this.envoi.set(false);
        this.modification.set(false);
        this.f.reset();
        this.message.set(`C'est prêt : un e-mail de test vient d'être envoyé à ${adresse}. Les plannings peuvent maintenant être envoyés.`);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}
