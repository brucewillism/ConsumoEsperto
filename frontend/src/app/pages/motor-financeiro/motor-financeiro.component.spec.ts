import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MotorFinanceiroComponent } from './motor-financeiro.component';
import { MotorFinanceiroService } from '../../services/motor-financeiro.service';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { whatsappParityTestProviders } from '../../testing/component-test-providers';

describe('MotorFinanceiroComponent', () => {
  let fixture: ComponentFixture<MotorFinanceiroComponent>;
  let component: MotorFinanceiroComponent;
  let motorService: jasmine.SpyObj<MotorFinanceiroService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const dadosCompletos = {
    scoreExplicavel: { scoreTotal: 72, componentes: [{ nome: 'A', valor: 10 }] },
    forecastInteligente: { alertas: [{ tipo: 'LIMITE', mensagem: 'Alerta' }] },
    patrimonioLiquido: 1500,
  };

  beforeEach(async () => {
    motorService = jasmine.createSpyObj('MotorFinanceiroService', ['obter']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [MotorFinanceiroComponent, NoopAnimationsModule],
      providers: [
        ...whatsappParityTestProviders(),
        { provide: MotorFinanceiroService, useValue: motorService },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    })
      .overrideProvider(MotorFinanceiroService, { useValue: motorService })
      .compileComponents();

    fixture = TestBed.createComponent(MotorFinanceiroComponent);
    component = fixture.componentInstance;
  });

  it('inicia em loading e carrega resposta completa', () => {
    motorService.obter.and.returnValue(of(dadosCompletos as any));
    fixture.detectChanges();
    expect(motorService.obter).toHaveBeenCalled();
    expect(component.dados).toBeTruthy();
    expect(component.carregando).toBeFalse();
  });

  it('aceita resposta parcial', () => {
    motorService.obter.and.returnValue(of({ scoreExplicavel: { scoreTotal: 50, componentes: [] } } as any));
    fixture.detectChanges();
    expect(component.dados?.scoreExplicavel?.scoreTotal).toBe(50);
  });

  it('estado vazio quando API retorna objeto mínimo', () => {
    motorService.obter.and.returnValue(of({} as any));
    fixture.detectChanges();
    expect(component.dados).toBeTruthy();
    expect(component.carregando).toBeFalse();
  });

  it('trata erro de API', () => {
    motorService.obter.and.returnValue(throwError(() => new Error('fail')));
    const openSpy = spyOn((component as any).snackBar, 'open').and.callThrough();
    fixture.detectChanges();
    expect(component.carregando).toBeFalse();
    expect(component.dados).toBeNull();
    expect(openSpy).toHaveBeenCalled();
  });

  it('atualiza ao alternar narrativa', () => {
    motorService.obter.and.returnValue(of(dadosCompletos as any));
    fixture.detectChanges();
    motorService.obter.calls.reset();
    component.alternarNarrativa();
    expect(motorService.obter).toHaveBeenCalledWith(true);
  });

  it('formata BRL sem NaN, Infinity ou undefined no texto', () => {
    motorService.obter.and.returnValue(of(dadosCompletos as any));
    fixture.detectChanges();
    const html = fixture.nativeElement.textContent ?? '';
    expect(component.brl(100)).toContain('R$');
    expect(component.brl(null)).not.toContain('NaN');
    expect(component.brl(undefined)).not.toContain('NaN');
    expect(component.brl(Number.POSITIVE_INFINITY)).not.toContain('Infinity');
    expect(html).not.toContain('undefined');
    expect(html).not.toContain('NaN');
  });
});
