import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Categoria } from '../../models/categoria.model';
import { CategoriaService } from '../../services/categoria.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    MatDialogModule,
  ],
  templateUrl: './categorias.component.html',
  styleUrl: './categorias.component.scss',
})
export class CategoriasComponent implements OnInit {
  @ViewChild('formTpl') formTpl!: TemplateRef<unknown>;

  categorias: Categoria[] = [];
  loading = false;
  salvando = false;
  form!: FormGroup;
  editando: Categoria | null = null;

  constructor(
    private fb: FormBuilder,
    private categoriaService: CategoriaService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {
    this.form = this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(100)]],
      descricao: ['', Validators.maxLength(500)],
      cor: [''],
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading = true;
    this.categoriaService.buscarPorUsuario().subscribe({
      next: (items) => {
        this.categorias = items ?? [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Erro ao carregar categorias', 'Fechar', { duration: 3000 });
      },
    });
  }

  abrirForm(categoria?: Categoria): void {
    this.editando = categoria ?? null;
    if (categoria) {
      this.form.patchValue({
        nome: categoria.nome,
        descricao: categoria.descricao ?? '',
        cor: categoria.cor ?? '',
      });
    } else {
      this.form.reset({ nome: '', descricao: '', cor: '' });
    }
    this.dialog.open(this.formTpl, { width: '460px' });
  }

  salvar(): void {
    if (this.form.invalid) {
      return;
    }
    this.salvando = true;
    const payload: Categoria = this.form.value;

    const req$ = this.editando?.id
      ? this.categoriaService.atualizarCategoria(this.editando.id, payload)
      : this.categoriaService.criarCategoria(payload);

    req$.subscribe({
      next: () => {
        this.salvando = false;
        this.dialog.closeAll();
        this.carregar();
        this.snackBar.open(this.editando ? 'Categoria atualizada' : 'Categoria criada', 'Fechar', { duration: 2500 });
      },
      error: (err) => {
        this.salvando = false;
        const msg = err?.error?.message || 'Erro ao salvar categoria';
        this.snackBar.open(msg, 'Fechar', { duration: 4000 });
      },
    });
  }

  excluir(categoria: Categoria): void {
    if (!categoria.id) {
      return;
    }
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Excluir categoria',
          message: `Excluir "${categoria.nome}"? Transações existentes podem ficar sem categoria.`,
          confirmText: 'Excluir',
        },
      })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) {
          return;
        }
        this.categoriaService.deletarCategoria(categoria.id!).subscribe({
          next: () => {
            this.carregar();
            this.snackBar.open('Categoria excluída', 'Fechar', { duration: 2500 });
          },
          error: () => this.snackBar.open('Erro ao excluir', 'Fechar', { duration: 3000 }),
        });
      });
  }
}
