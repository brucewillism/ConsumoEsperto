import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoadingIndicatorComponent, LoadingIndicatorSize } from '../../components/loading-indicator/loading-indicator.component';

@Component({
  selector: 'app-page-loading',
  standalone: true,
  imports: [CommonModule, LoadingIndicatorComponent],
  template: `
    <section class="page-loading-block" aria-live="polite" aria-busy="true">
      <app-loading-indicator mode="panel" [size]="size" [message]="message"></app-loading-indicator>
    </section>
  `,
  styleUrl: './page-loading.component.scss',
})
export class PageLoadingComponent {
  @Input() message = 'Carregando…';
  @Input() size: LoadingIndicatorSize = 'md';
}
