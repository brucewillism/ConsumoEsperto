import { Directive } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';

/**
 * Marca áreas roláveis dentro de overlays (modais Material, painéis mat-select)
 * para o {@link BlockScrollStrategy} do CDK — sem isto a roda do mouse move a página de fundo.
 */
@Directive({
  selector: 'mat-dialog-content, .mat-mdc-select-panel, .ce-dialog-scroll, .transacoes-modal-scroll',
  hostDirectives: [CdkScrollable],
  standalone: true,
})
export class CeOverlayScrollableDirective {}
