import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { CeInputMaskDirective } from '../../shared/directives/ce-input-mask.directive';
import { Usuario } from '../../models/usuario.model';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CeInputMaskDirective],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  registerForm: FormGroup;
  isLoading = false;
  showPassword = false;
  alertMessage = '';
  alertType: 'success' | 'error' = 'error';

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
    if (!this.registerForm.valid) {
      this.markFormGroupTouched();
      return;
    }

    this.isLoading = true;
    this.alertMessage = '';
    const user: Usuario = this.registerForm.value;

    this.authService.register(user).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/login'], {
          queryParams: { registered: '1' }
        });
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        this.alertMessage = this.resolveRegisterError(error);
        this.alertType = 'error';
      }
    });
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.registerForm.get(fieldName);
    return field ? field.invalid && (field.dirty || field.touched) : false;
  }

  private markFormGroupTouched(): void {
    Object.keys(this.registerForm.controls).forEach(key => {
      this.registerForm.get(key)?.markAsTouched();
    });
  }

  private resolveRegisterError(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Erro de conexão. Verifique sua internet e tente novamente.';
    }
    const msg = error.error?.message;
    if (typeof msg === 'string' && msg.trim()) {
      return msg;
    }
    if (error.status === 409) {
      return 'Este e-mail ou usuário já está cadastrado.';
    }
    return 'Não foi possível criar a conta. Verifique os dados e tente novamente.';
  }
}
