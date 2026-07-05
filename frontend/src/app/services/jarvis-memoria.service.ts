import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface JarvisMemoriaTimelineItem {
  id?: number;
  contexto?: string;
  categoriaOrigem?: string;
  dataRegistro?: string;
  temEmbedding?: boolean;
  tipo?: string;
  status?: string;
  confianca?: number;
  contadorReforco?: number;
}

@Injectable({ providedIn: 'root' })
export class JarvisMemoriaService {
  private readonly base = `${environment.apiUrl}/jarvis/memoria`;

  constructor(private http: HttpClient) {}

  timeline(limite = 40): Observable<JarvisMemoriaTimelineItem[]> {
    const p = new HttpParams().set('limite', String(limite));
    return this.http.get<JarvisMemoriaTimelineItem[]>(`${this.base}/timeline`, { params: p });
  }

  /** Insights mais relevantes para o card compacto do dashboard. */
  insights(limite = 3): Observable<JarvisMemoriaTimelineItem[]> {
    const p = new HttpParams().set('limite', String(limite));
    return this.http.get<JarvisMemoriaTimelineItem[]>(`${this.base}/insights`, { params: p });
  }

  /** Marca a memória como refutada (sai do RAG e do painel). */
  refutar(id: number): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/refutar`, {});
  }
}
