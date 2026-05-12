import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  private readonly ctaOperacao =
    'Se o problema continuar, aguarde um instante e tente de novo ou reformule o pedido.';

  constructor(private snackBar: MatSnackBar) {}

  /** Sucesso — tom Stark (ciano). */
  success(message?: string): void {
    const m = message?.trim()
      ? message.trim()
      : 'Protocolo executado com sucesso, Senhor.';
    this.open(m, ['jarvis-success-snackbar'], 3800);
  }

  /** Erro genérico — painel âmbar/roxo. */
  error(message: string): void {
    this.errorJarvis(message, undefined);
  }

  warning(message: string): void {
    this.open(message, ['warning-snackbar'], 4500);
  }

  info(message: string): void {
    this.open(message, ['info-snackbar'], 4000);
  }

  /**
   * Erro a partir da resposta HTTP (ApiError com {@code message} e opcionalmente {@code instrucao}).
   */
  errorFromHttpResponse(err: HttpErrorResponse, fallback: string): void {
    const parsed = this.parseJarvisErrorPayload(err.error);
    const msg = parsed.message?.trim() ? parsed.message.trim() : fallback;
    this.errorJarvis(msg, parsed.instrucao);
  }

  /** Sessão / 401 — protocolo de segurança (usa painel de erro Stark). */
  sessionProtocolExpired(): void {
    const text = [
      'Protocolo de segurança negado, Senhor.',
      'A sua sessão pode ter expirado — faça login novamente para revalidar as credenciais.',
    ].join('\n\n');
    this.open(text, ['jarvis-error-snackbar'], 6500);
  }

  errorJarvis(message: string, instrucao?: string | null): void {
    const text = this.montarTextoJarvisErro(message, instrucao);
    this.open(text, ['jarvis-error-snackbar'], 7500);
  }

  private montarTextoJarvisErro(message: string, instrucao?: string | null): string {
    const base = message.trim();
    const ins = instrucao?.trim();
    const cta = ins || this.ctaOperacao;
    return `${base}\n\n${cta}`;
  }

  private parseJarvisErrorPayload(body: unknown): { message?: string; instrucao?: string } {
    if (body == null) {
      return {};
    }
    if (typeof body === 'string') {
      const s = body.trim();
      return s ? { message: s } : {};
    }
    if (typeof body !== 'object') {
      return {};
    }
    const b = body as Record<string, unknown>;
    const message =
      b['message'] != null
        ? String(b['message'])
        : b['error'] != null && typeof b['error'] === 'string'
          ? String(b['error'])
          : undefined;
    const instrucao = b['instrucao'] != null ? String(b['instrucao']) : undefined;
    return { message, instrucao };
  }

  private open(message: string, panelClass: string[], durationMs: number): void {
    this.snackBar.open(message, 'Fechar', {
      duration: durationMs,
      horizontalPosition: 'right',
      verticalPosition: 'top',
      panelClass,
    });
  }
}
