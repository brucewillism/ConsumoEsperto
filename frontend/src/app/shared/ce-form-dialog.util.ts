import { ComponentType } from '@angular/cdk/overlay';
import { TemplateRef } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

const SCROLLABLE_IN_OVERLAY =
  '.ce-dialog-scroll, .transacoes-modal-scroll, mat-dialog-content, .mat-mdc-dialog-content, .mat-mdc-select-panel, .mat-mdc-autocomplete-panel, .mat-mdc-menu-panel';

let modalLockCount = 0;
let savedScrollY = 0;
let wheelTrapHandler: ((event: WheelEvent) => void) | null = null;

function mergePanelClass(config?: MatDialogConfig): string | string[] {
  const extra = config?.panelClass;
  const extras = extra ? (Array.isArray(extra) ? extra : [extra]) : [];
  return ['ce-form-dialog', ...extras];
}

function stabilizeFormFields(): void {
  requestAnimationFrame(() => {
    window.dispatchEvent(new Event('resize'));
    requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
  });
}

function isAuxiliaryOverlayOpen(): boolean {
  return !!document.querySelector(
    '.cdk-overlay-pane:has(.mat-mdc-select-panel), .cdk-overlay-pane:has(.mat-datepicker-content), .cdk-overlay-pane:has(.mat-mdc-menu-panel), .cdk-overlay-pane:has(.mat-mdc-autocomplete-panel)'
  );
}

function isPointerInsideDialogPanel(event: PointerEvent): boolean {
  const panes = document.querySelectorAll(
    '.cdk-overlay-pane:has(.mat-mdc-dialog-container), .cdk-overlay-pane:has(.transacoes-modal)'
  );
  for (const pane of Array.from(panes)) {
    const rect = pane.getBoundingClientRect();
    if (
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom
    ) {
      return true;
    }
  }
  return false;
}

function findScrollableAncestor(target: Element | null): HTMLElement | null {
  if (!target) return null;
  const el = target.closest(SCROLLABLE_IN_OVERLAY) as HTMLElement | null;
  return el;
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

function lockPageScroll(): void {
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

  wheelTrapHandler = (event: WheelEvent) => {
    if (!document.documentElement.classList.contains('ce-modal-open')) return;

    const target = event.target as Element | null;
    const scrollable = findScrollableAncestor(target);
    if (scrollable && scrollElement(scrollable, event.deltaY)) {
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

function forceUnlockIfNoDialogs(): void {
  requestAnimationFrame(() => {
    if (!document.querySelector('.cdk-overlay-container .mat-mdc-dialog-container')) {
      modalLockCount = 0;
      unlockPageScroll();
    }
  });
}

function unlockPageScroll(): void {
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

/** Trava scroll da página e captura a roda do mouse para áreas roláveis do modal. */
export function wireCeDialogScrollLock<T, R = unknown>(ref: MatDialogRef<T, R>): void {
  ref.afterOpened().subscribe(() => lockPageScroll());
  ref.afterClosed().subscribe(() => forceUnlockIfNoDialogs());
}

/**
 * Fecha ao clicar no backdrop — não usa {@code backdropClick()} do CDK porque
 * scrollbar nativa e mat-select podem disparar fechamento indevido.
 */
export function wireCeDialogBackdropClose<T, R = unknown>(
  ref: MatDialogRef<T, R>,
  onBackdrop?: () => R | void
): void {
  ref.afterOpened().subscribe(() => {
    const backdrops = document.querySelectorAll<HTMLElement>(
      '.cdk-overlay-backdrop.cdk-overlay-backdrop-showing'
    );
    const backdrop = backdrops.length ? backdrops[backdrops.length - 1] : null;
    if (!backdrop) return;

    const onPointerDown = (event: PointerEvent): void => {
      if (isAuxiliaryOverlayOpen()) return;
      if (isPointerInsideDialogPanel(event)) return;
      if (event.target !== backdrop) return;

      if (onBackdrop) {
        const result = onBackdrop();
        ref.close(result as R);
      } else {
        ref.close();
      }
    };

    backdrop.addEventListener('pointerdown', onPointerDown);
    ref.afterClosed().subscribe(() => backdrop.removeEventListener('pointerdown', onPointerDown));
  });
}

/** Scroll lock + fechamento seguro no backdrop + estabilização de form fields. */
export function wireCeDialogBehavior<T, R = unknown>(
  ref: MatDialogRef<T, R>,
  onBackdrop?: () => R | void
): void {
  wireCeDialogScrollLock(ref);
  wireCeDialogBackdropClose(ref, onBackdrop);
  ref.afterOpened().subscribe(() => stabilizeFormFields());
}

export function openCeFormDialog<T, D = unknown, R = unknown>(
  dialog: MatDialog,
  componentOrTemplate: ComponentType<T> | TemplateRef<T>,
  config?: MatDialogConfig<D>
): MatDialogRef<T, R> {
  const ref = dialog.open<T, D, R>(componentOrTemplate, {
    maxWidth: '96vw',
    disableClose: true,
    ...config,
    panelClass: mergePanelClass(config),
  });

  wireCeDialogBehavior(ref);
  return ref;
}
