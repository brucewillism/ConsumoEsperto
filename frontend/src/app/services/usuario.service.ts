import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Usuario } from '../models/usuario.model';

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
