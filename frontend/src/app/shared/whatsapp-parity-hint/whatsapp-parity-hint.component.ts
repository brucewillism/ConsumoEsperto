import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { WhatsappParidadeService, WhatsappParityItem } from '../../services/whatsapp-paridade.service';

@Component({
  selector: 'app-whatsapp-parity-hint',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './whatsapp-parity-hint.component.html',
  styleUrl: './whatsapp-parity-hint.component.scss',
})
export class WhatsappParityHintComponent implements OnInit {
  /** Rota do app (ex.: /transacoes). Se vazio, usa a URL atual. */
  @Input() rota?: string;

  itens: WhatsappParityItem[] = [];
  expandido = false;
  carregando = true;
  erro = false;

  private readonly paridade = inject(WhatsappParidadeService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    const rota = this.rota ?? this.router.url.split('?')[0];
    this.paridade.listarPorRota(rota).subscribe({
      next: (res) => {
        this.itens = (res.itens ?? []).filter((i) => i.canal !== 'APP_ONLY' || (i.exemplosWhatsapp?.length ?? 0) > 0);
        this.carregando = false;
      },
      error: () => {
        this.erro = true;
        this.carregando = false;
      },
    });
  }

  canalLabel(canal: string): string {
    switch (canal) {
      case 'WHATSAPP_ONLY':
        return 'Só WhatsApp';
      case 'APP_ONLY':
        return 'Só app';
      default:
        return 'App + WhatsApp';
    }
  }
}
