/** Parâmetros de filtro CSV (espelha ExportacaoTransacaoFiltro do backend). */
export interface CsvFiltroInput {
  dataInicio?: string;
  dataFim?: string;
  contaId?: number;
  cartaoId?: number;
  categoriaId?: number;
  tipoTransacao?: string;
  statusConferencia?: string;
  descricaoContem?: string;
}

const INVALID_STRINGS = new Set(['', 'undefined', 'null']);

function isFilled(value: unknown): boolean {
  if (value === undefined || value === null) return false;
  const s = String(value).trim();
  return s.length > 0 && !INVALID_STRINGS.has(s);
}

function positiveId(value: unknown): number | undefined {
  if (value === undefined || value === null || value === '') return undefined;
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

/** Monta query params enviando somente filtros preenchidos. */
export function montarParamsCsv(filtro: CsvFiltroInput = {}): Record<string, string> {
  const params: Record<string, string> = {};
  if (isFilled(filtro.dataInicio)) params['dataInicio'] = String(filtro.dataInicio).trim();
  if (isFilled(filtro.dataFim)) params['dataFim'] = String(filtro.dataFim).trim();
  const contaId = positiveId(filtro.contaId);
  if (contaId) params['contaId'] = String(contaId);
  const cartaoId = positiveId(filtro.cartaoId);
  if (cartaoId) params['cartaoId'] = String(cartaoId);
  const categoriaId = positiveId(filtro.categoriaId);
  if (categoriaId) params['categoriaId'] = String(categoriaId);
  if (isFilled(filtro.tipoTransacao)) params['tipoTransacao'] = String(filtro.tipoTransacao).trim();
  if (isFilled(filtro.statusConferencia)) params['statusConferencia'] = String(filtro.statusConferencia).trim();
  if (isFilled(filtro.descricaoContem)) params['descricaoContem'] = String(filtro.descricaoContem).trim();
  return params;
}

export function validarIntervaloDatas(dataInicio: string, dataFim: string): string | null {
  if (!isFilled(dataInicio) || !isFilled(dataFim)) return null;
  const ini = parseYmd(dataInicio);
  const fim = parseYmd(dataFim);
  if (!ini || !fim) return 'Datas inválidas.';
  if (ini > fim) return 'A data inicial não pode ser posterior à data final.';
  return null;
}

function parseYmd(ymd: string): Date | null {
  const m = String(ymd).trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) return null;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  if (Number.isNaN(d.getTime())) return null;
  d.setHours(0, 0, 0, 0);
  return d;
}

export function extrairNomeArquivoContentDisposition(header: string | null | undefined): string | null {
  if (!header || typeof header !== 'string') return null;
  const utf8 = header.match(/filename\*=UTF-8''([^;\s]+)/i);
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1]);
    } catch {
      return utf8[1];
    }
  }
  const quoted = header.match(/filename="([^"]+)"/i);
  if (quoted?.[1]) return quoted[1];
  const plain = header.match(/filename=([^;\s]+)/i);
  if (plain?.[1]) return plain[1].replace(/^["']|["']$/g, '');
  return null;
}

export function nomeArquivoCsvFallback(dataRef = new Date()): string {
  const y = dataRef.getFullYear();
  const m = String(dataRef.getMonth() + 1).padStart(2, '0');
  const d = String(dataRef.getDate()).padStart(2, '0');
  return `transacoes-${y}-${m}-${d}.csv`;
}

export function resolverNomeArquivoCsv(contentDisposition: string | null | undefined, dataRef = new Date()): string {
  return extrairNomeArquivoContentDisposition(contentDisposition) ?? nomeArquivoCsvFallback(dataRef);
}

export function blobCsvVazio(blob: Blob | null | undefined): boolean {
  return !blob || blob.size === 0;
}

export interface BlobDownloadDeps {
  createObjectURL?: (b: Blob) => string;
  revokeObjectURL?: (u: string) => void;
  document?: Pick<Document, 'createElement'>;
}

export function baixarBlobComRevogacao(blob: Blob, nome: string, deps: BlobDownloadDeps = {}): void {
  const create = deps.createObjectURL ?? URL.createObjectURL.bind(URL);
  const revoke = deps.revokeObjectURL ?? URL.revokeObjectURL.bind(URL);
  const doc = deps.document ?? document;
  const url = create(blob);
  try {
    const a = doc.createElement('a');
    a.href = url;
    a.download = nome;
    a.click();
  } finally {
    revoke(url);
  }
}
