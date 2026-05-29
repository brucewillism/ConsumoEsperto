import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { Usuario, PreferenciaTratamentoJarvis } from '../models/usuario.model';

/** Resposta de POST /usuarios/whatsapp/vincular. */
export interface VincularWhatsappResponse {
  status?: string;
  message?: string;
  whatsappNumero?: string;
  usuarioId?: number;
  evolutionInstanceName?: string;
  evolutionAlreadyConnected?: boolean;
  /** Sessão WhatsApp realmente ligada à Evolution (connectionState), não só número na BD. */
  evolutionWaConnected?: boolean;
  evolutionQrCodeDataUri?: string | null;
  evolutionPairingCode?: string | null;
  evolutionWarning?: string | null;
  evolutionHasAlternativePairingHints?: boolean;
  /** Utilizador pediu «Desligar» — a Evolution pode ainda reportar open em cache. */
  sessionMarkedDisconnected?: boolean;
  evolutionManagerUrl?: string;
}

/** POST /usuarios/whatsapp/evolution-pairing-refresh — mesmo formato de campos Evolution que vincular (sem dados de perfil). */
export type EvolutionPairingRefreshResponse = Pick<
  VincularWhatsappResponse,
  | 'evolutionInstanceName'
  | 'evolutionAlreadyConnected'
  | 'evolutionQrCodeDataUri'
  | 'evolutionPairingCode'
  | 'evolutionWarning'
  | 'evolutionHasAlternativePairingHints'
> & {
  status?: string;
  evolutionWaConnected?: boolean;
  sessionMarkedDisconnected?: boolean;
  evolutionManagerUrl?: string;
};

/** GET /usuarios/whatsapp/evolution-connection-status */
export interface EvolutionWhatsappConnectionStatusDTO {
  connected: boolean;
  evolutionWaConnected?: boolean;
  sessionMarkedDisconnected?: boolean;
  numeroCadastrado?: boolean;
  whatsappNumero?: string;
  instanceName?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UsuarioService {
  private apiUrl = `${environment.apiUrl}/usuarios`;

  constructor(private http: HttpClient) { }

  getUsuario(): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.apiUrl}/perfil`);
  }

  atualizarUsuario(usuario: Usuario): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.apiUrl}/perfil`, usuario);
  }

  /**
   * Persiste tratamento J.A.R.V.I.S. (novo PATCH). Se indisponível (404/redes/servidor antigo),
   * usa PATCH {@code preferencia-tratamento} — mesma lógica no backend.
   */
  patchPerfilJarvis(preferenciaTratamento: PreferenciaTratamentoJarvis | string): Observable<Usuario> {
    const codigo = String(preferenciaTratamento);
    return this.http
      .patch<Usuario>(`${this.apiUrl}/perfil-jarvis`, { tratamento: codigo })
      .pipe(
        catchError((err: HttpErrorResponse) => {
          const fallback =
            err.status === 0 ||
            err.status === 404 ||
            err.status === 405 ||
            err.status >= 502;
          if (fallback) {
            console.warn(
              `[UsuarioService] perfil-jarvis indisponível (HTTP ${err.status}); a usar preferencia-tratamento.`,
              err.url
            );
            return this.patchPreferenciaTratamento(codigo);
          }
          return throwError(() => err);
        })
      );
  }

  patchPreferenciaTratamento(preferenciaTratamento: string): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.apiUrl}/preferencia-tratamento`, {
      preferenciaTratamento,
    });
  }

  alterarSenha(senhaAtual: string, novaSenha: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/alterar-senha`, {
      senhaAtual,
      novaSenha
    });
  }

  deletarConta(): Observable<any> {
    return this.http.delete(`${this.apiUrl}/conta`);
  }

  getUsuarioPorId(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.apiUrl}/${id}`);
  }

  getUsuarios(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.apiUrl);
  }

  vincularWhatsapp(numero: string): Observable<VincularWhatsappResponse> {
    return this.http.post<VincularWhatsappResponse>(`${this.apiUrl}/whatsapp/vincular`, { numero });
  }

  /** Polling Evolution: instância em estado Connected/Open (pareamento feito). */
  getEvolutionWhatsappConnectionStatus(): Observable<EvolutionWhatsappConnectionStatusDTO> {
    return this.http.get<EvolutionWhatsappConnectionStatusDTO>(
      `${this.apiUrl}/whatsapp/evolution-connection-status`
    );
  }

  /** Re-pede dados de QR/pairing à Evolution sem alterar o número (polling no modal). */
  refreshEvolutionPairing(): Observable<EvolutionPairingRefreshResponse> {
    return this.http.post<EvolutionPairingRefreshResponse>(
      `${this.apiUrl}/whatsapp/evolution-pairing-refresh`,
      {}
    );
  }

  desvincularWhatsapp(): Observable<any> {
    return this.http.post(`${this.apiUrl}/whatsapp/desvincular`, {});
  }

  /** Logout na Evolution sem apagar o número na app. */
  desligarEvolutionWhatsapp(): Observable<EvolutionDesligarResponse> {
    return this.http.post<EvolutionDesligarResponse>(`${this.apiUrl}/whatsapp/evolution-desligar`, {});
  }
}

export interface EvolutionDesligarResponse {
  status?: string;
  message?: string;
  instanceName?: string;
  evolutionWaConnected?: boolean;
  sessionMarkedDisconnected?: boolean;
  connectionStateBefore?: string;
  connectionStateAfter?: string;
  logoutRequested?: boolean;
  instanceDeleted?: boolean;
  instanceRotated?: boolean;
  ghostInstanceDetected?: boolean;
  evolutionApiReportsOpen?: boolean;
  instanceRestarted?: boolean;
}
