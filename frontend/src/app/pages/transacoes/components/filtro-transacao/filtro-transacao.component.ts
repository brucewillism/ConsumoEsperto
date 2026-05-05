import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';

@Component({
  selector: 'app-filtro-transacao',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatIconModule
  ],
  templateUrl: './filtro-transacao.component.html',
  styleUrls: ['./filtro-transacao.component.scss']
})
export class FiltroTransacaoComponent {
  @Input() filtroTipo: '' | 'RECEITA' | 'DESPESA' = '';
  @Input() filtroDataInicio: Date | null = null;
  @Input() filtroDataFim: Date | null = null;
  @Input() disabled = false;

  @Output() filtroTipoChange = new EventEmitter<'' | 'RECEITA' | 'DESPESA'>();
  @Output() filtroDataInicioChange = new EventEmitter<Date | null>();
  @Output() filtroDataFimChange = new EventEmitter<Date | null>();
  @Output() limparFiltros = new EventEmitter<void>();
}
