import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FamiliaService, GrupoFamiliar } from '../../services/familia.service';
import { ToastService } from '../../services/toast.service';
import { resolveHttpError } from '../utils/form.utils';

@Component({
  selector: 'app-criar-grupo-familiar-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './criar-grupo-familiar-dialog.component.html',
  styleUrl: './criar-grupo-familiar-dialog.component.scss',
})
export class CriarGrupoFamiliarDialogComponent {
  nomeGrupo = 'Orçamento do Casal';
  grupoErro = '';
  salvando = false;

  constructor(
    private familiaService: FamiliaService,
    private toast: ToastService,
    private dialogRef: MatDialogRef<CriarGrupoFamiliarDialogComponent, GrupoFamiliar | null>
  ) {}

  salvar(): void {
    this.grupoErro = '';
    const nome = (this.nomeGrupo || '').trim();
    if (!nome) {
      this.grupoErro = 'Informe um nome para o grupo familiar.';
      return;
    }
    this.salvando = true;
    this.familiaService.criar(nome).subscribe({
      next: (grupo) => {
        this.salvando = false;
        this.toast.success('Grupo familiar criado.');
        this.dialogRef.close(grupo);
      },
      error: (e) => {
        this.salvando = false;
        this.grupoErro = resolveHttpError(e, 'Erro ao criar grupo.');
      },
    });
  }
}
