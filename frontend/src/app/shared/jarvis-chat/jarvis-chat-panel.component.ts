import {
  Component,
  DestroyRef,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { fromEvent } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IaChatService, IaChatResponse } from '../../services/ia-chat.service';
import { EdithService, EdithSsePayload } from '../../services/edith.service';
import { CapabilityService, CapabilityInvokeResponse } from '../../services/capability.service';
import { Usuario } from '../../models/usuario.model';
import {
  CONSUMO_APPLICATION_ID,
  JARVIS_CHAT_SUGESTOES,
  JarvisAssistantState,
  JarvisChatSugestao,
  mensagemBoasVindasJarvis,
  mensagemDigitandoJarvis,
  mensagemErroJarvis,
  mensagemRespostaVaziaJarvis,
  normalizarMensagemChat,
  rotuloEstadoJarvis,
  vocativoJarvis,
} from './jarvis-chat.util';

export interface JarvisChatMensagem {
  autor: 'user' | 'ia';
  texto: string;
  capability?: string;
  data?: Record<string, unknown>;
}

@Component({
  selector: 'app-jarvis-chat-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './jarvis-chat-panel.component.html',
  styleUrl: './jarvis-chat-panel.component.scss',
})
export class JarvisChatPanelComponent implements OnInit, OnChanges, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);

  @Input() usuario: Usuario | null = null;
  @Input() dashboardCarregando = false;
  @Input() screen = 'dashboard';
  @Input() entityType = '';
  @Input() entityId = '';

  @Output() consultaConcluida = new EventEmitter<void>();

  @ViewChild('chatBody') private chatBody?: ElementRef<HTMLElement>;

  aberto = false;
  isFullscreen = false;
  mensagem = '';
  carregando = false;
  tutorialAtivo = false;
  historico: JarvisChatMensagem[] = [];
  assistantState: JarvisAssistantState = 'LOCAL';
  readonly sugestoes: JarvisChatSugestao[] = JARVIS_CHAT_SUGESTOES;

  private pendingCapability?: string;
  private unsubscribeSse: (() => void) | null = null;

  constructor(
    private iaChatService: IaChatService,
    private edithService: EdithService,
    private capabilityService: CapabilityService
  ) {}

  ngOnInit(): void {
    this.reiniciarBoasVindas();
    this.checkFullscreen();
    this.carregarEstadoAssistente();
    if (typeof window !== 'undefined') {
      fromEvent(window, 'resize')
        .pipe(debounceTime(150), takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.checkFullscreen());
    }
  }

  private checkFullscreen(): void {
    if (typeof window === 'undefined') {
      return;
    }
    this.isFullscreen = window.innerWidth < 768;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      changes['usuario'] &&
      this.historico.length === 1 &&
      this.historico[0]?.autor === 'ia' &&
      !this.carregando
    ) {
      this.reiniciarBoasVindas();
    }
  }

  ngOnDestroy(): void {
    this.unsubscribeSse?.();
    this.definirBloqueioScrollMobile(false);
  }

  get assistantLabel(): string {
    return rotuloEstadoJarvis(this.assistantState);
  }

  toggle(): void {
    this.aberto = !this.aberto;
    this.definirBloqueioScrollMobile(this.aberto);
    if (this.aberto) {
      setTimeout(() => this.rolarParaFim(), 0);
    }
  }

  fechar(): void {
    this.aberto = false;
    this.definirBloqueioScrollMobile(false);
  }

  fecharChat(): void {
    this.fechar();
  }

  usarSugestao(s: JarvisChatSugestao): void {
    if (this.carregando) return;
    this.pendingCapability = s.capability;
    this.mensagem = s.pergunta;
    this.enviar();
  }

  enviarComandoTutorial(): void {
    if (this.carregando) return;
    this.tutorialAtivo = true;
    this.pendingCapability = undefined;
    this.enviarMensagemDireta('tutorial');
  }

  encerrarTutorial(): void {
    if (this.carregando) return;
    this.enviarMensagemDireta('sair');
    this.tutorialAtivo = false;
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.enviar();
    }
  }

  enviar(): void {
    const texto = normalizarMensagemChat(this.mensagem);
    if (!texto || this.carregando) return;
    this.mensagem = '';
    this.enviarMensagemDireta(texto);
  }

  private enviarMensagemDireta(texto: string): void {
    if (!texto || this.carregando) return;
    const capability = this.pendingCapability;
    this.pendingCapability = undefined;

    this.historico.push({ autor: 'user', texto });
    this.carregando = true;
    this.rolarParaFim();

    if (capability) {
      this.capabilityService.invoke(capability, {}).subscribe({
        next: (res) => this.onCapabilityResponse(capability, res),
        error: () => {
          this.historico.push({ autor: 'ia', texto: mensagemErroJarvis() });
          this.carregando = false;
          this.rolarParaFim();
        },
      });
      return;
    }

    this.iaChatService
      .perguntar({
        mensagem: texto,
        capability,
        screen: this.screen,
        entityType: this.entityType,
        entityId: this.entityId,
        applicationId: CONSUMO_APPLICATION_ID,
      })
      .subscribe({
        next: (res) => this.onChatResponse(res),
        error: () => {
          this.historico.push({ autor: 'ia', texto: mensagemErroJarvis() });
          this.carregando = false;
          this.rolarParaFim();
        },
      });
  }

  private onCapabilityResponse(capability: string, res: CapabilityInvokeResponse): void {
    this.assistantState = 'LOCAL';
    this.historico.push({
      autor: 'ia',
      texto: '',
      capability,
      data: res.data || {},
    });
    this.carregando = false;
    this.consultaConcluida.emit();
    this.rolarParaFim();
  }

  cardsOf(msg: JarvisChatMensagem): Record<string, unknown>[] {
    return this.listOf(msg, 'cartoes');
  }

  listOf(msg: JarvisChatMensagem, key: string): Record<string, unknown>[] {
    const raw = msg.data?.[key];
    return Array.isArray(raw) ? (raw as Record<string, unknown>[]) : [];
  }

  brl(value: unknown): string {
    const n = typeof value === 'number' ? value : Number(value);
    if (Number.isNaN(n)) {
      return '—';
    }
    return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private onChatResponse(res: IaChatResponse): void {
    if (res.assistant) {
      this.assistantState = res.assistant as JarvisAssistantState;
    }
    if (res.mode === 'EDITH' && res.taskId && !res.resposta?.trim()) {
      this.unsubscribeSse?.();
      this.historico.push({ autor: 'ia', texto: '' });
      const idx = this.historico.length - 1;
      this.unsubscribeSse = this.edithService.subscribeTaskEvents(
        res.taskId,
        (ev) => this.onSse(ev, idx),
        () => {
          this.historico[idx].texto = this.historico[idx].texto || mensagemErroJarvis();
          this.carregando = false;
          this.rolarParaFim();
        }
      );
      return;
    }

    const voc = vocativoJarvis(this.usuario);
    const resposta = res.resposta?.trim() || mensagemRespostaVaziaJarvis(voc);
    this.processarRespostaJarvis(resposta);
    this.historico.push({ autor: 'ia', texto: resposta });
    this.carregando = false;
    this.consultaConcluida.emit();
    this.rolarParaFim();
  }

  private onSse(ev: EdithSsePayload, idx: number): void {
    const delta = ev.data?.['delta'];
    if (typeof delta === 'string' && delta) {
      this.historico[idx].texto += delta;
      this.rolarParaFim();
    }
    if (ev.status === 'COMPLETED') {
      const result = String(ev.data?.['result'] ?? '').trim();
      if (result) {
        this.historico[idx].texto = result;
      } else if (!this.historico[idx].texto.trim()) {
        this.historico[idx].texto = mensagemRespostaVaziaJarvis(vocativoJarvis(this.usuario));
      }
      this.processarRespostaJarvis(this.historico[idx].texto);
      this.carregando = false;
      this.unsubscribeSse?.();
      this.unsubscribeSse = null;
      this.consultaConcluida.emit();
      this.rolarParaFim();
    } else if (ev.status === 'FAILED') {
      this.historico[idx].texto = mensagemErroJarvis();
      this.assistantState = 'DEGRADED';
      this.carregando = false;
      this.unsubscribeSse?.();
      this.unsubscribeSse = null;
      this.rolarParaFim();
    }
  }

  private processarRespostaJarvis(resposta: string): void {
    if (
      resposta.includes('Voltei para o modo de operação padrão') ||
      resposta.includes('Tutorial encerrado')
    ) {
      this.tutorialAtivo = false;
    }
    if (resposta.includes('GUIA DE OPERAÇÕES — J.A.R.V.I.S.')) {
      this.tutorialAtivo = true;
    }
  }

  get mensagemDigitando(): string {
    return mensagemDigitandoJarvis();
  }

  private carregarEstadoAssistente(): void {
    this.edithService.status().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (s) => {
        this.assistantState = (s.assistant as JarvisAssistantState) || (s.enabled ? 'ONLINE' : 'LOCAL');
      },
      error: () => {
        this.assistantState = 'LOCAL';
      },
    });
  }

  private reiniciarBoasVindas(): void {
    this.historico = [{ autor: 'ia', texto: mensagemBoasVindasJarvis(this.usuario) }];
  }

  private rolarParaFim(): void {
    const el = this.chatBody?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }

  private definirBloqueioScrollMobile(ativo: boolean): void {
    if (typeof document === 'undefined' || typeof window === 'undefined') {
      return;
    }
    const mobile = window.matchMedia('(max-width: 767px)').matches;
    document.body.classList.toggle('jarvis-chat-open-mobile', ativo && mobile);
  }
}
