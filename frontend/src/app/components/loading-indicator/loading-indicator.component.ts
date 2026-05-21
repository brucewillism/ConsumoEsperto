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
  @Input() message = '';
  @Input() mode: LoadingIndicatorMode = 'panel';
  @Input() size: LoadingIndicatorSize = 'md';

  /** GIF animado (preferido). SVG estático se o GIF falhar. */
  readonly loadingAssetUrl = environment.loadingAssetUrl;
  readonly loadingFallbackUrl = environment.loadingFallbackUrl;

  assetFailed = false;
  useFallback = false;

  onAssetError(): void {
    if (!this.useFallback && this.loadingFallbackUrl) {
      this.useFallback = true;
      return;
    }
    this.assetFailed = true;
  }

  get currentAssetUrl(): string {
    return this.useFallback ? this.loadingFallbackUrl : this.loadingAssetUrl;
  }
}
