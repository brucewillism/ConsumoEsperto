import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { Usuario, PreferenciaTratamentoJarvis } from '../models/usuario.model';

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

  vincularWhatsapp(numero: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/whatsapp/vincular`, { numero });
  }

  desvincularWhatsapp(): Observable<any> {
    return this.http.post(`${this.apiUrl}/whatsapp/desvincular`, {});
  }
}
