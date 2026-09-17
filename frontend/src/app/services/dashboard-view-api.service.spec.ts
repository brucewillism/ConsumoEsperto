import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DashboardViewApiService } from './dashboard-view-api.service';
import { DASHBOARD_VIEW_STORAGE_KEY, gravarVisaoDashboard, lerVisaoDashboard } from '../models/dashboard-view.model';
import { environment } from '../../environments/environment';

describe('DashboardViewApiService', () => {
  let service: DashboardViewApiService;
  let http: HttpTestingController;
  const mem: Record<string, string> = {};
  const fakeStorage = {
    getItem: (k: string) => mem[k] ?? null,
    setItem: (k: string, v: string) => {
      mem[k] = v;
    },
  } as Storage;

  beforeEach(() => {
    for (const k of Object.keys(mem)) {
      delete mem[k];
    }
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(DashboardViewApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('padrão é Visão Mensal quando não há escolha', () => {
    expect(lerVisaoDashboard(fakeStorage)).toBe('MONTHLY');
  });

  it('persiste a última escolha', () => {
    gravarVisaoDashboard('GENERAL', fakeStorage);
    expect(lerVisaoDashboard(fakeStorage)).toBe('GENERAL');
    expect(mem[DASHBOARD_VIEW_STORAGE_KEY]).toBe('GENERAL');
  });

  it('GET recarrega a visão pedida no backend', () => {
    service.obter('MONTHLY').subscribe((dto) => {
      expect(dto.viewMode).toBe('MONTHLY');
    });
    const req = http.expectOne(`${environment.apiUrl}/dashboard?view=MONTHLY`);
    expect(req.request.method).toBe('GET');
    req.flush({ viewMode: 'MONTHLY', periodo: '2026-09', cards: { itens: [] } });
  });

  it('troca para Geral dispara outro GET', () => {
    service.obter('GENERAL').subscribe((dto) => {
      expect(dto.viewMode).toBe('GENERAL');
      expect(dto.cards?.itens?.[0]?.titulo).toBe('Dívida total');
    });
    const req = http.expectOne(`${environment.apiUrl}/dashboard?view=GENERAL`);
    req.flush({ viewMode: 'GENERAL', periodo: '2026-09', cards: { itens: [{ titulo: 'Dívida total', valor: 10 }] } });
  });

  it('preferência persistida no servidor vence o localStorage', () => {
    spyOn(localStorage, 'getItem').and.returnValue('MONTHLY');
    const setSpy = spyOn(localStorage, 'setItem');
    service.aplicarPreferenciaServidor().subscribe((mode) => {
      expect(mode).toBe('GENERAL');
    });
    const req = http.expectOne(`${environment.apiUrl}/dashboard/preferencia`);
    req.flush({ viewMode: 'GENERAL', persistida: true });
    expect(setSpy).toHaveBeenCalledWith(DASHBOARD_VIEW_STORAGE_KEY, 'GENERAL');
  });

  it('sem preferência persistida mantém a escolha local', () => {
    spyOn(localStorage, 'getItem').and.returnValue('GENERAL');
    service.aplicarPreferenciaServidor().subscribe((mode) => {
      expect(mode).toBe('GENERAL');
    });
    const req = http.expectOne(`${environment.apiUrl}/dashboard/preferencia`);
    req.flush({ viewMode: 'MONTHLY', persistida: false });
  });
});
