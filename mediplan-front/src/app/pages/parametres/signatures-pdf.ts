import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';

import { API_URL, messageErreur } from '../../core/api';
import { AuthService } from '../../core/auth.service';
import { ReferentielApi } from '../../core/referentiel.api';
import { Service } from '../../core/modeles';

interface Signataire {
  fonction: string;
  nom: string | null;
}

/** Réglage des signatures en bas du PDF des gardes, service par service. */
@Component({
  selector: 'app-signatures-pdf',
  imports: [FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTooltipModule],
  template: `
    <section class="mp-carte carte">
      <div class="titre">
        <div>
          <h2><mat-icon>draw</mat-icon> Signatures du PDF des gardes</h2>
          <p>De gauche à droite, en bas du planning. Le nom est facultatif.</p>
        </div>
        @if (services().length > 1) {
          <mat-form-field appearance="outline" class="service">
            <mat-select [ngModel]="serviceId()" (ngModelChange)="choisir($event)" aria-label="Service">
              @for (s of services(); track s.id) {
                <mat-option [value]="s.id">{{ s.nom }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
      </div>

      @if (erreur()) {
        <p class="mp-erreur" role="alert"><mat-icon>error</mat-icon> {{ erreur() }}</p>
      }
      @if (message()) {
        <p class="info"><mat-icon>check_circle</mat-icon> {{ message() }}</p>
      }
      @if (parDefaut()) {
        <p class="defaut">Signatures par défaut. Modifiez-les puis enregistrez pour les adapter à ce service.</p>
      }

      <ol class="liste">
        @for (s of liste(); track $index; let i = $index; let dernier = $last) {
          <li>
            <span class="numero">{{ i + 1 }}</span>
            <mat-form-field appearance="outline" class="fonction">
              <mat-label>Fonction</mat-label>
              <input matInput [ngModel]="s.fonction" (ngModelChange)="modifier(i, 'fonction', $event)" maxlength="100" placeholder="Ex. : Le Chef de Service" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="nom">
              <mat-label>Nom (facultatif)</mat-label>
              <input matInput [ngModel]="s.nom ?? ''" (ngModelChange)="modifier(i, 'nom', $event)" maxlength="100" placeholder="Ex. : Dr Beyti Sylla" />
            </mat-form-field>
            <span class="boutons">
              <button mat-icon-button (click)="deplacer(i, -1)" [disabled]="i === 0" aria-label="Vers la gauche" matTooltip="Monter : plus à gauche dans le PDF"><mat-icon>arrow_upward</mat-icon></button>
              <button mat-icon-button (click)="deplacer(i, 1)" [disabled]="dernier" aria-label="Vers la droite" matTooltip="Descendre : plus à droite dans le PDF"><mat-icon>arrow_downward</mat-icon></button>
              <button mat-icon-button (click)="retirer(i)" aria-label="Retirer" matTooltip="Retirer"><mat-icon>delete</mat-icon></button>
            </span>
          </li>
        }
      </ol>

      @if (liste().length < 4) {
        <button mat-button (click)="ajouter()"><mat-icon>add</mat-icon> Ajouter une signature</button>
      }

      <div class="apercu" aria-label="Aperçu du bas du PDF">
        @for (s of liste(); track $index) {
          <span>
            <u>{{ s.fonction || '…' }}</u>
            @if (s.nom) {
              <small>{{ s.nom }}</small>
            }
          </span>
        } @empty {
          <em>Aucune signature</em>
        }
      </div>

      <div class="actions">
        @if (!parDefaut()) {
          <button mat-button (click)="retablir()" [disabled]="envoi()">Rétablir les signatures par défaut</button>
        }
        <button mat-flat-button class="mp-bouton-principal" (click)="enregistrer()" [disabled]="envoi() || invalide()">
          <mat-icon>save</mat-icon> Enregistrer
        </button>
      </div>
    </section>
  `,
  styles: `
    .carte { padding: 24px; max-width: 860px; margin-top: 24px; }
    .titre { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
    h2 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 18px; }
    .titre p { margin: 4px 0 0; color: var(--mp-texte-secondaire); }
    .service { width: 240px; }
    .info { display: flex; align-items: center; gap: 6px; color: var(--mp-vert-texte); }
    .defaut { margin: 14px 0 0; font-size: 13px; color: var(--mp-texte-secondaire); }
    .liste { list-style: none; margin: 16px 0 0; padding: 0; }
    li { display: flex; align-items: flex-start; gap: 10px; flex-wrap: wrap; }
    .numero { display: grid; place-items: center; width: 28px; height: 28px; margin-top: 14px; border-radius: 8px; background: var(--mp-bleu-ciel); font-weight: 700; font-size: 13px; }
    .fonction { flex: 1 1 220px; }
    .nom { flex: 1 1 200px; }
    .boutons { display: flex; margin-top: 4px; }
    .apercu { display: flex; justify-content: space-around; gap: 12px; margin: 16px 0; padding: 18px 12px 26px; border: 1px dashed var(--mp-bordure); border-radius: var(--mp-rayon-petit); background: var(--mp-fond); font-family: Helvetica, Arial, sans-serif; }
    .apercu span { display: flex; flex-direction: column; align-items: center; gap: 22px; font-weight: 700; font-size: 13px; }
    .apercu small { font-size: 12px; }
    .apercu em { color: var(--mp-texte-secondaire); }
    .actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
  `,
})
export class SignaturesPdf {
  private readonly http = inject(HttpClient);
  private readonly referentiel = inject(ReferentielApi);
  private readonly auth = inject(AuthService);

