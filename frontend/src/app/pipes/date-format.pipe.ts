import { Pipe, PipeTransform } from '@angular/core';

/**
 * Pipe para formatação de datas no padrão brasileiro dd/mm/yyyy
 * 
 * Este pipe converte datas para o formato brasileiro padrão,
 * exibindo dia, mês e ano com zeros à esquerda quando necessário.
 * 
 * Exemplos de uso:
 * - {{ data | dateFormat }} -> "15/12/2024"
 * - {{ '2024-12-15' | dateFormat }} -> "15/12/2024"
 * - {{ new Date() | dateFormat }} -> "06/10/2025"
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Pipe({
  name: 'dateFormat',
  standalone: true
})
export class DateFormatPipe implements PipeTransform {

  /**
   * Transforma uma data para o formato brasileiro dd/mm/yyyy
   * 
   * @param value Data a ser formatada (string, Date ou timestamp)
   * @param args Argumentos adicionais (não utilizados)
   * @returns String formatada no padrão dd/mm/yyyy
   */
  transform(value: any, ...args: any[]): string {
    if (!value) {
      return '';
    }

    try {
      let date: Date;

      // Converte o valor para Date se necessário
      if (typeof value === 'string') {
        // Remove caracteres especiais e converte para Date
        const cleanValue = value.replace(/[^\d\-T:Z.]/g, '');
        date = new Date(cleanValue);
      } else if (typeof value === 'number') {
        // Se for timestamp, multiplica por 1000 se necessário
        date = new Date(value > 1000000000000 ? value : value * 1000);
      } else if (value instanceof Date) {
        date = value;
      } else {
        // Tenta converter para Date
        date = new Date(value);
      }

      // Verifica se a data é válida
      if (isNaN(date.getTime())) {
        console.warn('⚠️ Data inválida para formatação:', value);
        return '';
      }

      // Formata para dd/mm/yyyy
      const day = date.getDate().toString().padStart(2, '0');
      const month = (date.getMonth() + 1).toString().padStart(2, '0');
      const year = date.getFullYear();

      return `${day}/${month}/${year}`;

    } catch (error) {
      console.error('❌ Erro ao formatar data:', error, 'Valor:', value);
      return '';
    }
  }
}
