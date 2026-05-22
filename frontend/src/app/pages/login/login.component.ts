import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { LoadingService } from '../../services/loading.service';

/**
 * Componente responsável pela autenticação de usuários
 * 
 * Este componente gerencia o processo de login, incluindo:
 * - Formulário de login tradicional com email/senha
 * - Integração com Google OAuth2
 * - Validação de formulários
 * - Tratamento de erros de autenticação
 * - Redirecionamento após login bem-sucedido
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  /** Formulário reativo para login */
  loginForm: FormGroup;
  
  /** Indica se está processando o login */
  isLoading = false;
  
  /** Controla a visibilidade da senha no campo de input */
  showPassword = false;
  
  /** Mensagem de alerta para o usuário */
  alertMessage = '';
  
  /** Tipo de alerta (success, error, warning, info) */
  alertType: 'success' | 'error' | 'warning' | 'info' = 'info';

  /**
   * Construtor do componente
   * 
   * @param fb FormBuilder para criação do formulário reativo
   * @param authService Serviço de autenticação
   * @param router Serviço de roteamento
   */
  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private loadingService: LoadingService
  ) {
    // Inicializa o formulário com validações
    this.loginForm = this.fb.group({
      login: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(6)]],
      rememberMe: [false]
    });
  }

  /**
   * Método executado na inicialização do componente
   * Verifica se o usuário já está autenticado e redireciona se necessário
   */
  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
      return;
    }
    if (this.route.snapshot.queryParamMap.get('registered') === '1') {
      this.showAlert('Conta criada com sucesso. Faça login com seu e-mail/usuário e senha.', 'success');
    }
  }

  /**
   * Processa o envio do formulário de login
   * 
   * Valida as credenciais e tenta autenticar o usuário.
   * Em caso de sucesso, redireciona para o dashboard.
   */
  onSubmit(): void {
    if (this.loginForm.valid) {
      this.isLoading = true;
      this.clearAlert();
      this.loadingService.beginAuthFlow('Entrando…');

      const credentials = {
        username: this.loginForm.value.login.trim(),
        password: this.loginForm.value.password
      };

      this.authService.login(credentials).subscribe({
        next: () => {
          if (this.loginForm.value.rememberMe) {
            localStorage.setItem('rememberMe', 'true');
          }
          this.loadingService.updateAuthFlowMessage('Entrando…');
          this.router.navigate(['/dashboard']);
        },
        error: (error: HttpErrorResponse) => {
          this.isLoading = false;
          this.loadingService.endAuthFlow();
          this.showAlert(this.resolveLoginError(error), 'error');
        }
      });
    } else {
      this.markFormGroupTouched();
    }
  }

  /**
   * Inicia o processo de login via Google OAuth2
   * 
   * Abre a janela de autenticação do Google e processa
   * a resposta para autenticar o usuário.
   */
  loginWithGoogle(): void {
    if (this.isLoading) {
      return;
    }
    this.isLoading = true;
    this.clearAlert();
    this.loadingService.beginAuthFlow('Conectando com Google…');

    this.authService.loginWithGoogle().subscribe({
      next: () => {
        this.loadingService.updateAuthFlowMessage('Entrando…');
        this.router.navigate(['/dashboard']);
      },
      error: (error) => {
        this.isLoading = false;
        this.loadingService.endAuthFlow();
        console.error('Erro no login com Google:', error);

        let errorMessage = 'Erro ao fazer login com Google. Tente novamente.';
        if (error.status === 0) {
          errorMessage = 'Erro de conexão. Verifique sua internet.';
        } else if (error.error?.message) {
          errorMessage = error.error.message;
        }

        this.showAlert(errorMessage, 'error');
      }
    });
  }

  /**
   * Alterna a visibilidade da senha no campo de input
   * 
   * Permite ao usuário ver o que está digitando na senha
   */
  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  /** Evita navegação quebrada com href="#" e informa o utilizador. */
  onForgotPassword(event: Event): void {
    event.preventDefault();
    this.showAlert('Recuperação de palavra-passe não está disponível nesta versão. Utilize o suporte ou o login com Google.', 'info');
  }

  /**
   * Verifica se um campo específico do formulário é inválido
   * 
   * @param fieldName Nome do campo a ser verificado
   * @returns true se o campo for inválido e foi tocado, false caso contrário
   */
  isFieldInvalid(fieldName: string): boolean {
    const field = this.loginForm.get(fieldName);
    return field ? field.invalid && (field.dirty || field.touched) : false;
  }

  /**
   * Marca todos os campos do formulário como tocados
   * 
   * Útil para exibir erros de validação quando o usuário
   * tenta enviar um formulário inválido
   */
  markFormGroupTouched(): void {
    Object.keys(this.loginForm.controls).forEach(key => {
      const control = this.loginForm.get(key);
      control?.markAsTouched();
    });
  }

  /**
   * Exibe uma mensagem de alerta para o usuário
   * 
   * @param message Mensagem a ser exibida
   * @param type Tipo do alerta (success, error, warning, info)
   */
  showAlert(message: string, type: 'success' | 'error' | 'warning' | 'info'): void {
    this.alertMessage = message;
    this.alertType = type;
    
    // Auto-hide success messages after 5 seconds
    if (type === 'success') {
      setTimeout(() => {
        this.clearAlert();
      }, 5000);
    }
  }

  /**
   * Fecha o alerta atual
   * 
   * Permite ao usuário fechar manualmente as mensagens de alerta
   */
  closeAlert(): void {
    this.clearAlert();
  }

  /**
   * Limpa a mensagem de alerta atual
   * 
   * Método privado usado internamente para limpar alertas
   */
  private clearAlert(): void {
    this.alertMessage = '';
  }

  private resolveLoginError(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Erro de conexão. Verifique sua internet e tente novamente.';
    }
    const isLoginRequest = (error.url || '').includes('/auth/login')
      || (error.error?.path || '').includes('/auth/login');

    if (error.status === 401 && isLoginRequest) {
      return 'E-mail/usuário ou senha incorretos. Verifique os dados digitados e tente novamente.';
    }
    if (error.status === 401) {
      return 'Sessão expirada ou acesso não autorizado. Faça login novamente.';
    }
    const apiMessage = error.error?.message;
    if (typeof apiMessage === 'string' && apiMessage.trim()) {
      if (error.status === 401 && isLoginRequest) {
        return 'E-mail/usuário ou senha incorretos. Verifique os dados digitados e tente novamente.';
      }
      return apiMessage;
    }
    return 'Não foi possível entrar. Tente novamente em instantes.';
  }

  /**
   * Retorna o ícone apropriado para o tipo de alerta
   * 
   * @returns Nome da classe do ícone FontAwesome
   */
  getAlertIcon(): string {
    switch (this.alertType) {
      case 'success':
        return 'fas fa-check-circle';
      case 'error':
        return 'fas fa-exclamation-circle';
      case 'warning':
        return 'fas fa-exclamation-triangle';
      case 'info':
        return 'fas fa-info-circle';
      default:
        return 'fas fa-info-circle';
    }
  }
}
