import { OverlayContainer } from '@angular/cdk/overlay';
import { EnvironmentProviders, NgZone, inject, provideEnvironmentInitializer } from '@angular/core';

const OVERLAY_PANEL_SELECTORS =
  '.mat-mdc-select-panel, .mat-mdc-autocomplete-panel, .mat-mdc-menu-panel';

/**
 * Evita que interação no painel do mat-select dispare fechamento do modal por baixo.
 */
function bindOverlayPanelPointerShield(el: HTMLElement): void {
  if (el.dataset['cePointerShield']) return;
  el.dataset['cePointerShield'] = '1';

  const stop = (event: Event) => event.stopPropagation();
  el.addEventListener('pointerdown', stop, true);
  el.addEventListener('mousedown', stop, true);
  el.addEventListener('click', stop, true);
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

    const observer = new MutationObserver((records) => {
      ngZone.run(() => {
        records.forEach((record) => {
          record.addedNodes.forEach((node) => {
            if (node instanceof HTMLElement) scanOverlayPanels(node);
          });
        });
      });
    });

    observer.observe(container, { childList: true, subtree: true });
    scanOverlayPanels(container);
  });
}
