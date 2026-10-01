import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { filter, map } from 'rxjs';

import { Sidebar } from '../sidebar/sidebar';
import { Topbar } from '../topbar/topbar';
import { AuthService } from '../../core/auth.service';
import { NotificationsCentre } from '../../core/notifications';

/** Points de rupture du responsive (cf. cahier des charges, section Responsive). */
const MOBILE = '(max-width: 767.98px)';
const TABLETTE = '(min-width: 768px) and (max-width: 1199.98px)';

/**
 * Layout principal : barre latérale fixe + en-tête fixe + zone de contenu.
 * - Ordinateur (≥ 1200 px) : barre latérale complète.
 * - Tablette (768-1199 px) : barre latérale réduite aux icônes.
 * - Mobile (< 768 px) : barre latérale masquée, ouverte en tiroir par le bouton menu.
 */
@Component({
  selector: 'app-main-layout',
  imports: [RouterOutlet, MatSidenavModule, Sidebar, Topbar],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationsCentre);

  private readonly etat = toSignal(
    this.breakpoints.observe([MOBILE, TABLETTE]).pipe(
      map((r) => ({ mobile: r.breakpoints[MOBILE], tablette: r.breakpoints[TABLETTE] })),
    ),
    { initialValue: { mobile: false, tablette: false } },
  );

  readonly estMobile = computed(() => this.etat().mobile);
  readonly estReduite = computed(() => this.etat().tablette);
  readonly tiroirOuvert = signal(false);

  constructor() {
    // Sur mobile, on referme le tiroir après chaque navigation.
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        this.tiroirOuvert.set(false);
        this.notifications.charger(); // au plus toutes les 30 s
      });
    this.notifications.charger(true);

    // En repassant sur grand écran, le tiroir mobile ne doit pas rester « ouvert ».
    effect(() => {
      if (!this.estMobile()) this.tiroirOuvert.set(false);
    });
  }

  deconnecter(): void {
    this.auth.deconnecter();
    this.router.navigate(['/connexion']);
  }

  basculerMenu(): void {
    this.tiroirOuvert.update((ouvert) => !ouvert);
  }
}
