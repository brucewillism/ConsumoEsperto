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

function isBackdropPointerEvent(event: PointerEvent, backdrop: HTMLElement): boolean {
  const target = event.target as HTMLElement | null;
  if (!target || target !== backdrop) return false;
  if (target.closest('.cdk-overlay-pane')) return false;
  return true;
}

/**
 * Fecha ao clicar no backdrop — ignora cliques em painéis overlay (dialog, mat-select, calendário).
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
      if (!isBackdropPointerEvent(event, backdrop)) return;

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

function wireCeDialogPanelShield<T>(ref: MatDialogRef<T, unknown>): void {
  ref.afterOpened().subscribe(() => {
    const containers = document.querySelectorAll<HTMLElement>(
      '.cdk-overlay-container .mat-mdc-dialog-container'
    );
    const container = containers[containers.length - 1];
    if (!container) return;

    const pane = container.closest('.cdk-overlay-pane') ?? container;
    const stop = (event: Event) => event.stopPropagation();
    pane.addEventListener('pointerdown', stop, true);
    pane.addEventListener('mousedown', stop, true);
    ref.afterClosed().subscribe(() => {
      pane.removeEventListener('pointerdown', stop, true);
      pane.removeEventListener('mousedown', stop, true);
    });
  });
}

/** Fechamento seguro no backdrop + estabilização de form fields. Scroll lock é global (overlay observer). */
export function wireCeDialogBehavior<T, R = unknown>(
  ref: MatDialogRef<T, R>,
  onBackdrop?: () => R | void
): void {
  wireCeDialogBackdropClose(ref, onBackdrop);
  wireCeDialogPanelShield(ref);
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
