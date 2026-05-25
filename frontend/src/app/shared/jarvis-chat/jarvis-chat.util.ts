import { Usuario } from '../../models/usuario.model';

export interface JarvisChatSugestao {
  rotulo: string;
  pergunta: string;
}

export const JARVIS_CHAT_SUGESTOES: JarvisChatSugestao[] = [
  { rotulo: 'Listar meus cartões', pergunta: 'Lista os meus cartões' },
  { rotulo: 'Como vou fechar o mês?', pergunta: 'Como vou fechar o mês?' },
  { rotulo: 'Onde invisto meu saldo?', pergunta: 'Onde invisto meu saldo?' },
  { rotulo: 'Ajuda (menu)', pergunta: 'ajuda' },
];

export function vocativoJarvis(usuario: Usuario | null | undefined): string {
  const resumo = usuario?.jarvisTratamentoResumo?.trim();
  if (resumo) {
    return resumo;
  }
  const nome = usuario?.nome?.trim();
  const primeiro = nome ? nome.split(/\s+/)[0] : '';
  const pref = usuario?.preferenciaTratamentoJarvis;
  switch (pref) {
    case 'SENHOR':
      return primeiro ? `Senhor ${primeiro}` : 'Senhor';
    case 'SENHORA':
      return primeiro ? `Senhora ${primeiro}` : 'Senhora';
    case 'DOUTOR':
      return primeiro ? `Doutor ${primeiro}` : 'Doutor';
    case 'DOUTORA':
      return primeiro ? `Doutora ${primeiro}` : 'Doutora';
    case 'NENHUM':
      return primeiro || '';
    default:
      return 'Senhor(a)';
  }
}

export function mensagemBoasVindasJarvis(usuario: Usuario | null | undefined): string {
  const voc = vocativoJarvis(usuario);
  const saudacao = voc ? `Olá, ${voc}.` : 'Olá, Senhor(a).';
  return (
    `${saudacao} O ecossistema do ConsumoEsperto está totalmente operacional. ` +
    'Como posso auxiliá-lo com a engenharia das suas finanças hoje?'
  );
}

export function normalizarMensagemChat(texto: string): string {
  return texto
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .join(' ')
    .trim();
}

export function mensagemDigitandoJarvis(): string {
  return 'Consultando o núcleo de inferência do ecossistema operacional…';
}

export function mensagemErroJarvis(): string {
  return (
    'Peço desculpas, Senhor — o núcleo de inferência não respondeu neste momento. ' +
    'Verifique a conexão com o ecossistema operacional e tente novamente.'
  );
}

export function mensagemRespostaVaziaJarvis(vocativo?: string): string {
  const v = vocativo?.trim() || 'Senhor';
  return `Os protocolos não devolveram texto neste momento, ${v}.`;
}
