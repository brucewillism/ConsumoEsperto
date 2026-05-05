/**
 * Interface que representa um usuário do sistema ConsumoEsperto
 * 
 * Esta interface define a estrutura completa de dados de um usuário,
 * incluindo informações pessoais, de contato e de sistema.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
export interface Usuario {
  /** ID único do usuário no sistema (gerado automaticamente) */
  id?: number;
  
  /** Nome de usuário único para login (email ou username personalizado) */
  username: string;
  
  /** Endereço de email do usuário (usado para login e comunicação) */
  email: string;
  
  /** Nome completo do usuário para exibição */
  nome: string;
  
  /** URL da foto de perfil do usuário (opcional) */
  fotoUrl?: string;
  
  /** CPF do usuário para validação e identificação fiscal */
  cpf?: string;
  
  /** Data de criação da conta no sistema */
  dataCriacao?: Date;
  
  /** Data e hora do último acesso do usuário ao sistema */
  ultimoAcesso?: Date;
  
  /** Data de nascimento do usuário (para validações de idade) */
  dataNascimento?: Date;
  
  /** Número de telefone para contato */
  telefone?: string;

  /** Numero de WhatsApp vinculado em formato internacional */
  whatsappNumero?: string;
  
  /** Endereço residencial completo */
  endereco?: string;
  
  /** Cidade de residência */
  cidade?: string;
  
  /** Estado/UF de residência */
  estado?: string;
  
  /** CEP do endereço */
  cep?: string;
  
  /** Data de cadastro no sistema (pode ser diferente de dataCriacao) */
  dataCadastro?: Date;
  
  /** Indica se a conta está ativa (true) ou desativada (false) */
  ativo?: boolean;
}

/**
 * Interface para requisição de login tradicional
 * 
 * Usada quando o usuário faz login com username/email e senha
 */
export interface LoginRequest {
  /** Nome de usuário ou email para identificação */
  username: string;
  
  /** Senha do usuário para autenticação */
  password: string;
  
  /** Email alternativo (opcional, usado em alguns fluxos) */
  email?: string;
}

/**
 * Interface para resposta de login bem-sucedido
 * 
 * Retornada pelo backend após validação das credenciais
 */
export interface LoginResponse {
  /** Token JWT para autenticação nas requisições subsequentes */
  token: string;
  
  /** Tipo do token (geralmente "Bearer") */
  type: string;
}

/**
 * Interface para requisição de login via Google OAuth2
 * 
 * Usada quando o usuário faz login através da integração com Google
 */
export interface GoogleLoginRequest {
  /** Token de ID fornecido pelo Google após autenticação OAuth2 */
  idToken: string;
}

/**
 * Interface para resposta de login via Google OAuth2
 * 
 * Retornada pelo backend após validação do token Google
 */
export interface GoogleLoginResponse {
  /** Token JWT para autenticação nas requisições subsequentes */
  token: string;
  
  /** Tipo do token (geralmente "Bearer") */
  type: string;
  
  /** Dados do usuário obtidos do Google ou criados no sistema */
  user: Usuario;
}
