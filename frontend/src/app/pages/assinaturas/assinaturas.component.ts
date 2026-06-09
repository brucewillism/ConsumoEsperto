import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AssinaturaRecorrente, AssinaturaRecorrenteService } from '../../services/assinatura-recorrente.service';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';

@Component({
  selector: 'app-assinaturas',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    WhatsappParityHintComponent,
  ],
  templateUrl: './assinaturas.component.html',
  styleUrl: './assinaturas.component.scss',
})
export class AssinaturasComponent implements OnInit {
  lista: AssinaturaRecorrente[] = [];
  carregando = false;
  salvando = false;
  modalAberto = false;
  editando: AssinaturaRecorrente | null = null;
  form: AssinaturaRecorrente = this.novaVazia();

  constructor(
    private readonly service: AssinaturaRecorrenteService,
    private readonly snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.service.listar().subscribe({
      next: (items) => {
        this.lista = items ?? [];
        this.carregando = false;
      },
      error: () => {
        this.lista = [];
        this.carregando = false;
        this.snackBar.open('Não foi possível carregar assinaturas', 'Fechar', { duration: 3000 });
      },
    });
  }

  abrirNova(): void {
    this.editando = null;
    this.form = this.novaVazia();
    this.modalAberto = true;
  }

  abrirEditar(item: AssinaturaRecorrente): void {
    this.editando = item;
    this.form = { ...item };
    this.modalAberto = true;
  }

  fecharModal(): void {
    this.modalAberto = false;
    this.editando = null;
  }

  salvar(): void {
    if (!this.form.nome?.trim() || !this.form.valor || this.form.valor <= 0) {
      this.snackBar.open('Preencha nome e valor', 'Fechar', { duration: 2500 });
      return;
    }
    if (this.form.diaVencimento < 1 || this.form.diaVencimento > 31) {
      this.snackBar.open('Dia de vencimento deve ser entre 1 e 31', 'Fechar', { duration: 2500 });
      return;
    }
    this.salvando = true;
    const req = { ...this.form };
    const op = this.editando?.id
      ? this.service.atualizar(this.editando.id, req)
      : this.service.criar(req);
    op.subscribe({
      next: () => {
        this.salvando = false;
        this.fecharModal();
        this.snackBar.open('Assinatura salva', 'Fechar', { duration: 2500 });
        this.carregar();
      },
      error: (err) => {
        this.salvando = false;
        const msg = err?.error?.message || err?.error || 'Erro ao salvar';
        this.snackBar.open(typeof msg === 'string' ? msg : 'Erro ao salvar', 'Fechar', { duration: 3500 });
      },
    });
  }

  alternarAtivo(item: AssinaturaRecorrente): void {
    if (!item.id) return;
    const novo = !item.ativo;
    this.service.alternarAtivo(item.id, novo).subscribe({
      next: (atualizada) => {
        item.ativo = atualizada.ativo;
        this.snackBar.open(atualizada.ativo ? 'Assinatura ativada' : 'Assinatura pausada', 'Fechar', { duration: 2500 });
      },
      error: () => this.snackBar.open('Falha ao alterar status', 'Fechar', { duration: 2500 }),
    });
  }

  excluir(item: AssinaturaRecorrente): void {
    if (!item.id || !confirm(`Excluir assinatura "${item.nome}"?`)) return;
    this.service.excluir(item.id).subscribe({
      next: () => {
        this.snackBar.open('Assinatura excluída', 'Fechar', { duration: 2500 });
        this.carregar();
      },
      error: () => this.snackBar.open('Falha ao excluir', 'Fechar', { duration: 2500 }),
    });
  }

  formatarValor(v: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v ?? 0);
  }

  private novaVazia(): AssinaturaRecorrente {
    return {
      nome: '',
      valor: 0,
      diaVencimento: 10,
      ativo: true,
    };
  }
}
