import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { switchMap, startWith } from 'rxjs/operators';
import {
  EvolutionAdminService,
  EvolutionHealth,
  EvolutionSessaoDetalhe,
} from '../../services/evolution-admin.service';
import { PageLoadingComponent } from '../../shared/page-loading/page-loading.component';

@Component({
  selector: 'app-admin-evolution',
  standalone: true,
  imports: [CommonModule, MatCardModule, RouterLink, PageLoadingComponent],
  templateUrl: './admin-evolution.component.html',
  styleUrl: './admin-evolution.component.scss',
})
export class AdminEvolutionComponent implements OnInit, OnDestroy {
  health: EvolutionHealth | null = null;
  carregando = true;
  erro: string | null = null;
  private pollSub?: Subscription;

  constructor(private evolutionAdmin: EvolutionAdminService) {}

  ngOnInit(): void {
    this.pollSub = interval(30_000)
      .pipe(
        startWith(0),
        switchMap(() => this.evolutionAdmin.obterHealth(true))
      )
      .subscribe({
        next: (h) => {
          this.health = h;
          this.carregando = false;
          this.erro = null;
        },
        error: (e) => {
          this.carregando = false;
          this.erro = e?.error?.reason === 'admin-api-key-required'
            ? 'Acesso negado. Inicie sessão ou configure X-Admin-Api-Key.'
            : 'Não foi possível carregar métricas Evolution.';
        },
      });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  atualizar(): void {
    this.carregando = true;
    this.evolutionAdmin.obterHealth(true).subscribe({
      next: (h) => {
        this.health = h;
        this.carregando = false;
        this.erro = null;
      },
      error: () => {
        this.carregando = false;
        this.erro = 'Falha ao atualizar métricas.';
      },
    });
  }

  formatUptime(segundos: number): string {
    if (segundos <= 0) {
      return '—';
    }
    const h = Math.floor(segundos / 3600);
    const m = Math.floor((segundos % 3600) / 60);
    if (h > 0) {
      return `${h}h ${m}m`;
    }
    return `${m}m`;
  }

  formatInatividade(seg: number): string {
    if (seg < 0) {
      return '—';
    }
    if (seg < 60) {
      return `${seg}s`;
    }
    return `${Math.floor(seg / 60)} min`;
  }

  trackSessao(_: number, s: EvolutionSessaoDetalhe): string {
    return s.instancia;
  }
}
