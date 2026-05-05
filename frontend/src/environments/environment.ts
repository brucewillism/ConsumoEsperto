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
  
  /** URL base da API — alinhar com rodar-backend-evolution.bat (Spring na 8081). Se usar rodar-backend.bat na 8080, mude para 8080. */
  apiUrl: 'http://localhost:8081/api',
  
  /** ID do cliente Google OAuth2 para autenticação via Google
   * 
   * Este ID é usado para permitir login via Google na aplicação.
   * Deve ser configurado no Google Cloud Console e corresponder
   * ao domínio de desenvolvimento (localhost:4200).
   */
  googleClientID: '593452038228-47k24odoa6f18c78e3ssp9bhu56gugnm.apps.googleusercontent.com'
};
