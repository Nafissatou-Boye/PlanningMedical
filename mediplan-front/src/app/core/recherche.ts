import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { ReferentielApi } from './referentiel.api';
import { MENU_PRINCIPAL, MENU_SECONDAIRE } from './navigation';
import { QUESTIONS } from '../pages/aide/contenu-aide';

export type TypeResultat = 'Page' | 'Personnel' | 'Stagiaire' | 'Service' | 'Aide';

export interface Resultat {
  type: TypeResultat;
  icone: string;
  titre: string;
  sousTitre: string;
  route: string;
  q?: string;
  /** Texte sans accents dans lequel on cherche. */
  cle: string;
}

/** Recherche globale de la barre du haut : pages, membres, services et questions d'aide. */
@Injectable({ providedIn: 'root' })
export class RechercheGlobale {
  private readonly api = inject(ReferentielApi);
  private index: Resultat[] = [];
  private charge = false;
  readonly pret = signal(false);

  /** Charge (ou recharge) les membres et les services. */
  preparer(forcer = false): void {
    if (this.charge && !forcer) return;
    this.charge = true;
    const pages: Resultat[] = [...MENU_PRINCIPAL, ...MENU_SECONDAIRE].map((m) => ({
      type: 'Page',
      icone: m.icone,
      titre: m.libelle,
      sousTitre: 'Aller à la page',
      route: m.lien,
      cle: normaliser(m.libelle),
    }));
    const aide: Resultat[] = QUESTIONS.map((q) => ({
      type: 'Aide',
      icone: 'help',
      titre: q.question,
      sousTitre: "Centre d'aide · " + q.theme,
      route: '/aide',
      q: q.question,
      cle: normaliser(q.question),
    }));
    this.index = [...pages, ...aide];
    this.pret.set(true);
    forkJoin({ agents: this.api.agents(), services: this.api.services() }).subscribe({
      next: ({ agents, services }) => {
        const membres: Resultat[] = agents.map((a) => {
          const stagiaire = a.categorie === 'STAGIAIRE';
          return {
            type: stagiaire ? 'Stagiaire' : 'Personnel',
            icone: stagiaire ? 'school' : 'person',
            titre: a.nomAffichage,
            sousTitre: [a.profession, a.services.map((s) => s.libelle).join(', '), a.actif ? '' : 'inactif'].filter(Boolean).join(' · '),
            route: stagiaire ? '/stagiaires' : '/personnel',
            q: a.nomAffichage,
            cle: normaliser(`${a.nomAffichage} ${a.prenom} ${a.nom} ${a.matricule ?? ''} ${a.email ?? ''}`),
          };
        });
        const srv: Resultat[] = services.flatMap((s) => [
          { type: 'Service' as const, icone: 'medical_services', titre: s.nom, sousTitre: 'Service', route: '/services', cle: normaliser(s.nom) },
          ...s.sousServices.map((ss) => ({
            type: 'Service' as const,
            icone: 'view_column',
            titre: ss.nom,
            sousTitre: `Sous-service de ${s.nom}`,
            route: '/services',
            cle: normaliser(`${ss.nom} ${s.nom}`),
          })),
        ]);
        this.index = [...pages, ...membres, ...srv, ...aide];
      },
      error: () => (this.charge = false),
    });
  }

  chercher(texte: string): Resultat[] {
    const t = normaliser(texte);
    if (t.length < 2) return [];
    const mots = t.split(/\s+/);
    const ordre: TypeResultat[] = ['Page', 'Personnel', 'Stagiaire', 'Service', 'Aide'];
    return this.index
      .filter((r) => mots.every((m) => r.cle.includes(m)))
      .sort((a, b) => ordre.indexOf(a.type) - ordre.indexOf(b.type) || Number(!a.cle.startsWith(mots[0])) - Number(!b.cle.startsWith(mots[0])))
      .slice(0, 10);
  }
}

export function normaliser(t: string): string {
  return t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
