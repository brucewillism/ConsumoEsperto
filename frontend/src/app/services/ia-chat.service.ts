import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CONSUMO_APPLICATION_ID } from '../shared/jarvis-chat/jarvis-chat.util';

export interface IaChatRequest {
  mensagem: string;
  capability?: string;
  screen?: string;
  entityType?: string;
  entityId?: string;
  applicationId?: string;
  traceId?: string;
  awaitCompletion?: string;
}

export interface IaChatResponse {
  resposta: string;
  mode?: string;
  assistant?: string;
  conversationId?: string;
  taskId?: string;
  traceId?: string;
}

@Injectable({ providedIn: 'root' })
export class IaChatService {
  private readonly apiUrl = `${environment.apiUrl}/ia-chat`;

  constructor(private http: HttpClient) {}

  perguntar(mensagem: string | IaChatRequest): Observable<IaChatResponse> {
    const req: IaChatRequest = typeof mensagem === 'string' ? { mensagem } : mensagem;
    return this.http.post<IaChatResponse>(this.apiUrl, {
      mensagem: req.mensagem,
      capability: req.capability || '',
      screen: req.screen || 'dashboard',
      entityType: req.entityType || '',
      entityId: req.entityId || '',
      applicationId: req.applicationId || CONSUMO_APPLICATION_ID,
      traceId: req.traceId || '',
      awaitCompletion: req.awaitCompletion || '',
    });
  }
}
