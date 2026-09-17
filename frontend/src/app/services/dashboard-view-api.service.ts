import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import {
  DashboardViewMode,
  DashboardViewPayload,
  gravarVisaoDashboard,
  lerVisaoDashboard,
} from '../models/dashboard-view.model';

@Injectable({ providedIn: 'root' })
export class DashboardViewApiService {
  private readonly base = `${environment.apiUrl}/dashboard`;

  constructor(private http: HttpClient) {}

  obter(view: DashboardViewMode): Observable<DashboardViewPayload> {
    return this.http.get<DashboardViewPayload>(this.base, { params: { view } });
  }

  persistir(viewMode: DashboardViewMode): Observable<{ viewMode: string }> {
    gravarVisaoDashboard(viewMode);
    return this.http.put<{ viewMode: string }>(`${this.base}/preferencia`, { viewMode });
  }

  visaoInicial(): DashboardViewMode {
    return lerVisaoDashboard();
  }

  /**
   * Se o servidor já tem preferência, ela vence o localStorage (outro dispositivo).
   * Sem preferência persistida, mantém o padrão Mensal / última escolha local.
   */
  aplicarPreferenciaServidor(): Observable<DashboardViewMode> {
    const local = lerVisaoDashboard();
    return this.http.get<{ viewMode?: string; persistida?: boolean }>(`${this.base}/preferencia`).pipe(
      map((r) => {
        if (r?.persistida === true && (r.viewMode === 'GENERAL' || r.viewMode === 'MONTHLY')) {
          gravarVisaoDashboard(r.viewMode);
          return r.viewMode;
        }
        return local;
      }),
      catchError(() => of(local))
    );
  }
}
