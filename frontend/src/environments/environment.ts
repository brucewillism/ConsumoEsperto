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
   *  Na stack local com Evolution API Node, Evolution costuma usar :8080 e o Spring :8081
   *  (ver `.cursor/rules/stack-local.mdc`). Só use :8080 aqui se o Java for o único serviço nessa porta. */
  apiUrl: 'http://localhost:8081/api',
  
  /** ID do cliente Google OAuth2 para autenticação via Google
   * 
   * Este ID é usado para permitir login via Google na aplicação.
   * Deve ser configurado no Google Cloud Console e corresponder
   * ao domínio de desenvolvimento (localhost:4200).
   */
  googleClientID: '593452038228-47k24odoa6f18c78e3ssp9bhu56gugnm.apps.googleusercontent.com'
};
