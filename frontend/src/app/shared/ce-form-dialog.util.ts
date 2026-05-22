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

export function openCeFormDialog<T, D = unknown, R = unknown>(
  dialog: MatDialog,
  componentOrTemplate: ComponentType<T> | TemplateRef<T>,
  config?: MatDialogConfig<D>
): MatDialogRef<T, R> {
  const ref = dialog.open<T, D, R>(componentOrTemplate, {
    maxWidth: '96vw',
    ...config,
    panelClass: mergePanelClass(config),
  });

  ref.afterOpened().subscribe(() => stabilizeFormFields());
  return ref;
}
