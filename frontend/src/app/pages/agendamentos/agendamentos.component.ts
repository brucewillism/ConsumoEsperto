import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import {
  AgendamentoPagamento,
  AgendamentoPagamentoRequest,
  AgendamentoPagamentoService,
  StatusAgendamento,
} from '../../services/agendamento-pagamento.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { CategoriaService } from '../../services/categoria.service';
import { ContaBancaria } from '../../models/conta-bancaria.model';
import { CartaoCredito } from '../../models/cartao-credito.model';
import { Categoria } from '../../models/categoria.model';
import { RECORRENCIAS, descricaoRecorrencia, labelRecorrencia } from '../../utils/agendamento-recorrencia.util';
import { WhatsappParityHintComponent } from '../../shared/whatsapp-parity-hint/whatsapp-parity-hint.component';

@Component({
  selector: 'app-agendamentos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    MatChipsModule,
    MatTooltipModule,
    WhatsappParityHintComponent,
  ],
  templateUrl: './agendamentos.component.html',
  styleUrl: './agendamentos.component.scss',
})
export class AgendamentosComponent implements OnInit {
  lista: AgendamentoPagamento[] = [];
  historico: AgendamentoPagamento[] = [];
  contas: ContaBancaria[] = [];
  cartoes: CartaoCredito[] = [];
  categorias: Categoria[] = [];

  carregando = false;
  carregandoHistorico = false;
  erroCarregamento = false;
  salvando = false;
  acaoEmAndamentoId: number | null = null;

  modalAberto = false;
  detalheAberto: AgendamentoPagamento | null = null;
  editando: AgendamentoPagamento | null = null;
  form: AgendamentoPagamentoRequest = this.novoForm();

  busca = '';
  filtroStatus: '' | StatusAgendamento = '';
  abaAtiva = 0;

  readonly recorrencias = RECORRENCIAS;
  readonly statusOpcoes: StatusAgendamento[] = ['AGENDADO', 'PAUSADO', 'PAGO', 'FALHOU', 'CANCELADO'];

  constructor(
    private readonly service: AgendamentoPagamentoService,
    private readonly contaService: ContaBancariaService,
    private readonly cartaoService: CartaoCreditoService,
    private readonly categoriaService: CategoriaService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.carregarTudo();
  }

  carregarTudo(): void {
    this.carregando = true;
    this.erroCarregamento = false;
    forkJoin({
      agendamentos: this.service.listar(),
      contas: this.contaService.listarContasAtivas(),
      cartoes: this.cartaoService.getCartoes(),
      categorias: this.categoriaService.buscarPorUsuario(),
    }).subscribe({
      next: ({ agendamentos, contas, cartoes, categorias }) => {
        this.lista = agendamentos ?? [];
        this.contas = contas ?? [];
        this.cartoes = cartoes ?? [];
        this.categorias = categorias ?? [];
        this.carregando = false;
      },
      error: (err) => {
        this.lista = [];
        this.carregando = false;
        this.erroCarregamento = true;
        const msg = err?.status === 401 ? 'Sessão expirada.' : 'Não foi possível carregar agendamentos.';
        this.snackBar.open(msg, 'Fechar', { duration: 4000, panelClass: ['error-snackbar'] });
      },
    });
  }

  carregarHistorico(): void {
    if (this.carregandoHistorico) return;
    this.carregandoHistorico = true;
    this.service.historico().subscribe({
      next: (items) => {
        this.historico = items ?? [];
        this.carregandoHistorico = false;
      },
      error: () => {
        this.historico = [];
        this.carregandoHistorico = false;
        this.snackBar.open('Erro ao carregar histórico.', 'Fechar', { duration: 4000, panelClass: ['error-snackbar'] });
      },
    });
  }

  onTabChange(index: number): void {
    this.abaAtiva = index;
    if (index === 1 && !this.historico.length) {
      this.carregarHistorico();
    }
  }

  get listaFiltrada(): AgendamentoPagamento[] {
    const termo = this.busca.trim().toLowerCase();
    return this.lista.filter((a) => {
      if (this.filtroStatus && a.status !== this.filtroStatus) return false;
      if (!termo) return true;
      return (a.beneficiario ?? '').toLowerCase().includes(termo)
        || String(a.valor).includes(termo)
        || (a.contaDebitoNome ?? '').toLowerCase().includes(termo);
    });
  }

  abrirNova(): void {
    if (!this.contas.length) {
      this.snackBar.open('Cadastre uma conta bancária antes de agendar.', 'Fechar', { duration: 4000 });
      return;
    }
    this.editando = null;
    this.form = this.novoForm();
    this.modalAberto = true;
  }

  abrirEditar(item: AgendamentoPagamento): void {
    this.editando = item;
    this.form = {
      contaDebitoId: item.contaDebitoId ?? this.contas[0]?.id ?? 0,
      beneficiario: item.beneficiario,
      valor: item.valor,
      dataVencimento: item.dataVencimento?.substring(0, 10) ?? '',
      codigoBarrasOuPix: item.codigoBarrasOuPix,
      categoriaId: item.categoriaId,
      cartaoCreditoId: item.cartaoCreditoId,
      recorrencia: item.recorrencia ?? 'UNICA',
      dataFim: item.dataFim?.substring(0, 10),
      diaVencimentoMensal: item.diaVencimentoMensal,
    };
    this.modalAberto = true;
  }

  abrirDetalhe(item: AgendamentoPagamento): void {
    this.detalheAberto = item;
  }

