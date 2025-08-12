import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { AuthService } from './services/auth.service';
import { Usuario } from './models/usuario.model';
import { filter } from 'rxjs/operators';

/**
 * Interface que define a estrutura de uma notificação
 * Usada para exibir mensagens de alerta no header da aplicação
 */
interface Notification {
  id: number;           // Identificador único da notificação
  message: string;       // Mensagem da notificação
  icon: string;          // Ícone a ser exibido (FontAwesome)
  time: string;          // Horário da notificação
  read: boolean;         // Indica se a notificação foi lida
}

/**
 * Componente principal da aplicação ConsumoEsperto
 * 
 * Este é o componente raiz que gerencia o layout principal da aplicação,
 * incluindo header, sidebar, navegação e estado de autenticação do usuário.
 * Controla a exibição de componentes baseado no status de login.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  
  // Título da aplicação exibido no header
  title = 'ConsumoEsperto';
  
  // Indica se o usuário está autenticado
  isAuthenticated = false;
  
  // Dados do usuário logado
  currentUser: Usuario | null = null;
  
  // Indica se a sidebar está expandida ou recolhida
  sidebarExpanded = true;
  
  // Indica se a sidebar está recolhida (para compatibilidade com o template)
  sidebarCollapsed = false;
  
  // Indica se está na página de login
  isLoginPage = false;
  
  // Dados do usuário para exibição
  userName = '';
  userEmail = '';
  userPhoto = '';
  
  // Estado das notificações
  showNotifications = false;
  notificationCount = 0;
  
  // Estado do menu do usuário
  showUserMenu = false;
  
  // Página atual para breadcrumb
  currentPage = 'Dashboard';
  
  // Lista de notificações do usuário
  notifications: Notification[] = [
    {
      id: 1,
      message: 'Fatura do cartão vence em 3 dias',
      icon: 'fas fa-credit-card',
      time: '2 min atrás',
      read: false
    },
    {
      id: 2,
      message: 'Meta de economia atingida!',
      icon: 'fas fa-trophy',
      time: '1 hora atrás',
      read: false
    },
    {
      id: 3,
      message: 'Nova funcionalidade disponível',
      icon: 'fas fa-star',
      time: '2 horas atrás',
      read: true
    }
  ];

  /**
   * Construtor do componente
   * 
   * @param authService Serviço de autenticação para gerenciar login/logout
   * @param router Serviço de roteamento para navegação
   */
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  /**
   * Método executado na inicialização do componente
   * Configura o estado de autenticação e escuta mudanças de rota
   */
  ngOnInit() {
    // Escuta mudanças no estado de autenticação
    this.authService.currentUser$.subscribe(user => {
      this.isAuthenticated = !!user;
      this.currentUser = user;
      
      // Atualiza dados do usuário para exibição
      if (user) {
        this.userName = user.nome || user.email || 'Usuário';
        this.userEmail = user.email || '';
        this.userPhoto = user.foto || '';
        this.notificationCount = this.getUnreadNotificationsCount();
      }
    });

    // Escuta mudanças de rota para ajustar o layout
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      // Detecta se está na página de login
      this.isLoginPage = event.url === '/login' || event.url === '/register';
      
      // Atualiza a página atual para breadcrumb
      if (event.url === '/dashboard') {
        this.currentPage = 'Dashboard';
      } else if (event.url === '/transacoes') {
        this.currentPage = 'Transações';
      } else if (event.url === '/cartoes') {
        this.currentPage = 'Cartões';
      } else if (event.url === '/faturas') {
        this.currentPage = 'Faturas';
      } else if (event.url === '/relatorios') {
        this.currentPage = 'Relatórios';
      } else if (event.url === '/simulacoes') {
        this.currentPage = 'Simulações';
      }
    });
  }

  /**
   * Alterna o estado da sidebar (expandida/recolhida)
   * Usado para responsividade em dispositivos móveis
   */
  toggleSidebar() {
    this.sidebarExpanded = !this.sidebarExpanded;
  }

  /**
   * Marca uma notificação como lida
   * 
   * @param notificationId ID da notificação a ser marcada como lida
   */
  markNotificationAsRead(notificationId: number) {
    const notification = this.notifications.find(n => n.id === notificationId);
    if (notification) {
      notification.read = true;
    }
  }

  /**
   * Remove uma notificação da lista
   * 
   * @param notificationId ID da notificação a ser removida
   */
  removeNotification(notificationId: number) {
    this.notifications = this.notifications.filter(n => n.id !== notificationId);
  }

  /**
   * Realiza logout do usuário
   * Limpa o estado de autenticação e redireciona para login
   */
  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  /**
   * Obtém o número de notificações não lidas
   * 
   * @returns Quantidade de notificações não lidas
   */
  getUnreadNotificationsCount(): number {
    return this.notifications.filter(n => !n.read).length;
  }
  
  /**
   * Alterna o estado das notificações
   */
  toggleNotifications() {
    this.showNotifications = !this.showNotifications;
    this.showUserMenu = false; // Fecha o menu do usuário
  }
  
  /**
   * Marca todas as notificações como lidas
   */
  markAllAsRead() {
    this.notifications.forEach(n => n.read = true);
    this.notificationCount = 0;
  }
  
  /**
   * Alterna o estado do menu do usuário
   */
  toggleUserMenu() {
    this.showUserMenu = !this.showUserMenu;
    this.showNotifications = false; // Fecha as notificações
  }
  
  /**
   * Fecha todos os dropdowns
   */
  closeDropdowns() {
    this.showNotifications = false;
    this.showUserMenu = false;
  }
}
