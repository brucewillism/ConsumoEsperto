import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type EdithState = 'DISABLED' | 'AVAILABLE' | 'UNAVAILABLE';

export interface EdithStatus {
  enabled: boolean;
  state: EdithState;
}

export interface EdithConversation {
  conversationId: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface EdithMessageResponse {
  conversationId: string;
  messageId: string;
  taskId: string;
  requestId: string;
  clientRequestId: string;
  status: string;
}

export interface EdithSsePayload {
  status: string;
  data: Record<string, unknown>;
}

@Injectable({ providedIn: 'root' })
export class EdithService {
  private readonly apiUrl = `${environment.apiUrl}/edith`;

  constructor(private http: HttpClient) {}

  status(): Observable<EdithStatus> {
    return this.http.get<EdithStatus>(`${this.apiUrl}/status`);
  }

  createConversation(): Observable<EdithConversation> {
    return this.http.post<EdithConversation>(`${this.apiUrl}/conversations`, {});
  }

  listConversations(): Observable<EdithConversation[]> {
    return this.http.get<EdithConversation[]>(`${this.apiUrl}/conversations`);
  }

  sendMessage(
    conversationId: string,
    content: string,
    clientRequestId: string,
    sourceAction = 'consumo.chat'
  ): Observable<EdithMessageResponse> {
    const headers = new HttpHeaders({ 'Idempotency-Key': clientRequestId });
    return this.http.post<EdithMessageResponse>(
      `${this.apiUrl}/conversations/${conversationId}/messages`,
      { content, sourceAction, clientRequestId },
      { headers }
    );
  }

  subscribeTaskEvents(taskId: string, onEvent: (payload: EdithSsePayload) => void, onError?: (err: unknown) => void): () => void {
    const token = localStorage.getItem('token');
    const url = `${this.apiUrl}/tasks/${taskId}/events`;
    const controller = new AbortController();

    fetch(url, {
      method: 'GET',
      headers: {
        Authorization: token ? `Bearer ${token}` : '',
        Accept: 'text/event-stream',
      },
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok || !response.body) {
          throw new Error(`SSE HTTP ${response.status}`);
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const parts = buffer.split('\n\n');
          buffer = parts.pop() ?? '';
          for (const chunk of parts) {
            const dataLine = chunk.split('\n').find((l) => l.startsWith('data:'));
            if (!dataLine) continue;
            const json = dataLine.replace(/^data:\s*/, '');
            try {
              onEvent(JSON.parse(json) as EdithSsePayload);
            } catch {
              // ignora chunk inválido
            }
          }
        }
      })
      .catch((err) => onError?.(err));

    return () => controller.abort();
  }
}
