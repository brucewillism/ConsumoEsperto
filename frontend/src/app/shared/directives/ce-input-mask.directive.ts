import { Directive, ElementRef, HostListener, Input, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';
import {
  formatCardNumberDisplay,
  formatCpfDisplay,
  formatPhoneBrDisplay,
  sanitizeCardNumberInput,
  sanitizeDecimalInput,
  sanitizeEmailInput,
  sanitizeIntegerInput,
} from '../utils/form.utils';

export type CeInputMaskType =
  | 'email'
  | 'phone'
  | 'cpf'
  | 'card'
  | 'decimal'
  | 'integer'
  | 'year';

/**
 * Máscaras leves em inputs (sem lib externa).
 * Uso: &lt;input matInput ceMask="decimal" formControlName="valor" /&gt;
 */
@Directive({
  selector: 'input[ceMask]',
  standalone: true,
})
export class CeInputMaskDirective {
  @Input('ceMask') mask: CeInputMaskType = 'decimal';

  @Input() ceMaskMaxLength?: number;

  constructor(
    private readonly el: ElementRef<HTMLInputElement>,
    @Optional() @Self() private readonly ngControl: NgControl | null
  ) {}

  @HostListener('input')
  onInput(): void {
    const input = this.el.nativeElement;
    const raw = input.value;
    let masked = raw;

    switch (this.mask) {
      case 'email':
        masked = sanitizeEmailInput(raw);
        break;
      case 'phone':
        masked = formatPhoneBrDisplay(raw);
        break;
      case 'cpf':
        masked = formatCpfDisplay(raw);
        break;
      case 'card':
        masked = formatCardNumberDisplay(sanitizeCardNumberInput(raw));
        break;
      case 'decimal':
        masked = sanitizeDecimalInput(raw);
        break;
      case 'integer':
      case 'year':
        masked = sanitizeIntegerInput(raw, this.ceMaskMaxLength ?? (this.mask === 'year' ? 4 : undefined));
        break;
    }

    if (masked !== raw) {
      input.value = masked;
    }

    this.ngControl?.control?.setValue(masked, { emitEvent: false });
  }

  @HostListener('blur')
  onBlur(): void {
    if (this.mask === 'email') {
      const input = this.el.nativeElement;
      const normalized = sanitizeEmailInput(input.value).toLowerCase();
      if (normalized !== input.value) {
        input.value = normalized;
        this.ngControl?.control?.setValue(normalized, { emitEvent: false });
      }
    }
  }
}
