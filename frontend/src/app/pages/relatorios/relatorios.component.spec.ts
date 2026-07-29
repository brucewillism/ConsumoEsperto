import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Chart, registerables } from 'chart.js';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RelatoriosComponent } from './relatorios.component';
import { RelatorioService } from '../../services/relatorio.service';
import { ExportacaoDadosService } from '../../services/exportacao-dados.service';
import { TransacaoService } from '../../services/transacao.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { CategoriaService } from '../../services/categoria.service';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { whatsappParityTestProviders } from '../../testing/component-test-providers';

describe('RelatoriosComponent', () => {
  let fixture: ComponentFixture<RelatoriosComponent>;
  let component: RelatoriosComponent;
  let relatorioService: jasmine.SpyObj<RelatorioService>;
  let exportacaoService: jasmine.SpyObj<ExportacaoDadosService>;
  let transacaoService: jasmine.SpyObj<TransacaoService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const resumoMensal = {
    totalReceitas: 1000,
    totalDespesas: 400,
    fluxoMes: 600,
    saldo: 600,
  };

  beforeEach(async () => {
    Chart.register(...registerables);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:mock');
    spyOn(URL, 'revokeObjectURL');

    relatorioService = jasmine.createSpyObj('RelatorioService', [
      'getRelatorioMensal', 'exportarMensalPdf', 'exportarIrPdf',
    ]);
    exportacaoService = jasmine.createSpyObj('ExportacaoDadosService', ['exportarTransacoesCsv']);
    transacaoService = jasmine.createSpyObj('TransacaoService', ['getTransacoesPorPeriodo']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    relatorioService.getRelatorioMensal.and.returnValue(of(resumoMensal as any));
    transacaoService.getTransacoesPorPeriodo.and.returnValue(
      of([{ id: 1, valor: 1000, tipoTransacao: 'RECEITA', dataTransacao: '2026-01-15', descricao: 'Receita teste' }] as any),
    );
    exportacaoService.exportarTransacoesCsv.and.returnValue(
      of({ blob: new Blob(['a,b\n1,2'], { type: 'text/csv' }), nomeArquivo: 't.csv' }),
    );
    relatorioService.exportarMensalPdf.and.returnValue(of(new Blob(['pdf'], { type: 'application/pdf' })));
    relatorioService.exportarIrPdf.and.returnValue(of(new Blob(['pdf'], { type: 'application/pdf' })));

    await TestBed.configureTestingModule({
      imports: [RelatoriosComponent, NoopAnimationsModule],
      providers: [
        ...whatsappParityTestProviders(),
        { provide: RelatorioService, useValue: relatorioService },
        { provide: ExportacaoDadosService, useValue: exportacaoService },
        { provide: TransacaoService, useValue: transacaoService },
        { provide: CartaoCreditoService, useValue: { getCartoes: () => of([]) } },
        { provide: ContaBancariaService, useValue: { listarContasAtivas: () => of([]) } },
        { provide: CategoriaService, useValue: { buscarPorUsuario: () => of([]) } },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    })
      .overrideProvider(RelatorioService, { useValue: relatorioService })
      .overrideProvider(TransacaoService, { useValue: transacaoService })
      .overrideProvider(ExportacaoDadosService, { useValue: exportacaoService })
      .compileComponents();

    fixture = TestBed.createComponent(RelatoriosComponent);
    component = fixture.componentInstance;
  });

  it('instancia e inicializa formulário de filtros', () => {
    fixture.detectChanges();
    expect(component.formFiltros).toBeTruthy();
    expect(component.formFiltros.get('periodo')?.value).toBe('mesAtual');
  });

  it('entra em loading e carrega resposta da API', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    expect(transacaoService.getTransacoesPorPeriodo).toHaveBeenCalled();
    expect(relatorioService.getRelatorioMensal).toHaveBeenCalled();
    expect(component.carregando).toBeFalse();
    expect(component.dadosCarregados).toBeTrue();
    expect(component.resumo.totalReceitas).toBe(1000);
  });

  it('estado vazio sem transações', async () => {
    transacaoService.getTransacoesPorPeriodo.and.returnValue(of([]));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.transacoes).toEqual([]);
    expect(component.resumo.totalTransacoes).toBe(0);
  });

  it('trata erro ao gerar relatório', async () => {
    transacaoService.getTransacoesPorPeriodo.and.returnValue(throwError(() => new Error('fail')));
    const openSpy = spyOn((component as any).snackBar, 'open').and.callThrough();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.carregando).toBeFalse();
    expect(openSpy).toHaveBeenCalled();
  });

  it('monta quatro gráficos após dados', () => {
    transacaoService.getTransacoesPorPeriodo.and.returnValue(
      of([{ id: 1, valor: 50, tipoTransacao: 'DESPESA', dataTransacao: '2026-01-15', descricao: 'x' }] as any),
    );
    fixture.detectChanges();
    expect(component.pizzaData.datasets.length).toBeGreaterThan(0);
    expect(component.linhaData.datasets.length).toBeGreaterThan(0);
    expect(component.barrasData.datasets.length).toBeGreaterThan(0);
    expect(component.roscaData.datasets.length).toBeGreaterThan(0);
  });

  it('atualiza gráficos ao mudar filtro de tipo', () => {
    fixture.detectChanges();
    const antes = component.pizzaData.labels?.length ?? 0;
    component.formFiltros.get('tipoTransacao')?.setValue('DESPESA');
    expect(component.pizzaData).toBeTruthy();
    expect(component.pizzaData.labels?.length ?? 0).toBeGreaterThanOrEqual(antes);
  });

  it('exporta CSV com sucesso', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    component.periodoAtual = {
      dataInicio: new Date(2026, 0, 1),
      dataFim: new Date(2026, 0, 31),
      mes: 1,
      ano: 2026,
    };
    const openSpy = spyOn((component as any).snackBar, 'open').and.callThrough();
    component.baixarCsv();
    await fixture.whenStable();
    expect(exportacaoService.exportarTransacoesCsv).toHaveBeenCalled();
    expect(component.exportandoCsv).toBeFalse();
    expect(openSpy).toHaveBeenCalled();
  });

  it('bloqueia exportação CSV durante download', () => {
    fixture.detectChanges();
    component.exportandoCsv = true;
    component.baixarCsv();
    expect(exportacaoService.exportarTransacoesCsv).not.toHaveBeenCalled();
  });

  it('exporta PDF mensal', () => {
    fixture.detectChanges();
    component.baixarPdfMensal();
    expect(relatorioService.exportarMensalPdf).toHaveBeenCalled();
    expect(component.exportandoPdfMensal).toBeFalse();
  });

  it('mensagem de falha na exportação CSV', async () => {
    exportacaoService.exportarTransacoesCsv.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    await fixture.whenStable();
    component.periodoAtual = {
      dataInicio: new Date(2026, 0, 1),
      dataFim: new Date(2026, 0, 31),
      mes: 1,
      ano: 2026,
    };
    const openSpy = spyOn((component as any).snackBar, 'open').and.callThrough();
    component.baixarCsv();
    await fixture.whenStable();
    expect(openSpy).toHaveBeenCalledWith('Erro ao exportar CSV.', 'Fechar', jasmine.any(Object));
  });
});
