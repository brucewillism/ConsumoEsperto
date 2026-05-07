import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { ImportacaoFatura, ImportacaoFaturaService } from '../../services/importacao-fatura.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-importacoes-pendentes',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatIconModule],
  templateUrl: './importacoes-pendentes.component.html',
  styleUrl: './importacoes-pendentes.component.scss'
})
export class ImportacoesPendentesComponent implements OnInit {
  importacoes: ImportacaoFatura[] = [];
  carregando = true;
  confirmandoId: number | null = null;
  dragOver = false;
  enviandoPdf = false;

  constructor(
    private importacaoService: ImportacaoFaturaService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.importacaoService.pendentes().subscribe({
      next: (res) => {
        this.importacoes = res;
        this.carregando = false;
      },
      error: () => {
        this.toast.error('Erro ao carregar importações pendentes.');
        this.carregando = false;
      }
    });
  }

  confirmar(imp: ImportacaoFatura): void {
    const indices = imp.itens
      .map((item, index) => ({ item, index }))
      .filter(({ item }) => item.novo && item.selecionado)
      .map(({ index }) => index);
    if (!indices.length) {
      this.toast.warning('Selecione pelo menos um lançamento novo.');
      return;
    }
    this.confirmandoId = imp.id;
    this.importacaoService.confirmar(imp.id, indices).subscribe({
      next: (res) => {
        this.toast.success(`${res.criadas} lançamento(s) importado(s).`);
        this.confirmandoId = null;
        this.carregar();
      },
      error: (e) => {
        this.toast.error(e?.error?.message || 'Erro ao confirmar importação.');
        this.confirmandoId = null;
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.enviarPdf(file);
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) this.enviarPdf(file);
  }

  enviarPdf(file: File): void {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      this.toast.warning('Arraste ou selecione uma fatura em PDF.');
      return;
    }
    this.enviandoPdf = true;
    this.importacaoService.upload(file).subscribe({
      next: () => {
        this.toast.success('Fatura processada. Revise a conciliação antes de confirmar.');
        this.enviandoPdf = false;
        this.carregar();
      },
      error: (e) => {
        this.toast.error(e?.error?.message || 'Erro ao processar PDF.');
        this.enviandoPdf = false;
      }
    });
  }

  brl(v: number | null | undefined): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
