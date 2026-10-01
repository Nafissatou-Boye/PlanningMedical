import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface DonneesConfirmation {
  titre: string;
  message: string;
  action: string;
  danger?: boolean;
}

/** Boîte « Êtes-vous sûr ? ». Se ferme avec true si l'utilisateur confirme. */
@Component({
  selector: 'app-confirmation',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ d.titre }}</h2>
    <mat-dialog-content>{{ d.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Annuler</button>
      <button mat-flat-button [class.mp-bouton-danger]="d.danger" [mat-dialog-close]="true">{{ d.action }}</button>
    </mat-dialog-actions>
  `,
})
export class Confirmation {
  readonly d = inject<DonneesConfirmation>(MAT_DIALOG_DATA);
}
