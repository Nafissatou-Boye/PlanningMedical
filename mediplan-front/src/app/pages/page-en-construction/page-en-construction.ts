import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { map } from 'rxjs';

/** Page provisoire affichée pour les modules pas encore développés. */
@Component({
  selector: 'app-page-en-construction',
  imports: [MatIconModule],
  template: `
    <h1>{{ titre() }}</h1>
    <section class="mp-carte vide">
      <mat-icon>construction</mat-icon>
      <p>Ce module sera développé dans un prochain lot.</p>
    </section>
  `,
  styles: `
    h1 { margin: 0 0 20px; font-size: 26px; font-weight: 700; }
    .vide {
      display: flex; flex-direction: column; align-items: center; gap: 8px;
      padding: 56px 24px; color: var(--mp-texte-secondaire); text-align: center;
    }
    mat-icon { font-size: 40px; width: 40px; height: 40px; color: var(--mp-turquoise); }
  `,
})
export class PageEnConstruction {
  readonly titre = toSignal(inject(ActivatedRoute).title.pipe(map((t) => t?.replace(' | MediPlan', '') ?? '')), {
    initialValue: '',
  });
}