  readonly services = signal<Service[]>([]);
  readonly serviceId = signal<number | null>(null);
  readonly liste = signal<Signataire[]>([]);
  readonly parDefaut = signal(true);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly invalide = computed(() => this.liste().some((s) => !s.fonction.trim()));

  constructor() {
    this.referentiel.services().subscribe({
      next: (l) => {
        const actifs = l.filter((s) => s.actif);
        this.services.set(actifs);
        const s = actifs.find((x) => x.nom === this.auth.profil()?.service) ?? actifs[0];
        if (s) this.choisir(s.id);
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private url(): string {
    return `${API_URL}/services/${this.serviceId()}/signataires`;
  }

  choisir(id: number): void {
    this.serviceId.set(id);
    this.message.set(null);
    this.http.get<{ parDefaut: boolean; signataires: Signataire[] }>(this.url()).subscribe({
      next: (r) => this.appliquer(r),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private appliquer(r: { parDefaut: boolean; signataires: Signataire[] }): void {
    this.parDefaut.set(r.parDefaut);
    this.liste.set(r.signataires.map((s) => ({ ...s })));
  }

  modifier(i: number, champ: 'fonction' | 'nom', valeur: string): void {
    const l = [...this.liste()];
    l[i] = { ...l[i], [champ]: valeur };
    this.liste.set(l);
    this.message.set(null);
  }

  deplacer(i: number, sens: number): void {
    const l = [...this.liste()];
    [l[i], l[i + sens]] = [l[i + sens], l[i]];
    this.liste.set(l);
  }

  retirer(i: number): void {
    this.liste.set(this.liste().filter((_, j) => j !== i));
  }

  ajouter(): void {
    this.liste.set([...this.liste(), { fonction: '', nom: null }]);
  }

  retablir(): void {
    this.envoyer([], 'Signatures par défaut rétablies.');
  }

  enregistrer(): void {
    if (this.liste().length === 0) {
      this.erreur.set('Gardez au moins une signature, ou rétablissez celles par défaut.');
      return;
    }
    this.envoyer(this.liste(), 'Signatures enregistrées : elles apparaîtront dans le prochain PDF.');
  }

  private envoyer(liste: Signataire[], message: string): void {
    this.envoi.set(true);
    this.erreur.set(null);
    this.http.put<{ parDefaut: boolean; signataires: Signataire[] }>(this.url(), liste).subscribe({
      next: (r) => {
        this.appliquer(r);
        this.envoi.set(false);
        this.message.set(message);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}