  fecharDetalhe(): void {
    this.detalheAberto = null;
  }

  fecharModal(): void {
    this.modalAberto = false;
    this.editando = null;
  }

  salvar(): void {
    if (this.salvando) return;
    const erro = this.validarForm();
    if (erro) {
      this.snackBar.open(erro, 'Fechar', { duration: 3500, panelClass: ['error-snackbar'] });
      return;
    }
    this.salvando = true;
    const op = this.editando?.id
      ? this.service.atualizar(this.editando.id, this.form)
      : this.service.criar(this.form);
    op.subscribe({
      next: () => {
        this.salvando = false;
        this.fecharModal();
        this.snackBar.open('Agendamento salvo.', 'Fechar', { duration: 3000, panelClass: ['success-snackbar'] });
        this.carregarTudo();
      },
      error: (err) => {
        this.salvando = false;
        const st = err?.status;
        let msg = 'Erro ao salvar agendamento.';
        if (st === 401) msg = 'Não autorizado.';
        else if (st === 409) msg = 'Conflito: agendamento já processado ou duplicado.';
        else if (err?.error?.message) msg = String(err.error.message);
        this.snackBar.open(msg, 'Fechar', { duration: 4500, panelClass: ['error-snackbar'] });
      },
    });
  }

  pausar(item: AgendamentoPagamento): void {
    this.acao('pausar', item.id, () => this.service.pausar(item.id), 'Agendamento pausado.');
  }

  ativar(item: AgendamentoPagamento): void {
    this.acao('ativar', item.id, () => this.service.ativar(item.id), 'Agendamento reativado.');
  }

  cancelar(item: AgendamentoPagamento): void {
    if (!confirm(`Cancelar agendamento "${item.beneficiario}"?`)) return;
    this.acao('cancelar', item.id, () => this.service.cancelar(item.id), 'Agendamento cancelado.');
  }

  executar(item: AgendamentoPagamento): void {
    if (!confirm(`Executar manualmente "${item.beneficiario}" agora?`)) return;
    this.acao('executar', item.id, () => this.service.executar(item.id), 'Execução manual solicitada.');
  }

  marcarPago(item: AgendamentoPagamento): void {
    this.acao('marcarPago', item.id, () => this.service.marcarPago(item.id), 'Marcado como pago.');
  }

  private acao(_nome: string, id: number, fn: () => ReturnType<AgendamentoPagamentoService['pausar']>, sucesso: string): void {
    if (this.acaoEmAndamentoId != null) return;
    this.acaoEmAndamentoId = id;
    fn().subscribe({
      next: () => {
        this.acaoEmAndamentoId = null;
        this.snackBar.open(sucesso, 'Fechar', { duration: 3000, panelClass: ['success-snackbar'] });
        this.carregarTudo();
        if (this.abaAtiva === 1) this.carregarHistorico();
      },
      error: (err) => {
        this.acaoEmAndamentoId = null;
        const msg = err?.status === 401 ? 'Não autorizado.' : 'Falha na operação.';
        this.snackBar.open(msg, 'Fechar', { duration: 4000, panelClass: ['error-snackbar'] });
      },
    });
  }

  validarForm(): string | null {
    if (!this.form.contaDebitoId) return 'Selecione uma conta.';
    if (!this.form.beneficiario?.trim()) return 'Informe a descrição/beneficiário.';
    if (!this.form.valor || this.form.valor <= 0) return 'Informe um valor válido.';
    if (!this.form.dataVencimento) return 'Informe a data inicial/vencimento.';
    const rec = this.form.recorrencia ?? 'UNICA';
    const mensais = ['MENSAL', 'BIMESTRAL', 'TRIMESTRAL', 'SEMESTRAL'];
    if (mensais.includes(rec) && this.form.diaVencimentoMensal != null) {
      const d = this.form.diaVencimentoMensal;
      if (d < 1 || d > 31) return 'Dia da execução deve ser entre 1 e 31.';
    }
    return null;
  }

  readonly labelRecorrencia = labelRecorrencia;

  descricaoRec(item: AgendamentoPagamento): string {
    return descricaoRecorrencia(item.recorrencia ?? 'UNICA', item.diaVencimentoMensal);
  }

  brl(v: number | undefined | null): string {
    return Number(v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  formatarData(d?: string): string {
    if (!d) return '—';
    const dt = new Date(d);
    return Number.isNaN(dt.getTime()) ? d : dt.toLocaleDateString('pt-BR');
  }

  statusLabel(s: StatusAgendamento): string {
    const map: Record<StatusAgendamento, string> = {
      AGENDADO: 'Agendado',
      PAUSADO: 'Pausado',
      PAGO: 'Pago',
      FALHOU: 'Falhou',
      CANCELADO: 'Cancelado',
    };
    return map[s] ?? s;
  }

  acaoBloqueada(id: number): boolean {
    return this.acaoEmAndamentoId === id;
  }

  podeCancelar(item: AgendamentoPagamento): boolean {
    return item.status === 'AGENDADO' || item.status === 'PAUSADO' || item.status === 'FALHOU';
  }

  private novoForm(): AgendamentoPagamentoRequest {
    const hoje = new Date();
    const ymd = `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
    return {
      contaDebitoId: this.contas[0]?.id ?? 0,
      beneficiario: '',
      valor: 0,
      dataVencimento: ymd,
      recorrencia: 'UNICA',
    };
  }
}
