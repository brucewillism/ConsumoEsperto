import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin, of } from 'rxjs';
import { openCeFormDialog } from '../../shared/ce-form-dialog.util';
import { CriarGrupoFamiliarDialogComponent } from '../../shared/criar-grupo-familiar-dialog/criar-grupo-familiar-dialog.component';
import {
  ConvidarFamiliarDialogComponent,
  ConvidarFamiliarDialogResult,
} from '../../shared/convidar-familiar-dialog/convidar-familiar-dialog.component';
import { catchError } from 'rxjs/operators';
import { FamiliaService, GrupoFamiliar, GrupoFamiliarMembro } from '../../services/familia.service';
import { Orcamento } from '../../services/orcamento.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-familia',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatDialogModule,
    MatIconModule,
  ],
  templateUrl: './familia.component.html',
  styleUrl: './familia.component.scss'
})
export class FamiliaComponent implements OnInit {
  grupo: GrupoFamiliar | null = null;
  convites: GrupoFamiliarMembro[] = [];
  orcamentos: Orcamento[] = [];
  carregando = true;
  conviteVisual = '';

  constructor(
    private familiaService: FamiliaService,
    private toast: ToastService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    forkJoin({
      grupo: this.familiaService.meuGrupo().pipe(catchError(() => of(null))),
      convites: this.familiaService.convites().pipe(catchError(() => of([] as GrupoFamiliarMembro[]))),
      orcamentos: this.familiaService.orcamentosCompartilhados().pipe(catchError(() => of([] as Orcamento[])))
    }).subscribe(({ grupo, convites, orcamentos }) => {
      this.grupo = grupo;
      this.convites = convites;
      this.orcamentos = orcamentos;
      this.carregando = false;
    });
  }

  abrirCriarGrupo(): void {
    openCeFormDialog(this.dialog, CriarGrupoFamiliarDialogComponent, { width: '440px' })
      .afterClosed()
      .subscribe((grupo) => {
        const criado = grupo as GrupoFamiliar | null | undefined;
        if (criado) {
          this.grupo = criado;
          this.carregar();
        }
      });
  }

  abrirConvidar(): void {
    openCeFormDialog(this.dialog, ConvidarFamiliarDialogComponent, { width: '480px' })
      .afterClosed()
      .subscribe((res) => {
        const convite = res as ConvidarFamiliarDialogResult | null | undefined;
        if (convite?.conviteVisual) {
          this.conviteVisual = convite.conviteVisual;
          this.carregar();
        }
      });
  }

  responder(convite: GrupoFamiliarMembro, aceitar: boolean): void {
    this.familiaService.responderConvite(convite.id, aceitar).subscribe({
      next: () => {
        this.toast.success(aceitar ? 'Convite aceito.' : 'Convite recusado.');
        this.carregar();
      },
      error: (e) => this.toast.error(e?.error?.message || 'Erro ao responder convite.')
    });
  }

  brl(v: number): string {
    return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  progress(o: Orcamento): number {
    return Math.min(100, Math.max(0, Number(o.percentualUso) || 0));
  }

  qrUrl(): string {
    return this.conviteVisual
      ? `https://api.qrserver.com/v1/create-qr-code/?size=140x140&data=${encodeURIComponent(this.conviteVisual)}`
      : '';
  }
}
