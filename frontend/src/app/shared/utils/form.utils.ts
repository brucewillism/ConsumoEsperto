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
    if (error.status === 503) {
      const msg503 = error.error?.message;
      if (typeof msg503 === 'string' && msg503.trim()) {
        return msg503;
      }
      return 'Serviço de IA temporariamente indisponível. Tente novamente em cerca de 1–2 horas.';
    }
    if (error.status === 409) {
      return 'Este registro já existe ou conflita com outro cadastro.';
    }
    if (error.status === 400) {
      const msg400 = error.error?.message;
      if (typeof msg400 === 'string' && msg400.trim()) {
        return msg400;
      }
      return 'Verifique os dados informados e tente novamente.';
    }
  }
  return fallback;
}

/** Valida e-mail simples. */
export function isEmailValido(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim().toLowerCase());
}

/** Normaliza e-mail (sem espaços, minúsculas). */
export function sanitizeEmailInput(value: string): string {
  return value.replace(/\s/g, '');
}

/** Apenas dígitos do cartão (até 19). */
export function sanitizeCardNumberInput(value: string): string {
  return sanitizeIntegerInput(value, 19);
}

/** Exibe cartão em blocos de 4 dígitos. */
export function formatCardNumberDisplay(digits: string): string {
  return digits.replace(/(\d{4})(?=\d)/g, '$1 ').trim();
}

/** CPF parcial ou completo: 000.000.000-00 */
export function formatCpfDisplay(value: string): string {
  const d = sanitizeIntegerInput(value, 11);
  if (d.length <= 3) {
    return d;
  }
  if (d.length <= 6) {
    return `${d.slice(0, 3)}.${d.slice(3)}`;
  }
  if (d.length <= 9) {
    return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`;
  }
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

/** Telefone BR: +55 (11) 99999-9999 ou variantes parciais. */
export function formatPhoneBrDisplay(value: string): string {
  let d = value.replace(/\D/g, '');
  if (!d) {
    return '';
  }
  if (d.startsWith('55') && d.length > 11) {
    d = d.slice(0, 13);
  } else if (!d.startsWith('55') && d.length > 11) {
    d = d.slice(0, 11);
  }
  if (d.startsWith('55')) {
    const rest = d.slice(2);
    if (rest.length <= 2) {
      return `+55 (${rest}`;
    }
    if (rest.length <= 7) {
      return `+55 (${rest.slice(0, 2)}) ${rest.slice(2)}`;
    }
    return `+55 (${rest.slice(0, 2)}) ${rest.slice(2, 7)}-${rest.slice(7)}`;
  }
  if (d.length <= 2) {
    return `(${d}`;
  }
  if (d.length <= 7) {
    return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  }
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

/** Validador de CPF (11 dígitos, dígitos verificadores). */
export function cpfValidator(control: AbstractControl): ValidationErrors | null {
  const digits = sanitizeIntegerInput(String(control.value ?? ''), 11);
  if (!digits) {
    return null;
  }
  if (digits.length !== 11 || /^(\d)\1{10}$/.test(digits)) {
    return { cpfInvalido: true };
  }
  const calc = (len: number) => {
    let sum = 0;
    for (let i = 0; i < len; i++) {
      sum += +digits[i] * (len + 1 - i);
    }
    const mod = (sum * 10) % 11;
    return mod === 10 ? 0 : mod;
  };
  if (calc(9) !== +digits[9] || calc(10) !== +digits[10]) {
    return { cpfInvalido: true };
  }
  return null;
}

/** Apenas dígitos; limita tamanho opcionalmente. */
export function sanitizeIntegerInput(value: string, maxLength?: number): string {
  const digits = value.replace(/\D/g, '');
  return maxLength != null ? digits.slice(0, maxLength) : digits;
}

/** Dígitos com no máximo um separador decimal (, ou .). */
export function sanitizeDecimalInput(value: string): string {
  if (!value) {
    return '';
  }
  let result = '';
  let sepUsed = false;
  for (const ch of value) {
    if (ch >= '0' && ch <= '9') {
      result += ch;
    } else if ((ch === ',' || ch === '.') && !sepUsed) {
      result += ch;
      sepUsed = true;
    }
  }
  return result;
}
