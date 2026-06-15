import { OverlayContainer } from '@angular/cdk/overlay';
import { EnvironmentProviders, NgZone, inject, provideEnvironmentInitializer } from '@angular/core';

const OVERLAY_SCROLL_SELECTORS =
  '.mat-mdc-select-panel, .mat-mdc-autocomplete-panel, .mat-mdc-menu-panel, .ce-dialog-scroll';

/**
 * Painéis de mat-select/menu são criados pelo CDK fora dos nossos templates.
 * Com {@link BlockScrollStrategy} no dialog, a roda do mouse precisa ser tratada aqui.
 */
function bindOverlayPanelWheel(el: HTMLElement): void {
  if (el.dataset['ceWheelBound']) return;
  el.dataset['ceWheelBound'] = '1';

  el.addEventListener(
    'wheel',
    (event: WheelEvent) => {
      const { scrollTop, scrollHeight, clientHeight } = el;
      const dy = event.deltaY;
      const canScrollUp = scrollTop > 0;
      const canScrollDown = scrollTop + clientHeight < scrollHeight - 1;

      if ((dy < 0 && canScrollUp) || (dy > 0 && canScrollDown)) {
        event.preventDefault();
        event.stopImmediatePropagation();
        el.scrollTop += dy;
      }
    },
    { passive: false, capture: true }
  );
}

function scanOverlayScrollables(root: ParentNode): void {
  if (!(root instanceof HTMLElement || root instanceof DocumentFragment)) return;

  if (root instanceof HTMLElement && root.matches(OVERLAY_SCROLL_SELECTORS)) {
    bindOverlayPanelWheel(root);
  }

  root.querySelectorAll?.(OVERLAY_SCROLL_SELECTORS).forEach((node) => {
    if (node instanceof HTMLElement) bindOverlayPanelWheel(node);
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
            if (node instanceof HTMLElement) scanOverlayScrollables(node);
          });
        });
      });
    });

    observer.observe(container, { childList: true, subtree: true });
    scanOverlayScrollables(container);
  });
}
