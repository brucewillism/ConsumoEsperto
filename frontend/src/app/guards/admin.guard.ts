import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { UsuarioService } from '../services/usuario.service';

/**
 * Guard administrativo real: consulta o perfil no backend e só permite acesso
 * quando o papel persistido é ADMIN. A autorização definitiva continua sendo
 * do backend (403 para usuário comum) — este guard evita expor a rota na SPA.
 */
export const AdminGuard = () => {
  const authService = inject(AuthService);
  const usuarioService = inject(UsuarioService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  return usuarioService.getUsuario().pipe(
    map(usuario => {
      if (usuario?.role === 'ADMIN') {
        return true;
      }
      router.navigate(['/dashboard']);
      return false;
    }),
    catchError(() => {
      router.navigate(['/dashboard']);
      return of(false);
    })
  );
};
