import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface IaGroqPayload {
  apiKey?: string;
  baseUrl?: string;
  modelText?: string;
  modelVision?: string;
  whisperModel?: string;
}

export interface IaOpenaiPayload {
  apiKey?: string;
  baseUrl?: string;
  model?: string;
  whisperModel?: string;
}

export interface IaOllamaPayload {
  baseUrl?: string;
  model?: string;
}

export interface IaConfigPayload {
  providerOrder?: string[];
  /** Nome da instância Evolution (webhook multitenant). */
  evolutionInstanceName?: string;
  /** E.164 do dono do bot (trava webhook). */
  whatsappOwnerPhone?: string;
  groq?: IaGroqPayload;
  openai?: IaOpenaiPayload;
  ollama?: IaOllamaPayload;
}

export interface IaConfigStatus {
  providerOrder: string[];
  groqConfigured: boolean;
  openaiConfigured: boolean;
  ollamaConfigured: boolean;
  groqBaseUrl: string;
  groqModelText: string;
  groqModelVision: string;
  groqWhisperModel: string;
  openaiBaseUrl: string;
  openaiModel: string;
  openaiWhisperModel: string;
  ollamaBaseUrl: string;
  ollamaModel: string;
  whatsappOwnerPhone: string;
  evolutionInstanceName?: string;
  groqApiKeyMasked?: string;
  openaiApiKeyMasked?: string;
}

/**
 * Configuração de IA por usuário (PostgreSQL). Endpoint: {@code /api/config/ia}.
 */
@Injectable({
  providedIn: 'root'
})
export class AiConfigService {
  private readonly url = `${environment.apiUrl}/config/ia`;

  constructor(private http: HttpClient) {}

  get(): Observable<IaConfigStatus> {
    return this.http.get<IaConfigStatus>(this.url);
  }

  save(payload: IaConfigPayload): Observable<unknown> {
    return this.http.post(this.url, payload);
  }
}
