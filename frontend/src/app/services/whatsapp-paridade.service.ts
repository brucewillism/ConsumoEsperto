import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export type ParityCanal = 'BOTH' | 'APP_ONLY' | 'WHATSAPP_ONLY';

export interface WhatsappParityItem {
  id: string;
  titulo: string;
  rotaApp: string;
  menuApp: string;
  canal: ParityCanal;
  exemplosWhatsapp: string[];
  acoesApp: string[];
  nota?: string;
}

export interface WhatsappParidadeResponse {
  itens: WhatsappParityItem[];
  ajudaWhatsapp?: string;
}

@Injectable({ providedIn: 'root' })
export class WhatsappParidadeService {
  private readonly base = `${environment.apiUrl}/whatsapp/paridade`;

  private cache$?: Observable<WhatsappParidadeResponse>;

  constructor(private http: HttpClient) {}

  listarTudo(): Observable<WhatsappParidadeResponse> {
    if (!this.cache$) {
      this.cache$ = this.http.get<WhatsappParidadeResponse>(this.base).pipe(shareReplay(1));
    }
    return this.cache$;
  }

  listarPorRota(rota: string): Observable<WhatsappParidadeResponse> {
    const path = rota.startsWith('/') ? rota : `/${rota}`;
    return this.http.get<WhatsappParidadeResponse>(this.base, { params: { rota: path } });
  }

  invalidarCache(): void {
    this.cache$ = undefined;
  }
}
