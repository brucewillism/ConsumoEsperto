/**
 * Produção — substitui {@code environment.ts} no build ({@code ng build --configuration production}).
 *
 * O {@code apiUrl} deve usar o mesmo esquema + host (+ porta apenas se diferente de 443/80-padrão) que o
 * utilizador abre no browser. Caso contrário há CORS (ex.: SPA em {@code https://domínio} e API {@code ...:8443}).
 *
 * Com proxy reverso mapeando {@code /api} → Spring Boot neste mesmo host, mantemos origem igual à do site.
 */
export const environment = {
  production: true,

  apiUrl: 'https://consumoesperto.brucew07.com.br/api',

  /** Cliente OAuth Web no Google Cloud; URIs autorizadas devem incluir este domínio (sem path no cliente). */
  googleClientID: '593452038228-47k24odoa6f18c78e3ssp9bhu56gugnm.apps.googleusercontent.com',

  loadingAssetUrl: 'assets/loading/loading.gif',
  loadingFallbackUrl: 'assets/loading/loading.svg',
  loadingOverlayTailMs: 200
};
