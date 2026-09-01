import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EdithService, EdithState } from '../../services/edith.service';

interface ChatMsg {
  autor: 'user' | 'ia' | 'system';
  texto: string;
}

@Component({
  selector: 'app-edith-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="edith-assistant" *ngIf="visible">
      <header class="edith-header">
        <h3>Assistente E.D.I.T.H.</h3>
        <span class="edith-state" [class.ok]="state === 'AVAILABLE'" [class.warn]="state !== 'AVAILABLE'">
          {{ stateLabel }}
        </span>
      </header>

      <div class="edith-body" *ngIf="state === 'AVAILABLE'; else indisponivel">
        <div class="edith-msg" *ngFor="let m of historico" [class.user]="m.autor === 'user'">
          {{ m.texto }}
        </div>
        <div class="edith-status" *ngIf="statusAtual">{{ statusAtual }}</div>
      </div>

      <ng-template #indisponivel>
        <p class="edith-off">Assistente cognitivo indisponível neste ambiente.</p>
      </ng-template>

      <footer class="edith-footer" *ngIf="state === 'AVAILABLE'">
        <input [(ngModel)]="mensagem" (keyup.enter)="enviar()" [disabled]="carregando" placeholder="Pergunte sobre suas finanças..." />
        <button type="button" (click)="enviar()" [disabled]="carregando || !mensagem.trim()">Enviar</button>
        <button type="button" (click)="retry()" *ngIf="ultimoErro" [disabled]="carregando">Tentar novamente</button>
      </footer>
    </section>
  `,
  styles: [`
    .edith-assistant { border: 1px solid #334; border-radius: 8px; padding: 12px; margin-top: 12px; }
    .edith-header { display: flex; justify-content: space-between; align-items: center; }
    .edith-state.ok { color: #2e7d32; }
    .edith-state.warn { color: #c62828; }
    .edith-body { max-height: 280px; overflow-y: auto; margin: 8px 0; }
    .edith-msg { padding: 6px 8px; margin: 4px 0; border-radius: 6px; background: #1e293b; }
    .edith-msg.user { background: #0f3d5c; text-align: right; }
    .edith-footer { display: flex; gap: 8px; }
    .edith-footer input { flex: 1; }
    .edith-status { font-size: 12px; opacity: 0.8; }
    .edith-off { opacity: 0.85; }
  `],
})
export class EdithAssistantComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private unsubscribeSse: (() => void) | null = null;

  visible = false;
  state: EdithState = 'DISABLED';
  mensagem = '';
  carregando = false;
  statusAtual = '';
  ultimoErro = false;
  historico: ChatMsg[] = [];
  conversationId: string | null = null;

  constructor(private edith: EdithService) {}

  ngOnInit(): void {
    this.edith.status().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (s) => {
        this.visible = s.enabled;
        this.state = s.state;
      },
      error: () => {
        this.visible = false;
        this.state = 'UNAVAILABLE';
      },
    });
  }

  get stateLabel(): string {
    if (this.state === 'AVAILABLE') return 'Disponível';
    if (this.state === 'UNAVAILABLE') return 'Indisponível';
    return 'Desabilitado';
  }

  enviar(): void {
    const texto = this.mensagem.trim();
    if (!texto || this.carregando) return;
    this.ultimoErro = false;
    this.carregando = true;
    this.historico.push({ autor: 'user', texto });
    this.mensagem = '';
    const clientRequestId = crypto.randomUUID();

    if (!this.conversationId) {
      this.edith.createConversation().subscribe({
        next: (c) => {
          this.conversationId = c.conversationId;
          this.enviarMensagem(texto, clientRequestId);
        },
        error: () => this.onSendError(),
      });
      return;
    }

    this.enviarMensagem(texto, clientRequestId);
  }

  private enviarMensagem(texto: string, clientRequestId: string): void {
    if (!this.conversationId) {
      this.onSendError();
      return;
    }

    this.edith.sendMessage(this.conversationId, texto, clientRequestId).subscribe({
      next: (resp) => {
        this.statusAtual = resp.status;
        this.unsubscribeSse?.();
        this.unsubscribeSse = this.edith.subscribeTaskEvents(
          resp.taskId,
          (ev) => this.onSse(ev),
          () => this.onSseError()
        );
      },
      error: () => this.onSendError(),
    });
  }

  retry(): void {
    this.ultimoErro = false;
    if (this.historico.length) {
      const lastUser = [...this.historico].reverse().find((m) => m.autor === 'user');
      if (lastUser) {
        this.mensagem = lastUser.texto;
        this.enviar();
      }
    }
  }

  private onSse(ev: { status: string; data: Record<string, unknown> }): void {
    this.statusAtual = ev.status;
    if (ev.status === 'COMPLETED') {
      const result = String(ev.data['result'] ?? '');
      if (result) {
        this.historico.push({ autor: 'ia', texto: result });
      }
      this.carregando = false;
      this.unsubscribeSse?.();
    } else if (ev.status === 'FAILED') {
      this.onSseError();
    }
  }

  private onSseError(): void {
    this.carregando = false;
    this.ultimoErro = true;
    this.historico.push({ autor: 'system', texto: 'Não foi possível concluir a análise (E.D.I.T.H.).' });
    this.unsubscribeSse?.();
  }

  private onSendError(): void {
    this.carregando = false;
    this.ultimoErro = true;
    this.historico.push({ autor: 'system', texto: 'Falha ao enviar mensagem.' });
  }
}
