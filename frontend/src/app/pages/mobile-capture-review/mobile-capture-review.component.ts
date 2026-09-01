import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ConfirmMobileCaptureEvent,
  MobileCaptureEventReview,
  MobileCaptureService,
} from '../../services/mobile-capture.service';
import { CategoriaService } from '../../services/categoria.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { Categoria } from '../../models/categoria.model';
import { ContaBancaria } from '../../models/conta-bancaria.model';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { ToastService } from '../../services/toast.service';
import { resolveHttpError } from '../../shared/utils/form.utils';

@Component({
  selector: 'app-mobile-capture-review',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './mobile-capture-review.component.html',
  styleUrl: './mobile-capture-review.component.scss',
})
export class MobileCaptureReviewComponent implements OnInit {
  eventos: MobileCaptureEventReview[] = [];
  carregando = true;
  processandoId: number | null = null;
  categorias: Categoria[] = [];
  contas: ContaBancaria[] = [];
  cartoes: CartaoCredito[] = [];
  forms: Record<number, ConfirmMobileCaptureEvent & { saveRule: boolean }> = {};

  constructor(
    private mobileCapture: MobileCaptureService,
    private categoriaService: CategoriaService,
    private contaService: ContaBancariaService,
    private cartaoService: CartaoCreditoService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.carregar();
    this.categoriaService.buscarPorUsuario().subscribe({ next: (c) => (this.categorias = c) });
    this.contaService.listarContasAtivas().subscribe({ next: (c) => (this.contas = c) });
    this.cartaoService.getCartoes().subscribe({ next: (c) => (this.cartoes = c) });
  }

  carregar(): void {
    this.carregando = true;
    this.mobileCapture.listReviewEvents().subscribe({
      next: (list) => {
        this.eventos = list;
        for (const ev of list) {
          if (!this.forms[ev.id]) {
            this.forms[ev.id] = {
              merchant: ev.merchantNormalized || ev.merchantRaw || '',
              contaBancariaId: null,
              cartaoCreditoId: null,
              categoriaId: null,
              saveRule: false,
            };
          }
        }
        this.carregando = false;
      },
      error: (err) => {
        this.carregando = false;
        this.toast.error(resolveHttpError(err, 'Não foi possível carregar eventos pendentes.'));
      },
    });
  }

  confirmar(ev: MobileCaptureEventReview): void {
    const form = this.forms[ev.id];
    this.processandoId = ev.id;
    this.mobileCapture
      .confirmEvent(ev.id, {
        merchant: form.merchant,
        contaBancariaId: form.contaBancariaId,
        cartaoCreditoId: form.cartaoCreditoId,
        categoriaId: form.categoriaId,
        saveMerchantCategoryRule: form.saveRule,
      })
      .subscribe({
        next: () => {
          this.toast.success('Transação confirmada.');
          this.processandoId = null;
          this.carregar();
        },
        error: (err) => {
          this.processandoId = null;
          this.toast.error(resolveHttpError(err, 'Não foi possível confirmar o evento.'));
        },
      });
  }

  descartar(ev: MobileCaptureEventReview): void {
    this.processandoId = ev.id;
    this.mobileCapture.discardEvent(ev.id).subscribe({
      next: () => {
        this.toast.success('Evento descartado.');
        this.processandoId = null;
        this.carregar();
      },
      error: (err) => {
        this.processandoId = null;
        this.toast.error(resolveHttpError(err, 'Não foi possível descartar.'));
      },
    });
  }
}
