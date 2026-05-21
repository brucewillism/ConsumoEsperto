import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { environment } from '../../../environments/environment';

export type LoadingIndicatorMode = 'inline' | 'panel' | 'overlay';
export type LoadingIndicatorSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-loading-indicator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './loading-indicator.component.html',
  styleUrl: './loading-indicator.component.scss',
})
export class LoadingIndicatorComponent {
  /** Texto opcional abaixo do indicador. */
  @Input() message = '';

  /** inline = compacto; panel = bloco centrado na página; overlay = ecrã inteiro. */
  @Input() mode: LoadingIndicatorMode = 'panel';

  @Input() size: LoadingIndicatorSize = 'md';

  readonly loadingGifUrl = environment.loadingGifUrl;

  /** Spinner até o GIF carregar; placeholder 1×1 mantém só o spinner. */
  gifVisible = false;
  gifFailed = false;

  onGifLoad(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img.naturalWidth <= 4 || img.naturalHeight <= 4) {
      this.gifVisible = false;
      this.gifFailed = true;
      return;
    }
    this.gifVisible = true;
    this.gifFailed = false;
  }

  onGifError(): void {
    this.gifVisible = false;
    this.gifFailed = true;
  }
}
