import { inject, Injectable } from '@angular/core';
import { MAT_DATE_LOCALE, MatDateFormats, NativeDateAdapter } from '@angular/material/core';

/** Formato de exibição dd/MM/yyyy (padrão Brasil). */
export const BR_DATE_FORMATS: MatDateFormats = {
  parse: {
    dateInput: 'DD/MM/YYYY',
  },
  display: {
    dateInput: 'DD/MM/YYYY',
    monthYearLabel: 'MMM YYYY',
    dateA11yLabel: 'DD/MM/YYYY',
    monthYearA11yLabel: 'MMMM YYYY',
  },
};

@Injectable()
export class BrDateAdapter extends NativeDateAdapter {
  constructor() {
    super(inject(MAT_DATE_LOCALE, { optional: true }) ?? 'pt-BR');
  }

  override getFirstDayOfWeek(): number {
    return 0;
  }

  override format(date: Date, displayFormat: unknown): string {
    if (!this.isValid(date)) {
      return '';
    }
    const day = date.getDate().toString().padStart(2, '0');
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const year = date.getFullYear();
    if (
      displayFormat === 'DD/MM/YYYY' ||
      displayFormat === BR_DATE_FORMATS.display.dateInput ||
      displayFormat === BR_DATE_FORMATS.parse.dateInput
    ) {
      return `${day}/${month}/${year}`;
    }
    return date.toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }

  override parse(value: unknown): Date | null {
    if (typeof value === 'string' && value.trim()) {
      const trimmed = value.trim();
      const br = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(trimmed);
      if (br) {
        const day = +br[1];
        const month = +br[2] - 1;
        const year = +br[3];
        const date = new Date(year, month, day);
        return this.isValid(date) ? date : null;
      }
      const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(trimmed);
      if (iso) {
        const date = new Date(+iso[1], +iso[2] - 1, +iso[3]);
        return this.isValid(date) ? date : null;
      }
    }
    return super.parse(value);
  }
}
