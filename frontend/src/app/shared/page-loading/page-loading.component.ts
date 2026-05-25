import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { LoadingService } from '../../services/loading.service';

/**
 * Ativa overlay global em tela cheia via LoadingService (não renderiza bloco local).
 */
@Component({
  selector: 'app-page-loading',
  standalone: true,
  template: '',
})
export class PageLoadingComponent implements OnInit, OnDestroy {
  @Input() message = 'Carregando…';

  constructor(private loadingService: LoadingService) {}

  ngOnInit(): void {
    this.loadingService.setPageOverlay(true, this.message);
  }

  ngOnDestroy(): void {
    this.loadingService.setPageOverlay(false);
  }
}
