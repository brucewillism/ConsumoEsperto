import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AuthFlowOverlayState {
  active: boolean;
  message: string;
}

/** Overlay em tela cheia (sidebar + header + conteúdo). */
export interface ShellOverlayState {
  active: boolean;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
/**
 * Overlay de autenticação (login / Google). O overlay global por HTTP foi removido
 * para não interromper formulários durante polling e recargas em segundo plano.
 */
export class LoadingService {
  private activeRequests = 0;
  private pendingHideTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly loadingSubject = new BehaviorSubject<boolean>(false);
  readonly isLoading$ = this.loadingSubject.asObservable();

  /** Overlay único durante login Google/e-mail — evita segundo loading por cima. */
  private readonly authFlowSubject = new BehaviorSubject<AuthFlowOverlayState>({
    active: false,
    message: '',
  });
  readonly authFlowOverlay$ = this.authFlowSubject.asObservable();

  /** Um único overlay de página por vez (evita ficar preso por contador de profundidade). */
  private pageOverlayActive = false;
  private pageOverlayMessage = 'Carregando…';
  private requestOverlayActive = false;

  private readonly shellOverlaySubject = new BehaviorSubject<ShellOverlayState>({
    active: false,
    message: '',
  });
  readonly shellOverlay$ = this.shellOverlaySubject.asObservable();

  /** Margem fixa após o último pedido terminar, antes de ocultar (evita flicker). */
  private tailAfterRequestsMs(): number {
    const t = environment.loadingOverlayTailMs;
    return typeof t === 'number' && t >= 0 ? t : 200;
  }

  isAuthFlowActive(): boolean {
    return this.authFlowSubject.value.active;
  }

  isLoadingSnapshot(): boolean {
    return this.loadingSubject.value;
  }

  beginAuthFlow(message = 'Autenticando…'): void {
    this.authFlowSubject.next({ active: true, message });
    this.emitShellOverlay();
  }

  updateAuthFlowMessage(message: string): void {
    if (!this.isAuthFlowActive()) {
      return;
    }
    this.authFlowSubject.next({ active: true, message });
    this.emitShellOverlay();
  }

  endAuthFlow(): void {
    this.authFlowSubject.next({ active: false, message: '' });
    this.emitShellOverlay();
  }

  /**
   * Overlay de página (dashboard, page-loading, etc.) — sempre no app-root, cobre o ecrã inteiro.
   */
  setPageOverlay(active: boolean, message = 'Carregando…'): void {
    this.pageOverlayActive = active;
    if (active) {
      this.pageOverlayMessage = message;
    }
    this.emitShellOverlay();
  }

  updatePageOverlayMessage(message: string): void {
    if (this.pageOverlayActive) {
      this.pageOverlayMessage = message;
      this.emitShellOverlay();
    }
  }

  private emitShellOverlay(): void {
    const auth = this.authFlowSubject.value;
    if (auth.active) {
      this.shellOverlaySubject.next({ active: true, message: auth.message });
      return;
    }
    if (this.pageOverlayActive) {
      this.shellOverlaySubject.next({ active: true, message: this.pageOverlayMessage });
      return;
    }
    if (this.requestOverlayActive) {
      this.shellOverlaySubject.next({ active: true, message: this.requestOverlayMessage });
      return;
    }
    this.shellOverlaySubject.next({ active: false, message: '' });
  }

  private requestOverlayMessage = 'Carregando…';

  show(message = 'Carregando…'): void {
    if (this.pendingHideTimer != null) {
      clearTimeout(this.pendingHideTimer);
      this.pendingHideTimer = null;
    }

    this.requestOverlayMessage = message;
    this.activeRequests += 1;
    if (this.activeRequests === 1) {
      this.requestOverlayActive = true;
      this.loadingSubject.next(true);
      this.emitShellOverlay();
    }
  }

  hide(): void {
    if (this.activeRequests <= 0) {
      return;
    }
    this.activeRequests -= 1;
    if (this.activeRequests > 0) {
      return;
    }

    const finish = () => {
      this.requestOverlayActive = false;
      this.loadingSubject.next(false);
      this.pendingHideTimer = null;
      this.emitShellOverlay();
    };

    const tail = this.tailAfterRequestsMs();
    if (tail <= 0) {
      finish();
    } else {
      this.pendingHideTimer = setTimeout(finish, tail);
    }
  }

  /**
   * Repõe o contador (ex.: recuperação após erro excecional).
   * Evitar durante pedidos legítimos em paralelo noutros ecrãs.
   */
  reset(): void {
    if (this.pendingHideTimer != null) {
      clearTimeout(this.pendingHideTimer);
      this.pendingHideTimer = null;
    }
    this.activeRequests = 0;
    this.requestOverlayActive = false;
    this.pageOverlayActive = false;
    this.loadingSubject.next(false);
    this.endAuthFlow();
    this.emitShellOverlay();
  }
}
