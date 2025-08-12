import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Guard de autenticação para proteger rotas da aplicação
 * 
 * Este guard verifica se o usuário está autenticado antes de permitir
 * acesso às rotas protegidas. Se não estiver autenticado, redireciona
 * para a página de login.
 * 
 * Funcionalidades:
 * - Verifica se o usuário possui token JWT válido
 * - Redireciona usuários não autenticados para /login
 * - Permite acesso apenas a usuários autenticados
 * 
 * Uso nas rotas:
 * ```typescript
 * {
 *   path: 'dashboard',
 *   component: DashboardComponent,
 *   canActivate: [AuthGuard]
 * }
 * ```
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export const AuthGuard = () => {
  // Injeta o serviço de autenticação para verificar o status do usuário
  const authService = inject(AuthService);
  
  // Injeta o serviço de roteamento para redirecionamento
  const router = inject(Router);

  // Verifica se o usuário está autenticado
  if (authService.isAuthenticated()) {
    // Usuário autenticado: permite acesso à rota
    return true;
  }

  // Usuário não autenticado: redireciona para login
  router.navigate(['/login']);
  
  // Bloqueia acesso à rota
  return false;
};
