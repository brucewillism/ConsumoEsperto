import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormsModule } from '@angular/forms';
import { Subject, takeUntil, catchError, of } from 'rxjs';

import { BankApiService, CreditCardInvoice, BankTransaction } from '../../services/bank-api.service';
import { FaturaService } from '../../services/fatura.service';

@Component({
  selector: 'app-faturas',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    ReactiveFormsModule,
    FormsModule
  ],
  templateUrl: './faturas.component.html',
  styleUrls: ['./faturas.component.scss']
})
export class FaturasComponent implements OnInit, OnDestroy {
  faturas: CreditCardInvoice[] = [];
  faturasFiltradas: CreditCardInvoice[] = [];
  loading = false;
  showForm = false;
  
  // Filtros
  filtroStatus = '';
  filtroBanco = '';
  filtroMes = '';
  
  // Formulário
  novaFaturaForm: FormGroup;
  
  // Resumos
  totalFaturas = 0;
  totalFaturasPagas = 0;
  totalFaturasPendentes = 0;
  totalFaturasVencidas = 0;
  
  private destroy$ = new Subject<void>();
  Math = Math;

  constructor(
    private bankApiService: BankApiService,
    private faturaService: FaturaService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private fb: FormBuilder
  ) {
    this.novaFaturaForm = this.fb.group({
      banco: ['', Validators.required],
      valor: ['', [Validators.required, Validators.min(0.01)]],
      vencimento: ['', Validators.required],
      fechamento: ['', Validators.required],
      status: ['PENDING', Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public loadData(): void {
    this.loading = true;
    
    // Carrega dados do backend
    this.faturaService.getFaturasCartao()
      .pipe(
        takeUntil(this.destroy$),
        catchError(error => {
          console.error('Erro ao carregar faturas do backend:', error);
          this.snackBar.open('Erro ao carregar faturas. Verifique sua conexão.', 'Fechar', { duration: 3000 });
          // Retorna lista vazia em caso de erro
          return of([]);
        })
      )
      .subscribe({
        next: (faturas) => {
          this.faturas = faturas || [];
          this.aplicarFiltros();
          this.calcularResumos();
          this.loading = false;
        },
        error: (error) => {
          console.error('Erro ao carregar faturas:', error);
          this.snackBar.open('Erro ao carregar faturas. Verifique sua conexão.', 'Fechar', { duration: 3000 });
          this.faturas = [];
          this.aplicarFiltros();
          this.calcularResumos();
          this.loading = false;
        }
      });
  }

  carregarFaturas(): void {
    this.aplicarFiltros();
    this.calcularResumos();
  }

  adicionarFatura(): void {
    if (this.novaFaturaForm.valid) {
      const formValue = this.novaFaturaForm.value;
      const novaFatura: CreditCardInvoice = {
        id: Date.now().toString(),
        cardId: formValue.banco + '-card',
        bankName: formValue.banco,
        amount: formValue.valor,
        dueDate: formValue.vencimento,
        closingDate: formValue.fechamento,
        status: formValue.status,
        transactions: []
      };

      // Tenta salvar no backend primeiro
      this.faturaService.criarFaturaCartao(novaFatura)
        .pipe(
          takeUntil(this.destroy$),
          catchError(error => {
            console.warn('Erro ao salvar no backend, salvando localmente:', error);
            // Se falhar, adiciona localmente
            this.faturas.unshift(novaFatura);
            return of(novaFatura);
          })
        )
        .subscribe({
          next: (fatura) => {
            if (fatura.id !== novaFatura.id) {
              // Fatura foi salva no backend, atualiza a lista
              this.faturas.unshift(fatura);
            }
            this.snackBar.open('Fatura adicionada com sucesso!', 'Fechar', { duration: 3000 });
            this.novaFaturaForm.reset();
            this.showForm = false;
            this.carregarFaturas();
          },
          error: (error) => {
            console.error('Erro ao adicionar fatura:', error);
            this.snackBar.open('Erro ao adicionar fatura. Tente novamente.', 'Fechar', { duration: 3000 });
          }
        });
    }
  }

  editarFatura(fatura: CreditCardInvoice): void {
    // Implementar edição de fatura
    this.snackBar.open('Funcionalidade de edição em desenvolvimento', 'Fechar', { duration: 3000 });
  }

  excluirFatura(fatura: CreditCardInvoice): void {
    if (confirm(`Tem certeza que deseja excluir a fatura ${fatura.bankName}?`)) {
      // Tenta excluir do backend primeiro
      this.faturaService.excluirFaturaCartao(fatura)
        .pipe(
          takeUntil(this.destroy$),
          catchError(error => {
            console.warn('Erro ao excluir do backend, removendo localmente:', error);
            // Se falhar, remove localmente
            this.faturas = this.faturas.filter(f => f.id !== fatura.id);
            return of(void 0);
          })
        )
        .subscribe({
          next: () => {
            this.snackBar.open('Fatura excluída com sucesso!', 'Fechar', { duration: 3000 });
            this.carregarFaturas();
          },
          error: (error) => {
            console.error('Erro ao excluir fatura:', error);
            this.snackBar.open('Erro ao excluir fatura. Tente novamente.', 'Fechar', { duration: 3000 });
          }
        });
    }
  }

  marcarComoPaga(fatura: CreditCardInvoice): void {
    const faturaAtualizada = { ...fatura, status: 'PAID' as const };

    // Tenta atualizar no backend primeiro
    this.faturaService.atualizarFaturaCartao(faturaAtualizada)
      .pipe(
        takeUntil(this.destroy$),
        catchError(error => {
          console.warn('Erro ao atualizar no backend, atualizando localmente:', error);
          // Se falhar, atualiza localmente
          const index = this.faturas.findIndex(f => f.id === fatura.id);
          if (index !== -1) {
            this.faturas[index] = faturaAtualizada;
          }
          return of(faturaAtualizada);
        })
      )
      .subscribe({
        next: (fatura) => {
          this.snackBar.open('Fatura marcada como paga!', 'Fechar', { duration: 3000 });
          this.carregarFaturas();
        },
        error: (error) => {
          console.error('Erro ao marcar fatura como paga:', error);
          this.snackBar.open('Erro ao atualizar fatura. Tente novamente.', 'Fechar', { duration: 3000 });
        }
      });
  }

  visualizarTransacoes(fatura: CreditCardInvoice): void {
    // Implementar visualização de transações
    this.snackBar.open('Funcionalidade de transações em desenvolvimento', 'Fechar', { duration: 3000 });
  }

  aplicarFiltros(): void {
    let faturasFiltradas = [...this.faturas];

    if (this.filtroStatus) {
      faturasFiltradas = faturasFiltradas.filter(f => f.status === this.filtroStatus);
    }

    if (this.filtroBanco) {
      faturasFiltradas = faturasFiltradas.filter(f => f.bankName === this.filtroBanco);
    }

    if (this.filtroMes) {
      faturasFiltradas = faturasFiltradas.filter(f => {
        const dataFatura = new Date(f.closingDate);
        const mesAno = `${dataFatura.getFullYear()}-${String(dataFatura.getMonth() + 1).padStart(2, '0')}`;
        return mesAno === this.filtroMes;
      });
    }

    this.faturasFiltradas = faturasFiltradas;
  }

  limparFiltros(): void {
    this.filtroStatus = '';
    this.filtroBanco = '';
    this.filtroMes = '';
    this.aplicarFiltros();
  }

  calcularResumos(): void {
    this.totalFaturas = this.faturasFiltradas.reduce((total, f) => total + f.amount, 0);
    this.totalFaturasPagas = this.faturasFiltradas
      .filter(f => f.status === 'PAID')
      .reduce((total, f) => total + f.amount, 0);
    this.totalFaturasPendentes = this.faturasFiltradas
      .filter(f => f.status === 'PENDING')
      .reduce((total, f) => total + f.amount, 0);
    this.totalFaturasVencidas = this.faturasFiltradas
      .filter(f => f.status === 'OVERDUE')
      .reduce((total, f) => total + f.amount, 0);
  }

  // Métodos auxiliares
  formatarMoeda(valor: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(valor);
  }

  formatarData(data: Date): string {
    return new Intl.DateTimeFormat('pt-BR').format(data);
  }

  getDiasVencimento(fatura: CreditCardInvoice): number {
    const hoje = new Date();
    const vencimento = new Date(fatura.dueDate);
    const diffTime = vencimento.getTime() - hoje.getTime();
    return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  }

  isVencendo(fatura: CreditCardInvoice): boolean {
    const dias = this.getDiasVencimento(fatura);
    return dias >= 0 && dias <= 7;
  }

  isVencida(fatura: CreditCardInvoice): boolean {
    return this.getDiasVencimento(fatura) < 0;
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'PAID': return 'primary';
      case 'PENDING': return 'accent';
      case 'OVERDUE': return 'warn';
      default: return 'primary';
    }
  }

  getStatusText(status: string): string {
    switch (status) {
      case 'PAID': return 'Paga';
      case 'PENDING': return 'Pendente';
      case 'OVERDUE': return 'Vencida';
      default: return 'Pendente';
    }
  }

  getBancoColor(bankName: string): string {
    const cores = {
      'itau': '#EC7000',
      'nubank': '#8A05BE',
      'inter': '#FF7A00',
      'mercadopago': '#009EE3'
    };
    return cores[bankName as keyof typeof cores] || '#666';
  }

  getBancoNome(bankName: string): string {
    const nomes = {
      'itau': 'Itaú',
      'nubank': 'Nubank',
      'inter': 'Banco Inter',
      'mercadopago': 'Mercado Pago'
    };
    return nomes[bankName as keyof typeof nomes] || bankName;
  }
}
