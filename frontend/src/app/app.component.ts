import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd, Event as RouterEvent } from '@angular/router';
import { AuthService } from './services/auth.service';
import { Usuario } from './models/usuario.model';
import { filter } from 'rxjs/operators';
import { LoadingService } from './services/loading.service';
import { InboxNotification, NotificacaoInboxService } from './services/notificacao-inbox.service';
import { ScoreService, UsuarioScore } from './services/score.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  title = 'ConsumoEsperto';

  isAuthenticated = false;
  currentUser: Usuario | null = null;
  sidebarCollapsed = false;
  isLoginPage = false;

  userName = '';
  userEmail = '';
  userPhoto = '';

  showNotifications = false;
  notificationCount = 0;

  showUserMenu = false;
  currentPage = 'Dashboard';

  notifications: InboxNotification[] = [];
  usuarioScore: UsuarioScore | null = null;

  constructor(
    private authService: AuthService,
    private router: Router,
    private loadingService: LoadingService,
    private notificacaoInbox: NotificacaoInboxService,
    private scoreService: ScoreService
  ) {}

  get isLoading$() {
    return this.loadingService.isLoading$;
  }

  ngOnInit() {
    this.authService.currentUser$.subscribe((user) => {
      this.isAuthenticated = !!user;
      this.currentUser = user;

      if (user) {
        this.userName = user.nome || user.email || 'Usuário';
        this.userEmail = user.email || '';
        this.userPhoto = user.fotoUrl || '';
        this.refreshNotifications();
        this.scoreService.obter().subscribe({
          next: (score) => this.usuarioScore = score,
          error: () => this.usuarioScore = null
        });
      } else {
        this.notifications = [];
        this.notificationCount = 0;
        this.usuarioScore = null;
      }
    });

    this.router.events
      .pipe(filter((e: RouterEvent): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        const path = event.urlAfterRedirects || event.url;
        this.isLoginPage = path === '/login' || path === '/register';
        this.currentPage = this.tituloRota(path);
      });
  }

  toggleSidebar() {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  private tituloRota(url: string): string {
    const path = (url || '').split('?')[0];
    if (path.startsWith('/transacoes')) return 'Transações';
    if (path.startsWith('/cartoes')) return 'Cartões';
    if (path.startsWith('/faturas')) return 'Faturas';
    if (path.startsWith('/relatorios')) return 'Relatórios';
    if (path.startsWith('/simulacoes')) return 'Simulações';
    if (path.startsWith('/metas')) return 'Metas';
    if (path.startsWith('/orcamentos')) return 'Orçamentos';
    if (path.startsWith('/renda')) return 'Renda';
    if (path.startsWith('/familia')) return 'Família';
    if (path.startsWith('/investimentos')) return 'Investimentos';
    if (path.startsWith('/score')) return 'Score';
    if (path.startsWith('/importacoes-pendentes')) return 'Importações Pendentes';
    if (path.startsWith('/whatsapp-config')) return 'WhatsApp';
    if (path.startsWith('/perfil')) return 'Perfil';
    if (path.startsWith('/dashboard')) return 'Dashboard';
    if (path.startsWith('/register')) return 'Registo';
    if (path.startsWith('/login')) return 'Login';
    return 'ConsumoEsperto';
  }

  refreshNotifications(): void {
    this.notificacaoInbox.loadInbox().subscribe((items) => {
      this.notifications = items;
      this.notificationCount = items.filter((n) => !n.read).length;
    });
  }

  onNotificationClick(n: InboxNotification, ev: Event): void {
    ev.stopPropagation();
    if (n.read) return;
    if (n.serverId != null) {
      this.notificacaoInbox.marcarLidaServidor(n.serverId).subscribe(() => this.refreshNotifications());
    } else {
      this.notificacaoInbox.markSyntheticKeysRead([n.key]);
      n.read = true;
      this.notificationCount = this.notifications.filter((x) => !x.read).length;
    }
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  toggleNotifications() {
    this.showNotifications = !this.showNotifications;
    this.showUserMenu = false;
    if (this.showNotifications && this.isAuthenticated) {
      this.refreshNotifications();
    }
  }

  markAllAsRead() {
    const synthKeys = this.notifications.filter((n) => n.serverId == null).map((n) => n.key);
    this.notificacaoInbox.marcarTodasLidasServidor().subscribe(() => {
      if (synthKeys.length) {
        this.notificacaoInbox.markSyntheticKeysRead(synthKeys);
      }
      this.refreshNotifications();
    });
  }

  toggleUserMenu() {
    this.showUserMenu = !this.showUserMenu;
    this.showNotifications = false;
  }

  closeDropdowns() {
    this.showNotifications = false;
    this.showUserMenu = false;
  }

  trackNotif(_i: number, n: InboxNotification): string {
    return n.key;
  }
}
