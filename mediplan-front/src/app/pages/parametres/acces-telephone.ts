import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import QRCode from 'qrcode';

import { API_URL } from '../../core/api';

/** « Ouvrir MediPlan sur le téléphone » : adresse sur le réseau Wi-Fi + QR code à scanner. */
@Component({
  selector: 'app-acces-telephone',
  imports: [MatIconModule, MatButtonModule, MatTooltipModule],
  template: `
    <section class="mp-carte carte">
      <h2><mat-icon>smartphone</mat-icon> Ouvrir MediPlan sur le téléphone</h2>
      <p class="sous-titre">Le téléphone doit être connecté au <strong>même Wi-Fi</strong> que cet ordinateur, et MediPlan doit être démarré ici.</p>

      @if (chargement()) {
        <p class="sous-titre">Recherche de l'adresse…</p>
      } @else if (adresses().length === 0) {
        <p class="alerte"><mat-icon>wifi_off</mat-icon> Cet ordinateur n'est connecté à aucun réseau Wi-Fi ou câblé pour le moment.</p>
      } @else {
        <div class="acces">
          @if (qr()) {
            <img [src]="qr()" width="176" height="176" alt="QR code de l'adresse de MediPlan" />
          }
          <div class="etapes">
            <p><strong>1.</strong> Scannez ce code avec l'appareil photo du téléphone, ou tapez l'adresse :</p>
            <p class="adresse">
              <code>{{ adresse() }}</code>
              <button mat-icon-button (click)="copier()" [matTooltip]="copie() ? 'Copiée' : 'Copier'" aria-label="Copier l'adresse">
                <mat-icon>{{ copie() ? 'check' : 'content_copy' }}</mat-icon>
              </button>
            </p>
            @if (adresses().length > 1) {
              <p class="autres">
                Autres adresses :
                @for (a of adresses(); track a) {
                  @if (a !== adresse()) {
                    <button type="button" class="lien" (click)="choisir(a)">{{ a }}</button>
                  }
                }
              </p>
            }
            <p><strong>2.</strong> Connectez-vous avec le même nom d'utilisateur et le même mot de passe.</p>
            <p><strong>3.</strong> Dans le menu du navigateur, « <em>Ajouter à l'écran d'accueil</em> » crée une icône MediPlan sur le téléphone.</p>
            <p class="note">
              <mat-icon>info</mat-icon>
              <span>La première fois, Windows peut demander d'<strong>autoriser l'accès au réseau</strong> pour Java : choisissez « Réseaux privés » puis « Autoriser ».
              L'adresse peut changer d'un Wi-Fi à l'autre : revenez sur cette page pour la retrouver.</span>
            </p>
          </div>
        </div>
      }
    </section>
  `,
  styles: `
    .carte { padding: 24px; max-width: 860px; margin-top: 24px; }
    h2 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 18px; }
    .sous-titre { margin: 4px 0 0; color: var(--mp-texte-secondaire); }
    .alerte { display: flex; align-items: center; gap: 8px; color: #b26a00; }
    .acces { display: flex; gap: 24px; align-items: flex-start; flex-wrap: wrap; margin-top: 16px; }
    img { flex: none; padding: 8px; border: 1px solid var(--mp-bordure); border-radius: var(--mp-rayon-petit); background: #fff; }
    .etapes { flex: 1; min-width: 240px; }
    .etapes p { margin: 0 0 10px; }
    .adresse { display: flex; align-items: center; gap: 4px; }
    .adresse code { padding: 6px 10px; border-radius: 8px; background: var(--mp-fond); font-size: 16px; font-weight: 600; color: var(--mp-bleu-medical); }
    .autres { font-size: 12px; color: var(--mp-texte-secondaire); }
    .lien { margin-left: 6px; border: none; background: none; font: inherit; color: var(--mp-bleu-medical); text-decoration: underline; cursor: pointer; }
    .note { display: flex; gap: 8px; padding: 10px 12px; border-radius: var(--mp-rayon-petit); background: var(--mp-fond); font-size: 13px; color: var(--mp-texte-secondaire); }
    .note mat-icon { flex: none; font-size: 18px; width: 18px; height: 18px; color: var(--mp-bleu-medical); }
  `,
})
export class AccesTelephone {
  private readonly http = inject(HttpClient);

  readonly adresses = signal<string[]>([]);
  readonly adresse = signal('');
  readonly qr = signal<string | null>(null);
  readonly chargement = signal(true);
  readonly copie = signal(false);

  constructor() {
    this.http.get<{ port: number; adresses: string[] }>(`${API_URL}/configuration/acces`).subscribe({
      next: (r) => {
        this.adresses.set(r.adresses);
        // Priorité au réseau domestique classique (192.168.x.x)
        const a = r.adresses.find((x) => x.includes('//192.168.')) ?? r.adresses[0];
        if (a) this.choisir(a);
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false),
    });
  }

  choisir(a: string): void {
    this.adresse.set(a);
    this.copie.set(false);
    QRCode.toDataURL(a, { width: 352, margin: 1, color: { dark: '#173B5E', light: '#FFFFFF' } }).then((url) => this.qr.set(url));
  }

  copier(): void {
    navigator.clipboard?.writeText(this.adresse()).then(() => this.copie.set(true));
  }
}
