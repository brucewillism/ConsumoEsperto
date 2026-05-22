import { AbstractControl, FormGroup, ValidationErrors } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

/** Marca todos os controles como touched para exibir erros inline. */
export function markAllControlsTouched(control: AbstractControl): void {
  control.markAsTouched();
  if (control instanceof FormGroup) {
    Object.values(control.controls).forEach(markAllControlsTouched);
  }
}

/** Converte texto monetário brasileiro (1.234,56 ou 1234.56) em número. */
export function parseValorBrasileiro(v: unknown): number | null {
  if (v == null) {
    return null;
  }
  if (typeof v === 'number' && !Number.isNaN(v)) {
    return v;
  }
  const s = String(v).trim().replace(/\s/g, '').replace(/R\$\s?/i, '');
  if (!s) {
    return null;
  }
  const normalized = s.includes(',') ? s.replace(/\./g, '').replace(',', '.') : s;
  const n = parseFloat(normalized);
  return Number.isFinite(n) ? n : null;
}

/** Validador para campos de valor em formato brasileiro. */
export function valorMonetarioBrValidator(control: AbstractControl): ValidationErrors | null {
  const raw = control.value;
  if (raw == null || String(raw).trim() === '') {
    return null;
  }
  const n = parseValorBrasileiro(raw);
  if (n == null || n <= 0) {
    return { valorInvalido: true };
  }
  return null;
}

/** Mensagem amigável a partir de erro HTTP da API. */
export function resolveHttpError(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Erro de conexão. Verifique sua internet e tente novamente.';
    }
    const msg = error.error?.message;
    if (typeof msg === 'string' && msg.trim()) {
      return msg;
    }
    if (error.status === 409) {
      return 'Este registro já existe ou conflita com outro cadastro.';
    }
    if (error.status === 400) {
      return 'Verifique os dados informados e tente novamente.';
    }
  }
  return fallback;
}

/** Valida e-mail simples. */
export function isEmailValido(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}
