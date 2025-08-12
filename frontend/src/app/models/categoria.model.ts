/**
 * Interface que representa uma categoria de transação no sistema ConsumoEsperto
 * 
 * As categorias são usadas para organizar e classificar transações financeiras,
 * permitindo melhor controle e análise dos gastos e receitas.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export interface Categoria {
  /** ID único da categoria no sistema (gerado automaticamente) */
  id?: number;
  
  /** Nome da categoria (ex: "Alimentação", "Transporte", "Lazer") */
  nome: string;
  
  /** Descrição detalhada da categoria (opcional) */
  descricao?: string;
  
  /** Cor hexadecimal para identificação visual da categoria (opcional) */
  cor?: string;
  
  /** ID do usuário proprietário da categoria */
  usuarioId?: number;
  
  /** Data de criação da categoria no sistema */
  dataCriacao?: Date;
}
