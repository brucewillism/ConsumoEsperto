import { Routes } from '@angular/router';
import { AuthGuard } from './guards/auth.guard';

/**
 * Configuração de rotas da aplicação ConsumoEsperto
 * 
 * Este arquivo define todas as rotas disponíveis na aplicação,
 * incluindo rotas públicas (login/register) e rotas protegidas
 * que requerem autenticação.
 * 
 * Estrutura das rotas:
 * - Rotas públicas: login, register
 * - Rotas protegidas: dashboard, transações, cartões, faturas, relatórios, simulações
 * - Rota padrão: redireciona para dashboard
 * - Rota wildcard: redireciona rotas inexistentes para dashboard
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export const routes: Routes = [
  // Rota raiz: redireciona automaticamente para o dashboard
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  
  // Rota de login (pública - não requer autenticação)
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  
  // Rota de registro (pública - não requer autenticação)
  { path: 'register', loadComponent: () => import('./pages/register/register.component').then(m => m.RegisterComponent) },
  
  // Dashboard principal (protegida - requer autenticação)
  { 
    path: 'dashboard', 
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [AuthGuard] // Verifica se usuário está logado
  },
  
  // Gestão de transações financeiras (protegida)
  { 
    path: 'transacoes', 
    loadComponent: () => import('./pages/transacoes/transacoes.component').then(m => m.TransacoesComponent),
    canActivate: [AuthGuard]
  },
  
  // Gestão de cartões de crédito (protegida)
  { 
    path: 'cartoes', 
    loadComponent: () => import('./pages/cartoes/cartoes.component').then(m => m.CartoesComponent),
    canActivate: [AuthGuard]
  },
  
  // Gestão de faturas de cartão (protegida)
  { 
    path: 'faturas', 
    loadComponent: () => import('./pages/faturas/faturas.component').then(m => m.FaturasComponent),
    canActivate: [AuthGuard]
  },
  
  // Relatórios financeiros (protegida)
  { 
    path: 'relatorios', 
    loadComponent: () => import('./pages/relatorios/relatorios.component').then(m => m.RelatoriosComponent),
    canActivate: [AuthGuard]
  },
  
  // Simulações de compras (protegida)
  { 
    path: 'simulacoes', 
    loadComponent: () => import('./pages/simulacoes/simulacoes.component').then(m => m.SimulacoesComponent),
    canActivate: [AuthGuard]
  },
  
  // Configuração de APIs bancárias (protegida)
  { 
    path: 'bank-config', 
    loadComponent: () => import('./pages/bank-config/bank-config.component').then(m => m.BankConfigComponent),
    canActivate: [AuthGuard]
  },
  
  // Rota wildcard: captura todas as rotas inexistentes
  // Redireciona para o dashboard (útil para SPA)
  { path: '**', redirectTo: '/dashboard' }
];
