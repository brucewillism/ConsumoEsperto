import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class LoadingService {
  private activeRequests = 0;
  private pendingHideTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly loadingSubject = new BehaviorSubject<boolean>(false);
  readonly isLoading$ = this.loadingSubject.asObservable();

  /** Margem fixa após o último pedido terminar, antes de ocultar (evita flicker). */
  private tailAfterRequestsMs(): number {
    const t = environment.loadingOverlayTailMs;
    return typeof t === 'number' && t >= 0 ? t : 200;
  }

  show(): void {
    if (this.pendingHideTimer != null) {
      clearTimeout(this.pendingHideTimer);
      this.pendingHideTimer = null;
    }

    this.activeRequests += 1;
    if (this.activeRequests === 1) {
      this.loadingSubject.next(true);
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
      this.loadingSubject.next(false);
      this.pendingHideTimer = null;
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
    this.loadingSubject.next(false);
  }
}
