import { Directive } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';

/**
 * Marca áreas roláveis dentro de overlays (modais Material, painéis mat-select).
 */
@Directive({
  selector:
    'mat-dialog-content, .mat-mdc-select-panel, .mat-mdc-select-panel .mdc-list, .ce-dialog-scroll, .transacoes-modal-scroll',
  hostDirectives: [CdkScrollable],
  standalone: true,
})
export class CeOverlayScrollableDirective {}
