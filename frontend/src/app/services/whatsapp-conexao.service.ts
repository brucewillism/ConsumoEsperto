import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface WhatsappConexaoTransicao {
  estadoAnterior?: string | null;
  estadoNovo?: string;
  detalhe?: string | null;
  origem?: string;
  verificadoEm?: string;
}

export interface WhatsappConexaoStatus {
  estado?: string;
  detalhe?: string | null;
  instanceName?: string | null;
  estadoDesde?: string | null;
  verificadoEm?: string | null;
  minutosNoEstado?: number;
  tentativasReconexao?: number;
  proximaTentativaEm?: string | null;
  ultimaTentativaEm?: string | null;
  ultimoResultadoReconexao?: string | null;
  autoReconexaoEsgotada?: boolean;
  alertaDesconexaoEnviado?: boolean;
  transicoes?: WhatsappConexaoTransicao[];
}

export interface WhatsappPairingCodeResponse {
  pairingCode?: string | null;
  validadeSegundos?: number;
  numeroNormalizado?: string;
  alreadyConnected?: boolean;
  evolutionWarning?: string | null;
  instanceName?: string | null;
}

@Injectable({ providedIn: 'root' })
export class WhatsappConexaoService {
  private readonly base = `${environment.apiUrl}/whatsapp/conexao`;

  constructor(private http: HttpClient) {}

  getStatus(): Observable<WhatsappConexaoStatus> {
    return this.http.get<WhatsappConexaoStatus>(`${this.base}/status`);
  }

  reconectarAgora(): Observable<{ status?: string; conexao?: WhatsappConexaoStatus }> {
    return this.http.post<{ status?: string; conexao?: WhatsappConexaoStatus }>(
      `${this.base}/reconectar`,
      {}
    );
  }

  gerarPairingCode(numero: string): Observable<WhatsappPairingCodeResponse> {
    return this.http.post<WhatsappPairingCodeResponse>(`${this.base}/pairing-code`, { numero });
  }
}
