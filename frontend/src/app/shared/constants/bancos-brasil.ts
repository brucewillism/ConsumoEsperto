/** Bancos populares no Brasil — valor do mat-option = id persistido no backend. */
export interface BancoBrasilOption {
  id: string;
  nome: string;
  cor: string;
}

export const BANCOS_BRASIL: readonly BancoBrasilOption[] = [
  { id: 'nubank', nome: 'Nubank', cor: '#8A05BE' },
  { id: 'itau', nome: 'Itaú', cor: '#EC7000' },
  { id: 'inter', nome: 'Banco Inter', cor: '#FF7A00' },
  { id: 'bradesco', nome: 'Bradesco', cor: '#CC092F' },
  { id: 'santander', nome: 'Santander', cor: '#EC0000' },
  { id: 'bb', nome: 'Banco do Brasil', cor: '#FFCC29' },
  { id: 'caixa', nome: 'Caixa Econômica', cor: '#005CA9' },
  { id: 'c6', nome: 'C6 Bank', cor: '#1A1A1A' },
  { id: 'btg', nome: 'BTG Pactual', cor: '#002C4B' },
  { id: 'pagbank', nome: 'PagBank', cor: '#00A868' },
  { id: 'mercadopago', nome: 'Mercado Pago', cor: '#009EE3' },
  { id: 'sicredi', nome: 'Sicredi', cor: '#3E6334' },
  { id: 'sicoob', nome: 'Sicoob', cor: '#003641' },
  { id: 'neon', nome: 'Neon', cor: '#00D4FF' },
  { id: 'original', nome: 'Banco Original', cor: '#00A651' },
  { id: 'pan', nome: 'Banco PAN', cor: '#0072CE' },
  { id: 'safra', nome: 'Banco Safra', cor: '#1C2F5E' },
  { id: 'banrisul', nome: 'Banrisul', cor: '#004B8D' },
  { id: 'outros', nome: 'Outro', cor: '#64748b' },
] as const;

const BANCO_MAP = new Map(BANCOS_BRASIL.map((b) => [b.id, b]));

export function getBancoNomeBr(id: string | null | undefined): string {
  const key = (id ?? '').trim().toLowerCase();
  return BANCO_MAP.get(key)?.nome ?? (id?.trim() || 'Banco');
}

export function getBancoCorBr(id: string | null | undefined): string {
  const key = (id ?? '').trim().toLowerCase();
  return BANCO_MAP.get(key)?.cor ?? '#64748b';
}
