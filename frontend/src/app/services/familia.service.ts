import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Orcamento } from './orcamento.service';

export interface GrupoFamiliarMembro {
  id: number;
  usuarioId?: number | null;
  nome: string;
  email?: string;
  whatsapp?: string;
  status: 'PENDENTE' | 'ACEITO' | 'RECUSADO' | 'CANCELADO';
  eu: boolean;
}

export interface GrupoFamiliar {
  id: number;
  nome: string;
  membros: GrupoFamiliarMembro[];
}

@Injectable({ providedIn: 'root' })
export class FamiliaService {
  private readonly base = `${environment.apiUrl}/familia`;

  constructor(private http: HttpClient) {}

  meuGrupo(): Observable<GrupoFamiliar> {
    return this.http.get<GrupoFamiliar>(this.base);
  }

  criar(nome: string): Observable<GrupoFamiliar> {
    return this.http.post<GrupoFamiliar>(this.base, { nome });
  }

  convidar(email: string, whatsapp: string): Observable<GrupoFamiliar> {
    return this.http.post<GrupoFamiliar>(`${this.base}/convites`, { email, whatsapp });
  }

  convites(): Observable<GrupoFamiliarMembro[]> {
    return this.http.get<GrupoFamiliarMembro[]>(`${this.base}/convites`);
  }

  responderConvite(membroId: number, aceitar: boolean): Observable<GrupoFamiliar> {
    return this.http.post<GrupoFamiliar>(`${this.base}/convites/${membroId}/responder`, { aceitar });
  }

  orcamentosCompartilhados(mes?: number, ano?: number): Observable<Orcamento[]> {
    const params: Record<string, string> = {};
    if (mes) params['mes'] = String(mes);
    if (ano) params['ano'] = String(ano);
    return this.http.get<Orcamento[]>(`${this.base}/orcamentos-compartilhados`, { params });
  }
}
