import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface JarvisFeedbackBody {
  insightId: string;
  positivo: boolean;
  tipoAlvo: string;
  categoriaChave?: string;
}

@Injectable({ providedIn: 'root' })
export class JarvisFeedbackService {
  private readonly url = `${environment.apiUrl}/jarvis/feedback`;

  constructor(private http: HttpClient) {}

  enviar(body: JarvisFeedbackBody): Observable<void> {
    return this.http.post<void>(this.url, body);
  }
}
