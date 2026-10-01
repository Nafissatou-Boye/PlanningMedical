import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/api';

@Component({
  selector: 'app-connexion',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './connexion.html',
  // Même présentation que la page Bienvenue
  styleUrl: '../bienvenue/bienvenue.scss',
})
export class Connexion {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly masquerMdp = signal(true);

  readonly formulaire = this.fb.nonNullable.group({
    nomUtilisateur: ['', Validators.required],
    motDePasse: ['', Validators.required],
  });

  valider(): void {
    if (this.formulaire.invalid || this.envoi()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const { nomUtilisateur, motDePasse } = this.formulaire.getRawValue();
    this.envoi.set(true);
    this.erreur.set(null);
    this.auth.connecter(nomUtilisateur.trim().replace(/\s+/g, ' '), motDePasse).subscribe({
      next: () => this.router.navigate(['/tableau-de-bord']),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}
