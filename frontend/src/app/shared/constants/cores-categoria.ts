/** Cores padrão para categorias (hex). */
export interface CorCategoriaPadrao {
  id: string;
  hex: string;
  nome: string;
}

export const CORES_CATEGORIA_PADRAO: readonly CorCategoriaPadrao[] = [
  { id: 'verde', hex: '#22c55e', nome: 'Verde' },
  { id: 'azul', hex: '#3b82f6', nome: 'Azul' },
  { id: 'ciano', hex: '#06b6d4', nome: 'Ciano' },
  { id: 'roxo', hex: '#a855f7', nome: 'Roxo' },
  { id: 'rosa', hex: '#ec4899', nome: 'Rosa' },
  { id: 'vermelho', hex: '#ef4444', nome: 'Vermelho' },
  { id: 'laranja', hex: '#f97316', nome: 'Laranja' },
  { id: 'amarelo', hex: '#eab308', nome: 'Amarelo' },
  { id: 'lima', hex: '#84cc16', nome: 'Lima' },
  { id: 'teal', hex: '#14b8a6', nome: 'Teal' },
  { id: 'indigo', hex: '#6366f1', nome: 'Índigo' },
  { id: 'cinza', hex: '#94a3b8', nome: 'Cinza' },
] as const;

export function normalizarCorCategoria(valor: string | null | undefined): string {
  const v = (valor ?? '').trim();
  if (!v) {
    return '';
  }
  const lower = v.toLowerCase();
  const padrao = CORES_CATEGORIA_PADRAO.find(
    (c) => c.hex.toLowerCase() === lower || c.id === lower
  );
  return padrao?.hex ?? (lower.startsWith('#') && /^#[0-9a-f]{6}$/i.test(lower) ? lower : '');
}
