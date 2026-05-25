import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd, Event as RouterEvent } from '@angular/router';
import { hudRouteAnimations } from './app.animations';
import { AuthService } from './services/auth.service';
import { Usuario } from './models/usuario.model';
import { filter } from 'rxjs/operators';
import { LoadingService, ShellOverlayState } from './services/loading.service';
import { InboxNotification, NotificacaoInboxService } from './services/notificacao-inbox.service';
import { ScoreService, UsuarioScore } from './services/score.service';
import { LoadingIndicatorComponent } from './components/loading-indicator/loading-indicator.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, LoadingIndicatorComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  animations: [hudRouteAnimations],
})
export class AppComponent implements OnInit {
  title = 'ConsumoEsperto';

  /** Estado para re-disparar hudRouteAnimations a cada navegação. */
  routeAnimationState = '';

  isAuthenticated = false;
  currentUser: Usuario | null = null;
  sidebarCollapsed = false;
  isLoginPage = false;

  userName = '';
  userEmail = '';
  userPhoto = '';
  /** Evita ícone de imagem partida quando a URL falha (ex.: URL truncada antiga no BD). */
  userPhotoLoadError = false;

  showNotifications = false;
  notificationCount = 0;

  showUserMenu = false;
  currentPage = 'Dashboard';

  notifications: InboxNotification[] = [];
  usuarioScore: UsuarioScore | null = null;
  shellOverlay: ShellOverlayState = { active: false, message: '' };

  constructor(
    private authService: AuthService,
    private router: Router,
    private loadingService: LoadingService,
    private notificacaoInbox: NotificacaoInboxService,
    private scoreService: ScoreService
  ) {}

  ngOnInit() {
    this.authService.currentUser$.subscribe((user) => {
      this.isAuthenticated = !!user;
      this.currentUser = user;

      if (user) {
        this.userName = user.nome || user.email || 'Usuário';
        this.userEmail = user.email || '';
        this.userPhoto = user.fotoUrl || '';
        this.userPhotoLoadError = false;
        this.refreshNotifications();
        this.scoreService.obter().subscribe({
          next: (score) => this.usuarioScore = score,
          error: () => this.usuarioScore = null
        });
      } else {
        this.userName = '';
        this.userEmail = '';
        this.userPhoto = '';
        this.userPhotoLoadError = false;
        this.notifications = [];
        this.notificationCount = 0;
        this.usuarioScore = null;
      }
    });

    this.routeAnimationState = this.router.url;
    this.isLoginPage = this.router.url === '/login' || this.router.url === '/register';

    this.loadingService.shellOverlay$.subscribe((state) => {
      this.shellOverlay = state;
    });

    this.router.events
      .pipe(filter((e: RouterEvent): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        const path = event.urlAfterRedirects || event.url;
        this.isLoginPage = path === '/login' || path === '/register';
        this.currentPage = this.tituloRota(path);
        this.routeAnimationState = path;
        if (!this.isLoginPage && this.loadingService.isAuthFlowActive()) {
          this.loadingService.endAuthFlow();
        }
        this.closeDropdowns();
        if (typeof window !== 'undefined' && window.innerWidth <= 1024) {
          this.sidebarCollapsed = false;
        }
      });
  }

  toggleSidebar() {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  private tituloRota(url: string): string {
    const path = (url || '').split('?')[0];
    if (path.startsWith('/transacoes')) return 'Transações';
    if (path.startsWith('/cartoes')) return 'Cartões';
    if (path.startsWith('/contas')) return 'Contas';
    if (path.startsWith('/categorias')) return 'Categorias';
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

  /** Garante navegação mesmo se routerLink for bloqueado por overlay. */
  navigateSidebar(path: string, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.closeDropdowns();
    const destino = path.split('?')[0] || '/';
    void this.router.navigateByUrl(destino);
    if (typeof window !== 'undefined' && window.innerWidth <= 1024) {
      this.sidebarCollapsed = false;
    }
  }

  navigateUserMenu(path: string, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.closeDropdowns();
    const destino = path.split('?')[0];
    const atual = this.router.url.split('?')[0];
    if (atual !== destino) {
      void this.router.navigateByUrl(destino);
    }
  }

  onUserMenuLogout(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.closeDropdowns();
    this.logout();
  }

  trackNotif(_i: number, n: InboxNotification): string {
    return n.key;
  }
}
