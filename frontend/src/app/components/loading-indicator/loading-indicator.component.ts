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

  /** GIF só aparece se o ficheiro existir e tiver dimensões reais (não placeholder 1×1). */
  gifVisible = false;

  onGifLoad(event: Event): void {
    const img = event.target as HTMLImageElement;
    this.gifVisible = img.naturalWidth > 4 && img.naturalHeight > 4;
  }

  onGifError(): void {
    this.gifVisible = false;
  }
}
