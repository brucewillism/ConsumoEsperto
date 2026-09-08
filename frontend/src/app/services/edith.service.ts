import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CONSUMO_APPLICATION_ID } from '../shared/jarvis-chat/jarvis-chat.util';
import { ecoEdgeHeaders } from '../shared/eco/eco-envelope';

export type EdithState = 'DISABLED' | 'AVAILABLE' | 'UNAVAILABLE';

export interface EdithStatus {
  enabled: boolean;
  state: EdithState;
  assistant?: string;
  fallbackEnabled?: boolean;
  circuit?: string;
  applicationId?: string;
}

export interface EdithAdminStatus extends EdithStatus {
  envDefaultEnabled: boolean;
  configured: boolean;
  baseUrlConfigured: boolean;
  apiKeyConfigured: boolean;
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

export interface EdithMessageContext {
  screen?: string;
  entityType?: string;
  entityId?: string;
  applicationId?: string;
  traceId?: string;
}

@Injectable({ providedIn: 'root' })
export class EdithService {
  private readonly apiUrl = `${environment.apiUrl}/edith`;

  constructor(private http: HttpClient) {}

  status(): Observable<EdithStatus> {
    return this.http.get<EdithStatus>(`${this.apiUrl}/status`);
  }

  adminStatus(): Observable<EdithAdminStatus> {
    return this.http.get<EdithAdminStatus>(`${environment.apiUrl}/admin/edith`);
  }

  setEnabled(enabled: boolean): Observable<EdithAdminStatus> {
    return this.http.patch<EdithAdminStatus>(`${environment.apiUrl}/admin/edith`, { enabled });
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
    sourceAction = 'consumo.chat',
    context?: EdithMessageContext
  ): Observable<EdithMessageResponse> {
    const headers = new HttpHeaders({ 'Idempotency-Key': clientRequestId });
    return this.http.post<EdithMessageResponse>(
      `${this.apiUrl}/conversations/${conversationId}/messages`,
      {
        content,
        sourceAction,
        clientRequestId,
        applicationId: context?.applicationId || CONSUMO_APPLICATION_ID,
        screen: context?.screen || '',
        entityType: context?.entityType || '',
        entityId: context?.entityId || '',
        traceId: context?.traceId || '',
      },
      { headers }
    );
  }

  subscribeTaskEvents(
    taskId: string,
    onEvent: (payload: EdithSsePayload) => void,
    onError?: (err: unknown) => void,
    lastEventId?: string
  ): () => void {
    const token = localStorage.getItem('token');
    const url = `${this.apiUrl}/tasks/${taskId}/events`;
    let aborted = false;
    let currentController: AbortController | null = null;
    let resumeId = lastEventId ?? '';
    let reconnects = 0;
    let terminal = false;

    const parseChunk = (chunk: string): EdithSsePayload | null => {
      let dataJson = '';
      for (const line of chunk.split('\n')) {
        if (line.startsWith('id:')) {
          resumeId = line.slice(3).trim();
        } else if (line.startsWith('data:')) {
          dataJson = line.replace(/^data:\s*/, '');
        }
      }
      if (!dataJson) {
        return null;
      }
      try {
        return JSON.parse(dataJson) as EdithSsePayload;
      } catch {
        return null;
      }
    };

    const connect = () => {
      if (aborted || terminal) {
        return;
      }
      currentController = new AbortController();
      const headers: Record<string, string> = {
        Authorization: token ? `Bearer ${token}` : '',
        Accept: 'text/event-stream',
        ...ecoEdgeHeaders('DEEP'),
      };
      if (resumeId) {
        headers['Last-Event-ID'] = resumeId;
      }

      fetch(url, {
        method: 'GET',
        headers,
        signal: currentController.signal,
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            throw new Error(`SSE HTTP ${response.status}`);
          }
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          while (!aborted) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const parts = buffer.split('\n\n');
            buffer = parts.pop() ?? '';
            for (const chunk of parts) {
              const payload = parseChunk(chunk);
              if (!payload) continue;
              if (payload.status === 'COMPLETED' || payload.status === 'FAILED') {
                terminal = true;
              }
              onEvent(payload);
            }
          }
          if (!aborted && !terminal && reconnects < 3) {
            reconnects += 1;
            setTimeout(connect, 400 * reconnects);
          }
        })
        .catch((err) => {
          if (aborted) {
            return;
          }
          if (!terminal && reconnects < 3) {
            reconnects += 1;
            setTimeout(connect, 400 * reconnects);
            return;
          }
          onError?.(err);
        });
    };

    connect();
    return () => {
      aborted = true;
      currentController?.abort();
    };
  }
}
