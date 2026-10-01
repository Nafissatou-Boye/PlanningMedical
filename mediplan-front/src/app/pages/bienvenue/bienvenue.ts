import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/api';

type Etape = 'question' | 'formulaire';

/** Les deux mots de passe saisis doivent être identiques. */
function motsDePasseIdentiques(groupe: AbstractControl): ValidationErrors | null {
  const mdp = groupe.get('motDePasse')?.value;
  const confirmation = groupe.get('confirmation');
  if (confirmation && confirmation.value && mdp !== confirmation.value) {
    confirmation.setErrors({ ...(confirmation.errors ?? {}), differents: true });
  } else if (confirmation?.hasError('differents')) {
    const { differents, ...autres } = confirmation.errors ?? {};
    confirmation.setErrors(Object.keys(autres).length ? autres : null);
  }
  return null;
}

@Component({
  selector: 'app-bienvenue',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './bienvenue.html',
  styleUrl: './bienvenue.scss',
})
export class Bienvenue {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly etape = signal<Etape>('question');
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly masquerMdp = signal(true);

  readonly postes = [
    'Chef de service',
    'Chef de service adjoint',
    'Surveillant(e) de service',
    'Major',
    'Coordonnateur(trice)',
    'Autre',
  ];

  readonly formulaire = this.fb.nonNullable.group(
    {
      nom: ['', [Validators.required, Validators.maxLength(80)]],
      prenom: ['', [Validators.required, Validators.maxLength(80)]],
      nomUtilisateur: [
        '',
        [
          Validators.required,
          Validators.minLength(3),
          Validators.maxLength(30),
          // Lettres (accents compris), chiffres, espace, point, tiret, apostrophe. Ex. : « Dr Nafisa »
          Validators.pattern(/^[\p{L}\p{N}][\p{L}\p{N} ._'-]*[\p{L}\p{N}.]$/u),
        ],
      ],
      service: ['', [Validators.required, Validators.maxLength(150)]],
      poste: ['', Validators.required],
      motDePasse: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmation: ['', Validators.required],
    },
    { validators: motsDePasseIdentiques },
  );

  valider(): void {
    if (this.formulaire.invalid || this.envoi()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const { confirmation, ...valeurs } = this.formulaire.getRawValue();
    // « Dr   Nafisa » → « Dr Nafisa » (espaces multiples réduits)
    const donnees = { ...valeurs, nomUtilisateur: valeurs.nomUtilisateur.trim().replace(/\s+/g, ' ') };
    this.envoi.set(true);
    this.erreur.set(null);
    this.auth.installer(donnees).subscribe({
      next: () => this.router.navigate(['/tableau-de-bord']),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }

  dejaInscrit(): void {
    this.router.navigate(['/connexion']);
  }
}
