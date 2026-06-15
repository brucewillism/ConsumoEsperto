import { ComponentType } from '@angular/cdk/overlay';
import { TemplateRef } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

function mergePanelClass(config?: MatDialogConfig): string | string[] {
  const extra = config?.panelClass;
  const extras = extra ? (Array.isArray(extra) ? extra : [extra]) : [];
  return ['ce-form-dialog', ...extras];
}

/** Recalcula notch/altura dos mat-form-field após o dialog terminar de abrir. */
function stabilizeFormFields(): void {
  requestAnimationFrame(() => {
    window.dispatchEvent(new Event('resize'));
    requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
  });
}

function getDialogContainerForRef<T>(ref: MatDialogRef<T, unknown>): HTMLElement | null {
  const containers = document.querySelectorAll<HTMLElement>(
    '.cdk-overlay-container .mat-mdc-dialog-container'
  );
  return containers.length ? containers[containers.length - 1] : null;
}

function isBackdropClick(event: MouseEvent, backdrop: HTMLElement, dialog: HTMLElement | null): boolean {
  if (event.target !== backdrop) return false;
  if (!dialog) return true;

  const rect = dialog.getBoundingClientRect();
  const gutter = 24;
  const { clientX: x, clientY: y } = event;
  return !(
    x >= rect.left - gutter &&
    x <= rect.right + gutter &&
    y >= rect.top - gutter &&
    y <= rect.bottom + gutter
  );
}

/**
 * Fecha ao clicar no backdrop — usa click (não pointerdown) e ignora a zona do dialog + scrollbar.
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

    const dialog = getDialogContainerForRef(ref);

    const onClick = (event: MouseEvent): void => {
      if (!isBackdropClick(event, backdrop, dialog)) return;

      if (onBackdrop) {
        const result = onBackdrop();
        ref.close(result as R);
      } else {
        ref.close();
      }
    };

    backdrop.addEventListener('click', onClick);
    ref.afterClosed().subscribe(() => backdrop.removeEventListener('click', onClick));
  });
}

/** Fechamento seguro no backdrop + estabilização de form fields. Scroll lock é global (overlay observer). */
export function wireCeDialogBehavior<T, R = unknown>(
  ref: MatDialogRef<T, R>,
  onBackdrop?: () => R | void
): void {
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
