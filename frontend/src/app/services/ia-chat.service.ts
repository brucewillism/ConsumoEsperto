import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class IaChatService {
  private readonly apiUrl = `${environment.apiUrl}/ia-chat`;

  constructor(private http: HttpClient) {}

  perguntar(mensagem: string): Observable<{ resposta: string }> {
    return this.http.post<{ resposta: string }>(this.apiUrl, { mensagem });
  }
}
