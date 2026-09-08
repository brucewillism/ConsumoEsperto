import {
  CONSUMO_APPLICATION_ID,
  JARVIS_CHAT_SUGESTOES,
  rotuloEstadoJarvis,
} from './jarvis-chat.util';

describe('jarvis-chat.util', () => {
  it('botão de cartões aponta para capability local', () => {
    const cards = JARVIS_CHAT_SUGESTOES.find((s) => s.rotulo.includes('cartões'));
    expect(cards?.capability).toBe('finance.cards.list');
  });

  it('botão de fechamento do mês aponta para capability local', () => {
    const month = JARVIS_CHAT_SUGESTOES.find((s) => s.rotulo.includes('fechar'));
    expect(month?.capability).toBe('finance.month.summary');
  });

  it('pergunta em texto livre não tem capability id', () => {
    const livre = JARVIS_CHAT_SUGESTOES.find((s) => s.rotulo.includes('invisto'));
    expect(livre?.capability).toBeUndefined();
  });

  it('application_id do contexto é consumo-esperto', () => {
    expect(CONSUMO_APPLICATION_ID).toBe('consumo-esperto');
  });

  it('rótulos de estado não expõem detalhe técnico', () => {
    expect(rotuloEstadoJarvis('ONLINE')).toBe('Online');
    expect(rotuloEstadoJarvis('LOCAL')).toBe('Modo local');
    expect(rotuloEstadoJarvis('DEGRADED')).toBe('Modo degradado');
    expect(rotuloEstadoJarvis('EDITH_UNAVAILABLE')).toBe('E.D.I.T.H. indisponível');
  });
});
