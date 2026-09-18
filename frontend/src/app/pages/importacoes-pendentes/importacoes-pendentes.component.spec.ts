import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ImportacoesPendentesComponent } from './importacoes-pendentes.component';
import { ImportacaoFatura, ImportacaoFaturaService } from '../../services/importacao-fatura.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { CategoriaService } from '../../services/categoria.service';
import { whatsappParityTestProviders } from '../../testing/component-test-providers';

describe('ImportacoesPendentesComponent', () => {
  let fixture: ComponentFixture<ImportacoesPendentesComponent>;
  let component: ImportacoesPendentesComponent;
  let importacao: jasmine.SpyObj<ImportacaoFaturaService>;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    importacao = jasmine.createSpyObj('ImportacaoFaturaService', [
      'pendentes', 'upload', 'confirmar', 'excluirPendente', 'excluirTodasPendentes',
      'escolhaSaldoAnterior', 'escolhaRecurso', 'atualizarItens',
    ]);
    toast = jasmine.createSpyObj('ToastService', ['success', 'error', 'warning', 'errorFromHttpResponse']);
    importacao.pendentes.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [ImportacoesPendentesComponent, NoopAnimationsModule],
      providers: [
        ...whatsappParityTestProviders(),
        { provide: ImportacaoFaturaService, useValue: importacao },
        { provide: ToastService, useValue: toast },
        { provide: ConfirmDialogService, useValue: { ask: () => of(false) } },
        { provide: ContaBancariaService, useValue: { listarContasAtivas: () => of([]) } },
        { provide: CartaoCreditoService, useValue: { getCartoes: () => of([]) } },
        { provide: CategoriaService, useValue: { buscarPorUsuario: () => of([]) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ImportacoesPendentesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('aceita PDF e CSV no seletor', () => {
    const input: HTMLInputElement | null = fixture.nativeElement.querySelector('input[type=file]');
    expect(input?.accept).toContain('.pdf');
    expect(input?.accept).toContain('.csv');
  });

  it('reconhece extrato CSV', () => {
    expect(component.isCsv({
      id: 1,
      bancoCartao: 'Nubank',
      valorTotal: 0,
      pagamentoMinimo: 0,
      status: 'PENDENTE',
      novosDetectados: 1,
      itens: [],
      auditorias: [],
      dataCriacao: '',
      tipoArquivo: 'CARD_STATEMENT_CSV',
    })).toBeTrue();
  });

  it('rotula tipo detectado', () => {
    expect(component.tipoDetectadoLabel({
      id: 1,
      bancoCartao: 'x',
      valorTotal: 0,
      pagamentoMinimo: 0,
      status: 'PENDENTE',
      novosDetectados: 0,
      itens: [],
      auditorias: [],
      dataCriacao: '',
      tipoArquivo: 'BANK_STATEMENT_CSV',
    })).toBe('Extrato bancário CSV');
  });

  it('abrir a aba com lista vazia não mostra toast', () => {
    expect(toast.error).not.toHaveBeenCalled();
    expect(component.erroCarregar).toBeFalse();
    expect(component.importacoes.length).toBe(0);
  });

  it('falha ao listar mostra estado no ecrã sem toast', () => {
    importacao.pendentes.and.returnValue(throwError(() => ({ status: 500 })));
    component.carregar();
    expect(toast.error).not.toHaveBeenCalled();
    expect(component.erroCarregar).toBeTrue();
    expect(component.carregando).toBeFalse();
    expect(component.importacoes.length).toBe(0);
  });

  it('marcar todos seleciona só linhas marcáveis e desmarca na segunda vez', () => {
    const imp: ImportacaoFatura = {
      id: 9,
      bancoCartao: 'Nubank',
      valorTotal: 0,
      pagamentoMinimo: 0,
      status: 'PENDENTE',
      novosDetectados: 2,
      auditorias: [],
      dataCriacao: '',
      tipoArquivo: 'BANK_STATEMENT_CSV',
      itens: [
        { data: '2026-07-07', descricao: 'Atacadao', valor: 10, novo: true, selecionado: false, statusPreview: 'NEEDS_REVIEW' },
        { data: '2026-07-06', descricao: 'Claro', valor: 20, novo: true, selecionado: false, statusPreview: 'NOVO' },
        { data: '2026-07-05', descricao: 'Dup', valor: 30, novo: false, selecionado: false, statusPreview: 'DUPLICATE' },
      ],
    };
    importacao.atualizarItens.and.returnValue(of(imp));
    component.alternarMarcarTodos(imp);
    expect(imp.itens[0].selecionado).toBeTrue();
    expect(imp.itens[1].selecionado).toBeTrue();
    expect(imp.itens[2].selecionado).toBeFalse();
    expect(component.todosMarcados(imp)).toBeTrue();
    component.alternarMarcarTodos(imp);
    expect(imp.itens[0].selecionado).toBeFalse();
    expect(imp.itens[1].selecionado).toBeFalse();
    expect(imp.itens[2].selecionado).toBeFalse();
  });
});
