/**
 * Configurações do ambiente de desenvolvimento
 * 
 * Este arquivo contém as variáveis de configuração específicas
 * para o ambiente de desenvolvimento local. Em produção,
 * estas configurações são substituídas pelo environment.prod.ts
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export const environment = {
  /** Indica se está em modo de produção (false para desenvolvimento) */
  production: false,
  
  /** URL da API REST (Spring Boot).
   *  Stack local: ver `scripts/stack-ports.ps1` — Spring :18081, Evolution Node :18080, Angular :14200. */
   apiUrl: 'https://consumoesperto.brucew07.com.br/api',
  
  /** ID do cliente Google OAuth2 para autenticação via Google
   * 
   * Este ID é usado para permitir login via Google na aplicação.
   * Deve ser configurado no Google Cloud Console e corresponder
   * ao domínio de desenvolvimento (localhost:14200).
   */
  googleClientID: '593452038228-47k24odoa6f18c78e3ssp9bhu56gugnm.apps.googleusercontent.com',

  /** Overlay global — GIF animado (USAGIF loading-1); SVG estático como fallback. */
  loadingAssetUrl: 'assets/loading/loading.gif',
  loadingFallbackUrl: 'assets/loading/loading.svg',

  /** Tempo extra (ms) após o fim do último pedido antes de ocultar o overlay (ex.: 200 = ⅕ s). */
  loadingOverlayTailMs: 200
};
