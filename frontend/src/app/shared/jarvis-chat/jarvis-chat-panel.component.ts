import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IaChatService } from '../../services/ia-chat.service';
import { Usuario } from '../../models/usuario.model';
import {
  JARVIS_CHAT_SUGESTOES,
  JarvisChatSugestao,
  mensagemBoasVindasJarvis,
  mensagemDigitandoJarvis,
  mensagemErroJarvis,
  mensagemRespostaVaziaJarvis,
  normalizarMensagemChat,
  vocativoJarvis,
} from './jarvis-chat.util';

export interface JarvisChatMensagem {
  autor: 'user' | 'ia';
  texto: string;
}

@Component({
  selector: 'app-jarvis-chat-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './jarvis-chat-panel.component.html',
  styleUrl: './jarvis-chat-panel.component.scss',
})
export class JarvisChatPanelComponent implements OnInit, OnChanges, OnDestroy {
  @Input() usuario: Usuario | null = null;
  @Input() dashboardCarregando = false;

  @Output() consultaConcluida = new EventEmitter<void>();

  @ViewChild('chatBody') private chatBody?: ElementRef<HTMLElement>;

  aberto = false;
  mensagem = '';
  carregando = false;
  tutorialAtivo = false;
  historico: JarvisChatMensagem[] = [];
  readonly sugestoes: JarvisChatSugestao[] = JARVIS_CHAT_SUGESTOES;

  constructor(private iaChatService: IaChatService) {}

  ngOnInit(): void {
    this.reiniciarBoasVindas();
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
    this.definirBloqueioScrollMobile(false);
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

  usarSugestao(s: JarvisChatSugestao): void {
    if (this.carregando) return;
    this.mensagem = s.pergunta;
    this.enviar();
  }

  enviarComandoTutorial(): void {
    if (this.carregando) return;
    this.tutorialAtivo = true;
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

    this.historico.push({ autor: 'user', texto });
    this.carregando = true;
    this.rolarParaFim();

    this.iaChatService.perguntar(texto).subscribe({
      next: (res) => {
        const voc = vocativoJarvis(this.usuario);
        const resposta = res.resposta?.trim() || mensagemRespostaVaziaJarvis(voc);
        this.processarRespostaJarvis(resposta);
        this.historico.push({ autor: 'ia', texto: resposta });
        this.carregando = false;
        this.consultaConcluida.emit();
        this.rolarParaFim();
      },
      error: () => {
        this.historico.push({ autor: 'ia', texto: mensagemErroJarvis() });
        this.carregando = false;
        this.rolarParaFim();
      },
    });
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
