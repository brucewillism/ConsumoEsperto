const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

export const ECO_HEADERS = {
  TRACE_ID: 'X-Eco-Trace-Id',
  SPAN_ID: 'X-Eco-Span-Id',
  PARENT_SPAN_ID: 'X-Eco-Parent-Span-Id',
  USER_ID: 'X-Eco-User-Id',
  APP_ORIGIN: 'X-Eco-App-Origin',
  APP_CURRENT: 'X-Eco-App-Current',
  MODE: 'X-Eco-Mode',
  DEADLINE: 'X-Eco-Deadline',
  SENSITIVITY: 'X-Eco-Sensitivity',
  CONVERSATION_ID: 'X-Eco-Conversation-Id',
  TASK_ID: 'X-Eco-Task-Id',
} as const;

export type EcoMode = 'INTERACTIVE' | 'BALANCED' | 'DEEP';

function encodeTime(time: number, out: string[]): void {
  for (let i = 9; i >= 0; i--) {
    out[i] = CROCKFORD[time & 31];
    time = Math.floor(time / 32);
  }
}

export function ecoUlid(): string {
  const out = new Array<string>(26);
  encodeTime(Date.now(), out);
  const bytes = new Uint8Array(10);
  crypto.getRandomValues(bytes);
  let acc = 0;
  let bits = 0;
  let idx = 10;
  for (const b of bytes) {
    acc = (acc << 8) | b;
    bits += 8;
    while (bits >= 5 && idx < 26) {
      bits -= 5;
      out[idx++] = CROCKFORD[(acc >>> bits) & 31];
    }
  }
  if (idx < 26 && bits > 0) {
    out[idx] = CROCKFORD[(acc << (5 - bits)) & 31];
  }
  return out.join('');
}

export function ecoTraceId(): string {
  return `tr_${ecoUlid()}`;
}

export function ecoSpanId(): string {
  return `sp_${ecoUlid()}`;
}

const MODE_MS: Record<EcoMode, number> = {
  INTERACTIVE: 2000,
  BALANCED: 8000,
  DEEP: 60000,
};

export function ecoDeadline(mode: EcoMode): string {
  return new Date(Date.now() + MODE_MS[mode]).toISOString();
}

export function ecoEdgeHeaders(mode: EcoMode, sensitivity: 'INTERNAL' | 'FINANCIAL' = 'FINANCIAL'): Record<string, string> {
  return {
    [ECO_HEADERS.TRACE_ID]: ecoTraceId(),
    [ECO_HEADERS.SPAN_ID]: ecoSpanId(),
    [ECO_HEADERS.APP_ORIGIN]: 'jarvis',
    [ECO_HEADERS.APP_CURRENT]: 'consumo-esperto',
    [ECO_HEADERS.MODE]: mode,
    [ECO_HEADERS.DEADLINE]: ecoDeadline(mode),
    [ECO_HEADERS.SENSITIVITY]: sensitivity,
  };
}
