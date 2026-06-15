import { OverlayContainer } from '@angular/cdk/overlay';
import { EnvironmentProviders, NgZone, inject, provideEnvironmentInitializer } from '@angular/core';

const OVERLAY_PANEL_SELECTORS =
  '.mat-mdc-select-panel, .mat-mdc-autocomplete-panel, .mat-mdc-menu-panel';

let overlayObserver: MutationObserver | null = null;

function hasOpenDialog(): boolean {
  return !!document.querySelector('.cdk-overlay-container .mat-mdc-dialog-container');
}

function syncModalOpenClass(): void {
  document.documentElement.classList.toggle('ce-modal-open', hasOpenDialog());
}

/** Evita que clique no mat-select dispare handlers do modal por baixo. */
function bindOverlayPanelPointerShield(el: HTMLElement): void {
  if (el.dataset['cePointerShield']) return;
  el.dataset['cePointerShield'] = '1';

  const stop = (event: Event) => event.stopPropagation();
  el.addEventListener('pointerdown', stop, true);
  el.addEventListener('mousedown', stop, true);
}

/** Roda do mouse nos painéis mat-select (block() do dialog não os cobre). */
function bindOverlayPanelWheel(el: HTMLElement): void {
  if (el.dataset['ceWheelBound']) return;
  el.dataset['ceWheelBound'] = '1';

  const scrollTarget = (): HTMLElement => {
    const list = el.querySelector('.mdc-list') as HTMLElement | null;
    if (list && list.scrollHeight > list.clientHeight + 1) return list;
    return el;
  };

  el.addEventListener(
    'wheel',
    (event: WheelEvent) => {
      const target = scrollTarget();
      const { scrollTop, scrollHeight, clientHeight } = target;
      if (scrollHeight <= clientHeight + 1) return;
      target.scrollTop = Math.min(
        scrollHeight - clientHeight,
        Math.max(0, scrollTop + event.deltaY)
      );
      event.preventDefault();
      event.stopPropagation();
    },
    { passive: false }
  );
}

function scanOverlayPanels(root: ParentNode): void {
  if (!(root instanceof HTMLElement || root instanceof DocumentFragment)) return;

  if (root instanceof HTMLElement && root.matches(OVERLAY_PANEL_SELECTORS)) {
    bindOverlayPanelPointerShield(root);
    bindOverlayPanelWheel(root);
  }

  root.querySelectorAll?.(OVERLAY_PANEL_SELECTORS).forEach((node) => {
    if (node instanceof HTMLElement) {
      bindOverlayPanelPointerShield(node);
      bindOverlayPanelWheel(node);
    }
  });
}

export function provideCeOverlayScrollSupport(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const overlayContainer = inject(OverlayContainer);
    const ngZone = inject(NgZone);
    const container = overlayContainer.getContainerElement();

    overlayObserver = new MutationObserver(() => {
      ngZone.run(() => {
        syncModalOpenClass();
        scanOverlayPanels(container);
      });
    });

    overlayObserver.observe(container, { childList: true, subtree: true });
    syncModalOpenClass();
    scanOverlayPanels(container);
  });
}
