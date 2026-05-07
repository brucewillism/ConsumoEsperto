import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, map } from 'rxjs';
import { Usuario, LoginRequest, LoginResponse, GoogleLoginRequest, GoogleLoginResponse } from '../models/usuario.model';
import { environment } from '../../environments/environment';
import { Router } from '@angular/router';

/**
 * Serviço responsável por gerenciar autenticação de usuários
 * 
 * Este serviço implementa toda a lógica de autenticação, incluindo:
 * - Login tradicional com username/password
 * - Login via Google OAuth2
 * - Gerenciamento de tokens JWT
 * - Armazenamento local de dados do usuário
 * - Controle de estado de autenticação
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Injectable({
  providedIn: 'root' // Singleton disponível em toda a aplicação
})
export class AuthService {
  
  // URL base da API de autenticação
  private readonly API_URL = environment.apiUrl + '/auth';
  
  // ID do cliente Google OAuth2 configurado no environment
  private readonly GOOGLE_CLIENT_ID = environment.googleClientID;
  
  // Subject para gerenciar o estado do usuário atual
  private currentUserSubject = new BehaviorSubject<Usuario | null>(null);
  
  // Observable público para componentes se inscreverem nas mudanças do usuário
  public currentUser$ = this.currentUserSubject.asObservable();

  /**
   * Construtor do serviço
   * 
   * @param http Cliente HTTP para comunicação com o backend
   * @param router Serviço de roteamento para navegação
   */
  constructor(
    private http: HttpClient, 
    private router: Router
  ) {
    // Carrega usuário armazenado no localStorage (se existir)
    this.loadStoredUser();
    
    // Inicializa a autenticação Google OAuth2
    this.initializeGoogleAuth();
  }

  /**
   * Inicializa a autenticação Google OAuth2
   * 
   * Carrega dinamicamente o script do Google Identity Services
   * para permitir login via Google na aplicação.
   */
  private initializeGoogleAuth() {
    // Verifica se o script do Google já foi carregado
    if (typeof window !== 'undefined' && !(window as any).google) {
      // Cria e adiciona o script do Google Identity Services
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true; // Carrega de forma assíncrona
      script.defer = true; // Executa após o HTML ser parseado
      
      // Callback de sucesso
      script.onload = () => {
        console.log('Google Identity Services carregado com sucesso');
      };
      
      // Callback de erro
      script.onerror = () => {
        console.error('Erro ao carregar Google Identity Services');
      };
      
      // Adiciona o script ao head do documento
      document.head.appendChild(script);
    }
  }

  /**
   * Realiza login tradicional com username e password
   * 
   * @param credentials Credenciais de login (username e password)
   * @returns Observable com a resposta de login (token JWT)
   */
  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API_URL}/login`, credentials)
      .pipe(
        // Executa ações adicionais após o login bem-sucedido
        tap(response => {
          this.handleAuthSuccess(response.token); // Armazena o token
          this.loadUserInfo(response.token); // Carrega informações do usuário
        })
      );
  }

  /**
   * Realiza login via Google OAuth2
   * 
   * @param googleLogin Dados do login Google
   * @returns Observable com a resposta de login (token e usuário)
   */
  googleLogin(googleLogin: GoogleLoginRequest): Observable<GoogleLoginResponse> {
    return this.http.post<GoogleLoginResponse>(`${this.API_URL}/google`, googleLogin)
      .pipe(
        tap(response => {
          this.handleAuthSuccess(response.token); // Armazena o token
          this.loadUserInfo(response.token); // Perfil completo (jarvis_configurado, tratamento…) do servidor
        })
      );
  }

  /**
   * Inicia o processo de login com Google OAuth2
   * 
   * Este método inicia o fluxo de autenticação Google, abrindo
   * a janela de login do Google e processando a resposta.
   * 
   * @returns Observable com o resultado da autenticação
   */
  loginWithGoogle(): Observable<GoogleLoginResponse> {
    return new Observable<GoogleLoginResponse>((observer: any) => {
      // Verifica se o Google OAuth está configurado corretamente
      if (!this.isGoogleOAuthConfigured()) {
        observer.error({
          status: 400,
          error: { message: 'Google OAuth não configurado. Configure o googleClientId no environment.ts' }
        });
        return;
      }

      // Aguarda um pouco para garantir que a API Google esteja carregada
      setTimeout(() => {
        if (typeof window !== 'undefined' && (window as any).google) {
          this.authenticateWithGoogle(observer);
        } else {
          observer.error({
            status: 500,
            error: { message: 'Google API não disponível. Verifique se o script foi carregado.' }
          });
        }
      }, 500);
    });
  }

  /**
   * Verifica se o Google OAuth2 está configurado corretamente
   * 
   * @returns true se estiver configurado, false caso contrário
   */
  private isGoogleOAuthConfigured(): boolean {
    return !!(this.GOOGLE_CLIENT_ID && 
           this.GOOGLE_CLIENT_ID !== '123456789-abcdefghijklmnop.apps.googleusercontent.com' &&
           this.GOOGLE_CLIENT_ID.includes('.apps.googleusercontent.com'));
  }

  /**
   * Executa a autenticação com Google OAuth2
   * 
   * @param observer Observer para retornar o resultado da autenticação
   */
  private authenticateWithGoogle(observer: any) {
    try {
      const google = (window as any).google;
      if (google && google.accounts && google.accounts.oauth2) {
        // Inicializa o cliente OAuth2 do Google
        const client = google.accounts.oauth2.initTokenClient({
          client_id: this.GOOGLE_CLIENT_ID,
          scope: 'openid profile email',
          
          // Callback de sucesso
          callback: (response: any) => {
            if (response.access_token) {
              // Busca informações do usuário com o token de acesso
              this.getGoogleUserInfo(response.access_token, observer);
            } else {
              observer.error({
                status: 400,
                error: { message: 'Falha na autenticação com Google' }
              });
            }
          },
          
          // Callback de erro
          error_callback: (error: any) => {
            console.error('Erro na autenticação Google:', error);
            observer.error({
              status: 400,
              error: { message: 'Erro na autenticação com Google: ' + (error.error || 'Erro desconhecido') }
            });
          }
        });

        // Solicita o token de acesso
        client.requestAccessToken();
      } else {
        observer.error({
          status: 500,
          error: { message: 'Google OAuth2 não disponível' }
        });
      }
    } catch (error) {
      console.error('Erro ao inicializar Google Auth:', error);
      observer.error({
        status: 500,
        error: { message: 'Erro interno na autenticação Google' }
      });
    }
  }

  /**
   * Busca informações do usuário no Google usando o token de acesso
   * 
   * @param accessToken Token de acesso obtido do Google
   * @param observer Observer para retornar o resultado
   */
  private getGoogleUserInfo(accessToken: string, observer: any) {
    // URL da API do Google para obter informações do usuário
    const url = `https://www.googleapis.com/oauth2/v2/userinfo?access_token=${accessToken}`;
    
    fetch(url)
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
      })
      .then((userInfo: any) => {
        console.log('Dados reais do Google:', userInfo);
        
        // Cria um usuário com os dados obtidos do Google
        const googleUser: Usuario = {
          id: Date.now(),
          username: userInfo.email,
          nome: userInfo.name || `${userInfo.given_name || ''} ${userInfo.family_name || ''}`.trim(),
          email: userInfo.email,
          fotoUrl: userInfo.picture || '', // Foto do usuário do Google
          cpf: '', // Google não fornece CPF
          dataNascimento: new Date(), // Google não fornece data de nascimento
          telefone: '', // Google não fornece telefone
          endereco: '', // Google não fornece endereço
          cidade: '', // Google não fornece cidade
          estado: '', // Google não fornece estado
          cep: '', // Google não fornece CEP
          dataCadastro: new Date(),
          ativo: true
        };

        // Envia dados do usuário Google para o backend para criar/autenticar
        console.log('[AuthService] Enviando requisição para login Google:', {
          url: `${this.API_URL}/google`,
          accessToken: accessToken ? 'Presente' : 'Ausente',
          userInfo: userInfo ? 'Presente' : 'Ausente'
        });
        
        this.http.post<GoogleLoginResponse>(`${this.API_URL}/google`, {
          accessToken: accessToken,
          userInfo: userInfo
        }).subscribe({
          next: (response) => {
            console.log('Resposta do backend para login Google:', response);
            
            // Atualiza o estado da aplicação com o token JWT real
            this.handleAuthSuccess(response.token);
            this.loadUserInfo(response.token);
            
            // Prepara a resposta de sucesso
            const responseData: GoogleLoginResponse = {
              token: response.token,
              type: 'Bearer',
              user: response.user || googleUser
            };

            observer.next(responseData);
            observer.complete();
          },
          error: (error) => {
            console.error('Erro no backend para login Google:', error);
            
            // Tratamento específico para erro de conexão (0 Unknown Error)
            let errorMessage = 'Erro no servidor';
            if (error.status === 0) {
              errorMessage = 'Erro de conexão: Não foi possível conectar ao servidor. Verifique se o Ngrok está rodando e se a URL está correta.';
            } else if (error.error?.message) {
              errorMessage = 'Erro no servidor: ' + error.error.message;
            } else if (error.message) {
              errorMessage = 'Erro no servidor: ' + error.message;
            }
            
            observer.error({
              status: error.status || 500,
              error: { message: errorMessage }
            });
          }
        });
      })
      .catch(error => {
        console.error('Erro ao buscar dados do usuário Google:', error);
        observer.error({
          status: 500,
          error: { message: 'Erro ao buscar dados do usuário Google: ' + error.message }
        });
      });
  }

  /**
   * Registra um novo usuário no sistema
   * 
   * @param user Dados do usuário a ser registrado
   * @returns Observable com o usuário criado
   */
  register(user: Usuario): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.API_URL}/registro`, user);
  }

  /**
   * Realiza logout do usuário
   * 
   * Remove o token e dados do usuário do localStorage,
   * limpa o estado da aplicação e redireciona para login.
   */
  logout(): void {
    localStorage.removeItem('token'); // Remove o token JWT
    localStorage.removeItem('user'); // Remove dados do usuário
    this.currentUserSubject.next(null); // Limpa o usuário atual
    this.router.navigate(['/login']); // Redireciona para login
  }

  /**
   * Processa o sucesso da autenticação
   * 
   * @param token Token JWT recebido do backend
   */
  private handleAuthSuccess(token: string): void {
    localStorage.setItem('token', token); // Armazena o token no localStorage
  }

  /**
   * Carrega informações do usuário após login bem-sucedido
   * 
   * Este método faz uma requisição para o backend para obter
   * os dados completos do usuário autenticado.
   * 
   * @param token Token JWT para autenticação
   */
  private loadUserInfo(token: string): void {
    this.http.get<any>(`${environment.apiUrl}/usuarios/perfil`).subscribe({
      next: (response) => {
        console.log('[AuthService] Dados do usuário carregados:', response);
        this.applyPerfilResponse(response);
      },
      error: (error) => {
        console.error('[AuthService] Erro ao carregar dados do usuário:', error);
      }
    });
  }

  /** Recarrega perfil no servidor e atualiza o estado local (ex.: após PATCH tratamento). */
  reloadCurrentUserProfile(): Observable<Usuario> {
    return this.http.get<any>(`${environment.apiUrl}/usuarios/perfil`).pipe(
      tap((response) => console.log('[AuthService] Perfil recarregado:', response)),
      map((response) => this.mapPerfilToUsuario(response)),
      tap((user) => {
        this.currentUserSubject.next(user);
        localStorage.setItem('user', JSON.stringify(user));
      })
    );
  }

  public applyPerfilResponse(response: any): void {
    const user = this.mapPerfilToUsuario(response);
    this.currentUserSubject.next(user);
    localStorage.setItem('user', JSON.stringify(user));
  }

  private mapPerfilToUsuario(response: any): Usuario {
    return {
      id: response.id,
      username: response.username,
      email: response.email,
      nome: response.nome,
      fotoUrl: response.fotoUrl,
      whatsappNumero: response.whatsappNumero,
      dataCriacao: response.dataCriacao ? new Date(response.dataCriacao) : undefined,
      ultimoAcesso: response.ultimoAcesso ? new Date(response.ultimoAcesso) : undefined,
      preferenciaTratamentoJarvis: response.preferenciaTratamentoJarvis,
      jarvisTratamentoResumo: response.jarvisTratamentoResumo,
      genero: response.genero,
      generoConfirmado: response.generoConfirmado,
      tratamento: response.tratamento,
      jarvisConfigurado:
        response.jarvisConfigurado === true || response.jarvis_configurado === true,
      ativo: true
    };
  }

  /**
   * Carrega usuário armazenado no localStorage
   * 
   * Útil para manter o usuário logado após recarregar a página.
   */
  private loadStoredUser(): void {
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
      try {
        const user = JSON.parse(storedUser);
        this.currentUserSubject.next(user);
      } catch (error) {
        console.error('Erro ao carregar usuário armazenado:', error);
        localStorage.removeItem('user'); // Remove dados corrompidos
      }
    }
  }

  /**
   * Obtém o usuário atualmente autenticado
   * 
   * @returns Usuário atual ou null se não autenticado
   */
  getCurrentUser(): Usuario | null {
    return this.currentUserSubject.value;
  }

  /**
   * Verifica se o usuário está autenticado
   * 
   * @returns true se autenticado, false caso contrário
   */
  isAuthenticated(): boolean {
    return !!localStorage.getItem('token');
  }

  /**
   * Obtém o token JWT atual
   * 
   * @returns Token JWT ou null se não existir
   */
  getToken(): string | null {
    return localStorage.getItem('token');
  }

  /**
   * Obtém o ID do usuário atualmente autenticado
   * 
   * @returns ID do usuário ou null se não autenticado
   */
  getUserId(): number | null {
    const user = this.getCurrentUser();
    return user?.id ?? null;
  }

}
