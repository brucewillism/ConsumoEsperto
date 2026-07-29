import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MotorFinanceiroInteligente, MotorFinanceiroService } from '../../services/motor-financeiro.service';
import { PageLoadingComponent } from '../../shared/page-loading/page-loading.component';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';

@Component({
  selector: 'app-motor-financeiro',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    PageLoadingComponent,
    WhatsappParityHintComponent,
  ],
  templateUrl: './motor-financeiro.component.html',
  styleUrl: './motor-financeiro.component.scss',
})
export class MotorFinanceiroComponent implements OnInit {
  dados: MotorFinanceiroInteligente | null = null;
  carregando = false;
  incluirNarrativa = false;

  constructor(
    private readonly motorService: MotorFinanceiroService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.motorService.obter(this.incluirNarrativa).subscribe({
      next: (d) => {
        this.dados = d;
        this.carregando = false;
      },
      error: () => {
        this.carregando = false;
        this.snackBar.open('Não foi possível carregar o diagnóstico financeiro.', 'Fechar', {
          duration: 4000,
          panelClass: ['error-snackbar'],
        });
      },
    });
  }

  alternarNarrativa(): void {
    this.incluirNarrativa = !this.incluirNarrativa;
    this.carregar();
  }

  brl(v: number | undefined | null): string {
    return Number(v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  pct(v: number | undefined | null): string {
    return `${Number(v ?? 0)}%`;
  }
}
