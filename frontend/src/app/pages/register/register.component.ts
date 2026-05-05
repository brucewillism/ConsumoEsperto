import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from '../../services/auth.service';
import { Usuario } from '../../models/usuario.model';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    RouterLink
  ],
  template: `
    <div class="register-container">
      <mat-card class="register-card">
        <mat-card-header>
          <mat-card-title>Registro</mat-card-title>
          <mat-card-subtitle>Crie sua conta</mat-card-subtitle>
        </mat-card-header>
        
        <mat-card-content>
          <form [formGroup]="registerForm" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Nome</mat-label>
              <input matInput formControlName="nome" placeholder="Digite seu nome completo">
              <mat-error *ngIf="registerForm.get('nome')?.hasError('required')">
                Nome é obrigatório
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Usuário</mat-label>
              <input matInput formControlName="username" placeholder="Digite seu usuário">
              <mat-error *ngIf="registerForm.get('username')?.hasError('required')">
                Usuário é obrigatório
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" placeholder="Digite seu email">
              <mat-error *ngIf="registerForm.get('email')?.hasError('required')">
                Email é obrigatório
              </mat-error>
              <mat-error *ngIf="registerForm.get('email')?.hasError('email')">
                Email inválido
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Senha</mat-label>
              <input matInput type="password" formControlName="password" placeholder="Digite sua senha">
              <mat-error *ngIf="registerForm.get('password')?.hasError('required')">
                Senha é obrigatória
              </mat-error>
              <mat-error *ngIf="registerForm.get('password')?.hasError('minlength')">
                Senha deve ter pelo menos 6 caracteres
              </mat-error>
            </mat-form-field>

            <button mat-raised-button color="primary" type="submit" class="full-width" [disabled]="registerForm.invalid">
              Registrar
            </button>
          </form>

          <div class="login-link">
            <span>Já tem uma conta?</span>
            <a routerLink="/login">Faça login</a>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .register-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      padding: 16px;
      background: radial-gradient(ellipse at 20% 0%, rgba(16, 185, 129, 0.12) 0%, transparent 50%),
        radial-gradient(ellipse at 80% 100%, rgba(245, 158, 11, 0.08) 0%, transparent 45%),
        var(--exec-bg, #0f172a);
    }

    .register-card {
      max-width: 420px;
      width: 100%;
      margin: 0 auto;
    }

    .full-width {
      width: 100%;
      margin-bottom: 16px;
    }

    .login-link {
      text-align: center;
      margin-top: 20px;
      color: var(--text-secondary, #94a3b8);
      font-size: 0.875rem;
    }

    .login-link a {
      color: var(--exec-emerald, #10b981);
      text-decoration: none;
      margin-left: 6px;
      font-weight: 600;
    }

    .login-link a:hover {
      text-decoration: underline;
      color: #34d399;
    }
  `]
})
export class RegisterComponent {
  registerForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.registerForm = this.fb.group({
      nome: ['', Validators.required],
      username: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  onSubmit(): void {
    if (this.registerForm.valid) {
      const user: Usuario = this.registerForm.value;
      this.authService.register(user).subscribe({
        next: () => {
          this.router.navigate(['/login']);
        },
        error: (error) => {
          console.error('Erro no registro:', error);
          // Aqui você pode adicionar um snackbar ou toast para mostrar o erro
        }
      });
    }
  }
}
