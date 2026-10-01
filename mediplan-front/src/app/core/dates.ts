import { Injectable, Provider } from '@angular/core';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE, MatDateFormats, NativeDateAdapter } from '@angular/material/core';

import { DateIso } from './modeles';

/** Date locale → « AAAA-MM-JJ » (sans décalage de fuseau). */
export function versIso(date: Date): DateIso {
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const j = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${m}-${j}`;
}

/** « AAAA-MM-JJ » → Date locale à minuit. */
export function depuisIso(iso: DateIso): Date {
  const [a, m, j] = iso.split('-').map(Number);
  return new Date(a, m - 1, j);
}

/** « AAAA-MM-JJ » → « JJ/MM/AAAA ». */
export function formatFr(iso: DateIso | null | undefined): string {
  if (!iso) return '';
  const [a, m, j] = iso.split('-');
  return `${j}/${m}/${a}`;
}

/** Heure « 08:00:00 » → « 08H00 ». */
export function heureCourte(heure: string): string {
  const [h, min] = heure.split(':');
  return min === '00' ? `${h}H` : `${h}H${min}`;
}

/** Permet de taper « 15/09/2026 » dans les champs date (l'adaptateur natif attend le format américain). */
@Injectable()
export class AdaptateurDateFr extends NativeDateAdapter {
  override parse(valeur: unknown): Date | null {
    if (typeof valeur === 'string') {
      const m = valeur.trim().match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/);
      if (m) {
        const date = new Date(Number(m[3]), Number(m[2]) - 1, Number(m[1]));
        return date.getDate() === Number(m[1]) ? date : this.invalid();
      }
    }
    return super.parse(valeur);
  }

  override format(date: Date, format: object): string {
    if ((format as { jjmmaaaa?: boolean }).jjmmaaaa) {
      return versIso(date).split('-').reverse().join('/');
    }
    return super.format(date, format);
  }

  override getFirstDayOfWeek(): number {
    return 1; // lundi
  }
}

const FORMATS_FR: MatDateFormats = {
  parse: { dateInput: { jjmmaaaa: true } },
  display: {
    dateInput: { jjmmaaaa: true },
    monthYearLabel: { year: 'numeric', month: 'long' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' },
  },
};

export const FOURNISSEURS_DATES_FR: Provider[] = [
  { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' },
  { provide: DateAdapter, useClass: AdaptateurDateFr },
  { provide: MAT_DATE_FORMATS, useValue: FORMATS_FR },
];
