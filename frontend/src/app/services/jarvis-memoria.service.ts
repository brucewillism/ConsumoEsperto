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
}

@Injectable({ providedIn: 'root' })
export class JarvisMemoriaService {
  private readonly base = `${environment.apiUrl}/jarvis/memoria`;

  constructor(private http: HttpClient) {}

  timeline(limite = 40): Observable<JarvisMemoriaTimelineItem[]> {
    const p = new HttpParams().set('limite', String(limite));
    return this.http.get<JarvisMemoriaTimelineItem[]>(`${this.base}/timeline`, { params: p });
  }
}
