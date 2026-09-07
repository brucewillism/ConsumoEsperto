import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ImportacoesPendentesComponent } from './importacoes-pendentes.component';
import { ImportacaoFaturaService } from '../../services/importacao-fatura.service';
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
});
