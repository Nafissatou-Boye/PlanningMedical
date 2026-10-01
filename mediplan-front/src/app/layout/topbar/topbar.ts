import { Component, ElementRef, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { Location } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';

import { Notification, NotificationsCentre } from '../../core/notifications';
import { RechercheGlobale, Resultat } from '../../core/recherche';

@Component({
  selector: 'app-topbar',
  imports: [RouterLink, MatIconModule, MatButtonModule, MatMenuModule, MatBadgeModule, MatAutocompleteModule, MatTooltipModule],
  templateUrl: './topbar.html',
  styleUrl: './topbar.scss',
  host: { '(document:keydown)': 'raccourci($event)' },
})
export class Topbar {
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  /**
   * Bouton « Retour » : MediPlan s'ouvre dans une fenêtre sans barre de navigation,
   * il faut donc pouvoir revenir à la page précédente depuis l'application.
   * Angular numérote chaque page dans l'historique (ɵrouterPageId) : le bouton s'affiche
   * dès qu'on n'est plus sur la toute première page ouverte.
   */
  private premierId: number | null = null;
  readonly peutRevenir = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => {
        // ɵrouterPageId : rang de la page dans l'historique, conservé quand on revient en arrière
        const id = (this.location.getState() as { ɵrouterPageId?: number } | null)?.ɵrouterPageId ?? 0;
        this.premierId ??= id;
        return id > this.premierId;
      }),
    ),
    { initialValue: false },
  );

  retour(): void {
    this.location.back();
  }
  protected readonly notifications = inject(NotificationsCentre);
  private readonly recherche = inject(RechercheGlobale);

  /** Affichage mobile : logo + bouton menu, recherche repliée derrière une loupe. */
  readonly mobile = input(false);
  /** Demande d'ouverture / fermeture du menu latéral (mobile). */
  readonly menu = output<void>();
  /** L'utilisateur a cliqué sur « Se déconnecter ». */
  readonly deconnexion = output<void>();

  readonly nomUtilisateur = input('Administrateur');
  readonly fonction = input('Chef de service');

  readonly texte = signal('');
  readonly resultats = computed(() => (this.recherche.pret() ? this.recherche.chercher(this.texte()) : []));
  /** Mobile : champ de recherche déplié. */
  readonly rechercheOuverte = signal(false);
  private readonly champ = viewChild<ElementRef<HTMLInputElement>>('champ');

  readonly initiales = computed(() =>
    this.nomUtilisateur()
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((mot) => mot[0].toUpperCase())
      .join(''),
  );

  preparer(): void {
    this.recherche.preparer();
  }

  /** Ctrl+K (ou Cmd+K) : aller à la recherche. */
  raccourci(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      if (this.mobile()) this.rechercheOuverte.set(true);
      setTimeout(() => this.champ()?.nativeElement.focus());
    }
  }

  ouvrirRechercheMobile(): void {
    this.rechercheOuverte.set(true);
    setTimeout(() => this.champ()?.nativeElement.focus());
  }

  choisir(e: MatAutocompleteSelectedEvent): void {
    const r = e.option.value as Resultat;
    this.texte.set('');
    this.rechercheOuverte.set(false);
    this.champ()?.nativeElement.blur();
    this.router.navigate([r.route], { queryParams: r.q ? { q: r.q } : {} });
  }

  afficher = (r: Resultat | string | null): string => (typeof r === 'string' ? r : '');

  // ---------- Notifications ----------

  ouvrirNotifications(): void {
    this.notifications.charger(true);
  }

  suivre(n: Notification): void {
    this.notifications.marquerLue(n);
    this.router.navigateByUrl(n.lien);
  }
}
