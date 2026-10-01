import { Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { MENU_PRINCIPAL, MENU_SECONDAIRE } from '../../core/navigation';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, MatIconModule, MatTooltipModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  /** Mode tablette : seules les icônes sont affichées. */
  readonly reduite = input(false);

  protected readonly menuPrincipal = MENU_PRINCIPAL;
  protected readonly menuSecondaire = MENU_SECONDAIRE;
}
