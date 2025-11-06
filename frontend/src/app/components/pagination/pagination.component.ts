import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Interface que define a estrutura de uma página na paginação
 */
export interface PaginationPage {
  number: number;        // Número da página (1-based)
  isActive: boolean;     // Se é a página atual
  isDisabled: boolean;   // Se está desabilitada
}

/**
 * Componente de paginação reutilizável
 * 
 * Este componente exibe controles de paginação com botões para
 * navegar entre páginas, mostrar informações de registros e
 * permitir seleção de itens por página.
 * 
 * Funcionalidades:
 * - Navegação entre páginas (anterior/próximo)
 * - Exibição de números de página
 * - Seleção de itens por página
 * - Informações de registros (ex: "1-10 de 100")
 * - Suporte a páginas grandes com ellipsis
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.scss'
})
export class PaginationComponent implements OnInit, OnChanges {

  // Inputs do componente
  @Input() currentPage: number = 1;           // Página atual
  @Input() totalItems: number = 0;            // Total de itens
  @Input() itemsPerPage: number = 10;         // Itens por página
  @Input() maxVisiblePages: number = 5;       // Máximo de páginas visíveis
  @Input() showItemsPerPage: boolean = true;  // Mostrar seletor de itens por página
  @Input() showInfo: boolean = true;          // Mostrar informações de registros
  @Input() disabled: boolean = false;         // Se a paginação está desabilitada

  // Outputs do componente
  @Output() pageChange = new EventEmitter<number>();           // Emite quando a página muda
  @Output() itemsPerPageChange = new EventEmitter<number>();   // Emite quando itens por página muda

  // Propriedades calculadas
  totalPages: number = 0;
  pages: PaginationPage[] = [];
  startItem: number = 0;
  endItem: number = 0;
  hasPrevious: boolean = false;
  hasNext: boolean = false;

  // Opções de itens por página
  itemsPerPageOptions: number[] = [5, 10, 20, 50, 100];

  ngOnInit() {
    this.calculatePagination();
  }

  ngOnChanges(changes: SimpleChanges) {
    this.calculatePagination();
  }

  /**
   * Calcula todas as propriedades da paginação
   */
  private calculatePagination() {
    // Calcula total de páginas
    this.totalPages = Math.ceil(this.totalItems / this.itemsPerPage);
    
    // Garante que a página atual está dentro dos limites
    if (this.currentPage < 1) {
      this.currentPage = 1;
    } else if (this.currentPage > this.totalPages && this.totalPages > 0) {
      this.currentPage = this.totalPages;
    }

    // Calcula itens de início e fim
    this.startItem = this.totalItems === 0 ? 0 : ((this.currentPage - 1) * this.itemsPerPage) + 1;
    this.endItem = Math.min(this.currentPage * this.itemsPerPage, this.totalItems);

    // Calcula se há páginas anterior e próxima
    this.hasPrevious = this.currentPage > 1;
    this.hasNext = this.currentPage < this.totalPages;

    // Gera array de páginas
    this.generatePages();
  }

  /**
   * Gera o array de páginas para exibição
   */
  private generatePages() {
    this.pages = [];

    if (this.totalPages <= 1) {
      return;
    }

    const startPage = Math.max(1, this.currentPage - Math.floor(this.maxVisiblePages / 2));
    const endPage = Math.min(this.totalPages, startPage + this.maxVisiblePages - 1);

    for (let i = startPage; i <= endPage; i++) {
      this.pages.push({
        number: i,
        isActive: i === this.currentPage,
        isDisabled: false
      });
    }
  }

  /**
   * Vai para uma página específica
   */
  goToPage(page: number) {
    if (this.disabled || page < 1 || page > this.totalPages || page === this.currentPage) {
      return;
    }

    this.currentPage = page;
    this.pageChange.emit(page);
  }

  /**
   * Vai para a página anterior
   */
  goToPrevious() {
    if (this.hasPrevious && !this.disabled) {
      this.goToPage(this.currentPage - 1);
    }
  }

  /**
   * Vai para a próxima página
   */
  goToNext() {
    if (this.hasNext && !this.disabled) {
      this.goToPage(this.currentPage + 1);
    }
  }

  /**
   * Vai para a primeira página
   */
  goToFirst() {
    if (!this.disabled && this.currentPage > 1) {
      this.goToPage(1);
    }
  }

  /**
   * Vai para a última página
   */
  goToLast() {
    if (!this.disabled && this.currentPage < this.totalPages) {
      this.goToPage(this.totalPages);
    }
  }

  /**
   * Altera o número de itens por página
   */
  onItemsPerPageChange(newItemsPerPage: number) {
    if (this.disabled || newItemsPerPage === this.itemsPerPage) {
      return;
    }

    this.itemsPerPage = newItemsPerPage;
    this.currentPage = 1; // Volta para a primeira página
    this.itemsPerPageChange.emit(newItemsPerPage);
  }

  /**
   * Retorna a string de informações dos registros
   */
  getItemsInfo(): string {
    if (this.totalItems === 0) {
      return 'Nenhum registro encontrado';
    }

    return `${this.startItem}-${this.endItem} de ${this.totalItems}`;
  }

  /**
   * Verifica se deve mostrar ellipsis antes das páginas
   */
  showEllipsisBefore(): boolean {
    return this.pages.length > 0 && this.pages[0].number > 1;
  }

  /**
   * Verifica se deve mostrar ellipsis depois das páginas
   */
  showEllipsisAfter(): boolean {
    return this.pages.length > 0 && this.pages[this.pages.length - 1].number < this.totalPages;
  }
}
