import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import {
  CHART_METODOLOGIAS,
  ChartMetodologiaId,
} from '../../utils/chart-metodologias';

@Component({
  selector: 'app-chart-metodologia',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './chart-metodologia.component.html',
  styleUrl: './chart-metodologia.component.scss',
})
export class ChartMetodologiaComponent {
  @Input({ required: true }) id!: ChartMetodologiaId;
  /** Se true, bloco inicia aberto. */
  @Input() aberto = false;
  /** Variante compacta (menos padding). */
  @Input() compact = false;

  get meta() {
    return CHART_METODOLOGIAS[this.id];
  }
}
