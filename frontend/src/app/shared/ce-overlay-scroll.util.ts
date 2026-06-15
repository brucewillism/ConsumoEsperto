import { OverlayContainer } from '@angular/cdk/overlay';
import { EnvironmentProviders, NgZone, inject, provideEnvironmentInitializer } from '@angular/core';

const OVERLAY_PANEL_SELECTORS =
  '.mat-mdc-select-panel, .mat-mdc-autocomplete-panel, .mat-mdc-menu-panel';

let modalLockCount = 0;
let wheelTrapHandler: ((event: WheelEvent) => void) | null = null;
let overlayObserver: MutationObserver | null = null;

function hasOpenDialog(): boolean {
  return !!document.querySelector('.cdk-overlay-container .mat-mdc-dialog-container');
}

function isInsideOverlayUi(target: Element | null): boolean {
  return !!target?.closest('.cdk-overlay-container');
}

/**
 * Bloqueia scroll da página quando há modal aberto — sem position:fixed (preserva scroll nativo nos overlays).
 */
export function lockPageScroll(): void {
  modalLockCount += 1;
  if (modalLockCount > 1) return;

  document.documentElement.classList.add('ce-modal-open');
  document.body.classList.add('ce-modal-open');

  if (wheelTrapHandler) return;

  wheelTrapHandler = (event: WheelEvent) => {
    if (!hasOpenDialog() && !document.documentElement.classList.contains('ce-modal-open')) {
      return;
    }
    if (isInsideOverlayUi(event.target as Element)) {
      return;
    }
    event.preventDefault();
  };

  document.addEventListener('wheel', wheelTrapHandler, { passive: false, capture: true });
}

export function unlockPageScroll(): void {
  modalLockCount = Math.max(0, modalLockCount - 1);
  if (modalLockCount > 0) return;

  if (wheelTrapHandler) {
    document.removeEventListener('wheel', wheelTrapHandler, { capture: true });
    wheelTrapHandler = null;
  }

  document.documentElement.classList.remove('ce-modal-open');
  document.body.classList.remove('ce-modal-open');
}

function syncModalScrollLock(): void {
  if (hasOpenDialog()) {
    if (modalLockCount === 0) {
      lockPageScroll();
    }
    return;
  }
  if (modalLockCount > 0) {
    modalLockCount = 0;
    unlockPageScroll();
  }
}

function bindOverlayPanelPointerShield(el: HTMLElement): void {
  if (el.dataset['cePointerShield']) return;
  el.dataset['cePointerShield'] = '1';

  const stop = (event: Event) => event.stopPropagation();
  el.addEventListener('pointerdown', stop, true);
  el.addEventListener('mousedown', stop, true);
}

function scanOverlayPanels(root: ParentNode): void {
  if (!(root instanceof HTMLElement || root instanceof DocumentFragment)) return;

  if (root instanceof HTMLElement && root.matches(OVERLAY_PANEL_SELECTORS)) {
    bindOverlayPanelPointerShield(root);
  }

  root.querySelectorAll?.(OVERLAY_PANEL_SELECTORS).forEach((node) => {
    if (node instanceof HTMLElement) bindOverlayPanelPointerShield(node);
  });
}

export function provideCeOverlayScrollSupport(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const overlayContainer = inject(OverlayContainer);
    const ngZone = inject(NgZone);
    const container = overlayContainer.getContainerElement();

    overlayObserver = new MutationObserver(() => {
      ngZone.run(() => {
        syncModalScrollLock();
      });
    });

    overlayObserver.observe(container, { childList: true, subtree: true });

    const panelObserver = new MutationObserver((records) => {
      ngZone.run(() => {
        records.forEach((record) => {
          record.addedNodes.forEach((node) => {
            if (node instanceof HTMLElement) scanOverlayPanels(node);
          });
        });
      });
    });

    panelObserver.observe(container, { childList: true, subtree: true });
    scanOverlayPanels(container);
    syncModalScrollLock();
  });
}
