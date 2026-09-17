import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AutonomyReviewItem, AutonomyService } from '../../services/autonomy.service';
import { ToastService } from '../../services/toast.service';
import { resolveHttpError } from '../../shared/utils/form.utils';

@Component({
  selector: 'app-revisao-autonomia',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './revisao-autonomia.component.html',
  styleUrl: './revisao-autonomia.component.scss',
})
export class RevisaoAutonomiaComponent implements OnInit {
  itens: AutonomyReviewItem[] = [];
  carregando = true;
  processandoId: number | null = null;

  constructor(
    private autonomy: AutonomyService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.autonomy.revisao().subscribe({
      next: (r) => {
        this.itens = r.itens || [];
        this.carregando = false;
      },
      error: (err) => {
        this.carregando = false;
        this.toast.error(resolveHttpError(err, 'Não foi possível carregar a revisão.'));
      },
    });
  }

  resolver(item: AutonomyReviewItem): void {
    this.processandoId = item.id;
    this.autonomy.resolver(item.id).subscribe({
      next: () => {
        this.processandoId = null;
        this.itens = this.itens.filter((i) => i.id !== item.id);
        this.toast.success('Item resolvido.');
      },
      error: (err) => {
        this.processandoId = null;
        this.toast.error(resolveHttpError(err, 'Não foi possível resolver.'));
      },
    });
  }
}
