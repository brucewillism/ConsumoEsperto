import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface InboxNotification {
  key: string;
  serverId?: number;
  message: string;
  icon: string;
  time: string;
  read: boolean;
}

interface NotificacaoApi {
  id: number;
  titulo: string;
  mensagem: string;
  tipo?: string;
  lida?: boolean;
  dataCriacao?: string;
}

const SYNTH_PREFIX = 'syn-';
const LS_SYNTH_READ = 'ce_notif_synthetic_read';

function iconForTipo(tipo?: string): string {
  switch ((tipo || '').toUpperCase()) {
    case 'AVISO':
      return 'fas fa-exclamation-triangle';
    case 'ALERTA':
      return 'fas fa-exclamation-circle';
    case 'SUCESSO':
      return 'fas fa-check-circle';
    case 'TESTE':
      return 'fas fa-flask';
    default:
      return 'fas fa-bell';
  }
}

@Injectable({ providedIn: 'root' })
export class NotificacaoInboxService {
  private readonly apiBase = `${environment.apiUrl}/notificacoes`;
  constructor(private http: HttpClient) {}

  loadInbox(): Observable<InboxNotification[]> {
    return this.http.get<NotificacaoApi[]>(`${this.apiBase}/todas`).pipe(
      catchError(() => of([] as NotificacaoApi[])),
      map((notif) => this.merge(notif || []))
    );
  }

  marcarLidaServidor(id: number): Observable<void> {
    return this.http
      .put<{ sucesso?: boolean }>(`${this.apiBase}/${id}/marcar-lida`, {})
      .pipe(map(() => undefined), catchError(() => of(undefined)));
  }

  marcarTodasLidasServidor(): Observable<void> {
    return this.http
      .put<{ sucesso?: boolean }>(`${this.apiBase}/marcar-todas-lidas`, {})
      .pipe(map(() => undefined), catchError(() => of(undefined)));
  }

  markSyntheticKeysRead(keys: string[]): void {
    const cur = this.readSyntheticKeys();
    keys.forEach((k) => cur.add(k));
    localStorage.setItem(LS_SYNTH_READ, JSON.stringify([...cur]));
  }

  clearSyntheticReadKeys(): void {
    localStorage.removeItem(LS_SYNTH_READ);
  }

  private readSyntheticKeys(): Set<string> {
    try {
      const raw = localStorage.getItem(LS_SYNTH_READ);
      const arr = raw ? (JSON.parse(raw) as string[]) : [];
      return new Set(arr);
    } catch {
      return new Set();
    }
  }

  private merge(rows: NotificacaoApi[]): InboxNotification[] {
    const fromDb: InboxNotification[] = rows.map((n) => ({
      key: `db-${n.id}`,
      serverId: n.id,
      message: [n.titulo, n.mensagem].filter(Boolean).join(' — '),
      icon: iconForTipo(n.tipo),
      time: this.formatRelative(n.dataCriacao),
      read: !!n.lida,
    }));

    return fromDb.sort((a, b) => {
      if (a.read !== b.read) return a.read ? 1 : -1;
      return 0;
    });
  }

  private formatRelative(iso?: string): string {
    if (!iso) return 'Recentemente';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return 'Recentemente';
    const diff = Date.now() - d.getTime();
    const m = Math.floor(diff / 60000);
    if (m < 1) return 'Agora';
    if (m < 60) return `${m} min atrás`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h} h atrás`;
    const days = Math.floor(h / 24);
    if (days < 7) return `${days} d atrás`;
    return d.toLocaleDateString('pt-BR');
  }
}
