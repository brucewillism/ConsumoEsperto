import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AgendamentosComponent } from './agendamentos.component';
import { AgendamentoPagamentoService } from '../../services/agendamento-pagamento.service';
import { ContaBancariaService } from '../../services/conta-bancaria.service';
import { CartaoCreditoService } from '../../services/cartao-credito.service';
import { CategoriaService } from '../../services/categoria.service';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { whatsappParityTestProviders } from '../../testing/component-test-providers';

describe('AgendamentosComponent', () => {
  let fixture: ComponentFixture<AgendamentosComponent>;
  let component: AgendamentosComponent;
  let agService: jasmine.SpyObj<AgendamentoPagamentoService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const item = {
    id: 1,
    beneficiario: 'Aluguel',
    valor: 1200,
    status: 'AGENDADO' as const,
    contaDebitoId: 10,
    dataVencimento: '2026-08-01',
    recorrencia: 'UNICA' as const,
  };

  beforeEach(async () => {
    spyOn(window, 'confirm').and.returnValue(true);

    agService = jasmine.createSpyObj('AgendamentoPagamentoService', [
      'listar', 'historico', 'criar', 'atualizar', 'pausar', 'ativar', 'executar', 'cancelar',
    ]);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    agService.listar.and.returnValue(of([item]));
    agService.historico.and.returnValue(of([{ ...item, status: 'PAGO' as const }]));
    agService.criar.and.returnValue(of(item));
    agService.atualizar.and.returnValue(of(item));
    agService.pausar.and.returnValue(of({ ...item, status: 'PAUSADO' as const }));
    agService.ativar.and.returnValue(of({ ...item, status: 'AGENDADO' as const }));
    agService.executar.and.returnValue(of({ ...item, status: 'PAGO' as const }));
    agService.cancelar.and.returnValue(of({ ...item, status: 'CANCELADO' as const }));

    await TestBed.configureTestingModule({
      imports: [AgendamentosComponent, NoopAnimationsModule],
      providers: [
        ...whatsappParityTestProviders(),
        { provide: AgendamentoPagamentoService, useValue: agService },
        { provide: ContaBancariaService, useValue: { listarContasAtivas: () => of([{ id: 10, nome: 'Conta' }]) } },
        { provide: CartaoCreditoService, useValue: { getCartoes: () => of([]) } },
        { provide: CategoriaService, useValue: { buscarPorUsuario: () => of([]) } },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    })
      .overrideProvider(AgendamentoPagamentoService, { useValue: agService })
      .compileComponents();

    fixture = TestBed.createComponent(AgendamentosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lista agendamentos', () => {
    expect(component.lista.length).toBe(1);
    expect(agService.listar).toHaveBeenCalled();
  });

  it('filtra por busca', () => {
    component.busca = 'aluguel';
    expect(component.listaFiltrada.length).toBe(1);
    component.busca = 'inexistente';
    expect(component.listaFiltrada.length).toBe(0);
  });

  it('carrega histórico na aba', () => {
    component.onTabChange(1);
    expect(agService.historico).toHaveBeenCalled();
    expect(component.historico.length).toBe(1);
  });

  it('pausa agendamento', () => {
    component.pausar(item);
    expect(agService.pausar).toHaveBeenCalledWith(1);
  });

  it('ativa agendamento pausado', () => {
    component.ativar({ ...item, status: 'PAUSADO' });
    expect(agService.ativar).toHaveBeenCalledWith(1);
  });

  it('executa manualmente', () => {
    component.executar(item);
    expect(agService.executar).toHaveBeenCalledWith(1);
  });

  it('cancela com confirmação implícita via método', () => {
    component.cancelar(item);
    expect(agService.cancelar).toHaveBeenCalledWith(1);
  });

  it('impede duplo clique ao salvar', () => {
    component.contas = [{ id: 10, nome: 'Conta' } as any];
    component.form = {
      contaDebitoId: 10, beneficiario: 'Teste', valor: 50,
      dataVencimento: '2026-08-01', recorrencia: 'UNICA',
    };
    component.salvando = true;
    component.salvar();
    expect(agService.criar).not.toHaveBeenCalled();
  });

  it('cria novo agendamento', () => {
    component.contas = [{ id: 10, nome: 'Conta' } as any];
    component.form = {
      contaDebitoId: 10, beneficiario: 'Novo', valor: 99,
      dataVencimento: '2026-08-01', recorrencia: 'UNICA',
    };
    component.salvar();
    expect(agService.criar).toHaveBeenCalled();
  });

  it('edita agendamento existente', () => {
    component.contas = [{ id: 10, nome: 'Conta' } as any];
    component.editando = item;
    component.form = {
      contaDebitoId: 10, beneficiario: 'Editado', valor: 1300,
      dataVencimento: '2026-08-01', recorrencia: 'UNICA',
    };
    component.salvar();
    expect(agService.atualizar).toHaveBeenCalledWith(1, jasmine.any(Object));
  });

  it('trata erro de carregamento', async () => {
    agService.listar.and.returnValue(throwError(() => ({ status: 500 })));
    const openSpy = spyOn((component as any).snackBar, 'open').and.callThrough();
    component.carregarTudo();
    await fixture.whenStable();
    expect(component.erroCarregamento).toBeTrue();
    expect(openSpy).toHaveBeenCalled();
  });

  it('estado vazio quando lista retorna vazia', () => {
    agService.listar.and.returnValue(of([]));
    component.carregarTudo();
    expect(component.lista).toEqual([]);
  });
});
