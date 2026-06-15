import { OverlayContainer } from '@angular/cdk/overlay';
import { EnvironmentProviders, NgZone, inject, provideEnvironmentInitializer } from '@angular/core';

const OVERLAY_PANEL_SELECTORS =
  '.mat-mdc-select-panel, .mat-mdc-autocomplete-panel, .mat-mdc-menu-panel';

const SCROLLABLE_SELECTORS = [
  '.ce-dialog-scroll',
  '.transacoes-modal-scroll',
  'mat-dialog-content',
  '.mat-mdc-dialog-content',
  '.mat-mdc-select-panel',
  '.mat-mdc-select-panel .mdc-list',
  '.mat-mdc-autocomplete-panel',
  '.mat-mdc-menu-panel',
].join(', ');

let modalLockCount = 0;
let savedScrollY = 0;
let wheelTrapHandler: ((event: WheelEvent) => void) | null = null;
let overlayObserver: MutationObserver | null = null;

function findScrollable(target: Element | null): HTMLElement | null {
  if (!target) return null;
  const el = target.closest(SCROLLABLE_SELECTORS) as HTMLElement | null;
  if (el) return el;
  const panel = target.closest('.mat-mdc-select-panel') as HTMLElement | null;
  return panel;
}

function scrollElement(el: HTMLElement, deltaY: number): boolean {
  const { scrollTop, scrollHeight, clientHeight } = el;
  const canUp = scrollTop > 0;
  const canDown = scrollTop + clientHeight < scrollHeight - 1;
  if ((deltaY < 0 && canUp) || (deltaY > 0 && canDown)) {
    el.scrollTop += deltaY;
    return true;
  }
  return false;
}

function hasOpenDialog(): boolean {
  return !!document.querySelector('.cdk-overlay-container .mat-mdc-dialog-container');
}

export function lockPageScroll(): void {
  modalLockCount += 1;
  if (modalLockCount > 1) return;

  savedScrollY = window.scrollY || document.documentElement.scrollTop || 0;
  document.documentElement.classList.add('ce-modal-open');
  document.body.classList.add('ce-modal-open');
  document.body.style.position = 'fixed';
  document.body.style.top = `-${savedScrollY}px`;
  document.body.style.left = '0';
  document.body.style.right = '0';
  document.body.style.width = '100%';

  if (wheelTrapHandler) return;

  wheelTrapHandler = (event: WheelEvent) => {
    if (!hasOpenDialog() && !document.documentElement.classList.contains('ce-modal-open')) {
      return;
    }

    const target = event.target as Element | null;
    const scrollable = findScrollable(target);
    if (scrollable) {
      scrollElement(scrollable, event.deltaY);
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }

    if (target?.closest('.cdk-overlay-pane')) {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }

    event.preventDefault();
    event.stopImmediatePropagation();
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
  document.body.style.position = '';
  document.body.style.top = '';
  document.body.style.left = '';
  document.body.style.right = '';
  document.body.style.width = '';

  window.scrollTo(0, savedScrollY);
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

function bindOverlayPanelWheel(el: HTMLElement): void {
  if (el.dataset['ceWheelBound']) return;
  el.dataset['ceWheelBound'] = '1';

  el.addEventListener(
    'wheel',
    (event: WheelEvent) => {
      scrollElement(el, event.deltaY);
      event.preventDefault();
      event.stopImmediatePropagation();
    },
    { passive: false, capture: true }
  );
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
    bindOverlayPanelWheel(root);
    const list = root.querySelector('.mdc-list') as HTMLElement | null;
    if (list) bindOverlayPanelWheel(list);
  }

  root.querySelectorAll?.(OVERLAY_PANEL_SELECTORS).forEach((node) => {
    if (node instanceof HTMLElement) {
      bindOverlayPanelPointerShield(node);
      bindOverlayPanelWheel(node);
      const list = node.querySelector('.mdc-list') as HTMLElement | null;
      if (list) bindOverlayPanelWheel(list);
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
