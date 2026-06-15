import { OverlayContainer } from '@angular/cdk/overlay';
import { EnvironmentProviders, NgZone, inject, provideEnvironmentInitializer } from '@angular/core';

let overlayObserver: MutationObserver | null = null;

function hasOpenDialog(): boolean {
  return !!document.querySelector('.cdk-overlay-container .mat-mdc-dialog-container');
}

/** Marca modal aberto: bloqueia scroll do body e esconde shell-loading por cima do dialog. */
function syncModalOpenClass(): void {
  document.documentElement.classList.toggle('ce-modal-open', hasOpenDialog());
}

export function provideCeOverlayScrollSupport(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const overlayContainer = inject(OverlayContainer);
    const ngZone = inject(NgZone);
    const container = overlayContainer.getContainerElement();

    overlayObserver = new MutationObserver(() => {
      ngZone.run(() => syncModalOpenClass());
    });

    overlayObserver.observe(container, { childList: true, subtree: true });
    syncModalOpenClass();
  });
}
