export type ChartMetodologiaId =
  | 'PREVISAO_FUTURO'
  | 'SAFRA_CASCATA'
  | 'GASTOS_CATEGORIA'
  | 'RANKING_CATEGORIAS'
  | 'RENDA_CONFIG'
  | 'SCORE_EVOLUCAO'
  | 'RENDA_CONTRACHEQUES'
  | 'RELATORIO_RESUMO'
  | 'RELATORIO_PIZZA'
  | 'RELATORIO_LINHA'
  | 'RELATORIO_BARRAS'
  | 'RELATORIO_ROSCA'
  | 'RELATORIO_IR_PDF'
  | 'ORCAMENTO_FORECAST'
  | 'ORCAMENTO_PROGRESSO'
  | 'INVESTIMENTO_CARDS'
  | 'INVESTIMENTO_SIMULADOR'
  | 'TIMELINE_IMPACTO'
  | 'METAS_PROGRESSO'
  | 'PAGAMENTO_FATURA_PROJECAO';

export interface ChartMetodologia {
  titulo: string;
  itens: string[];
  fonte?: string;
}

export const CHART_METODOLOGIAS: Record<ChartMetodologiaId, ChartMetodologia> = {
  PREVISAO_FUTURO: {
    titulo: 'Trajetória de caixa',
    itens: [
      'Linha sólida (Real): saldo em contas bancárias dia a dia, ancorado nas transações confirmadas do mês corrente.',
      'Linha tracejada (Projeção): estende o ritmo médio de gastos até o último dia do mês, somando receitas previstas (salário configurado menos o já recebido) e descontando despesas fixas conhecidas.',
      'Losangos âmbar: dias de vencimento de despesas fixas cadastradas (Sentinela).',
      'Triângulos: provisões futuras registradas pela memória semântica do J.A.R.V.I.S.',
      'Área vermelha/âmbar: trecho em que a projeção indica saldo negativo (risco de descoberto).',
    ],
    fonte: 'API: GET /api/projecoes/previsao-futuro (PrevisaoFluxoCaixaService).',
  },
  SAFRA_CASCATA: {
    titulo: 'Patrimônio — Safra Cascata',
    itens: [
      'Chips M / M+1 / M+2: saldo projetado ao fim de cada mês (maio, junho, julho…).',
      'M (mês corrente): patrimônio líquido hoje + receitas previstas + receitas fiscais (13º/IR com status PREVISTO) − despesas restantes (burn rate = gasto confirmado ÷ dia atual × dias que faltam).',
      'M+1 e M+2: o saldo final do mês anterior alimenta o patrimônio inicial do seguinte (efeito cascata); burn rate fixo do mês corrente; salário e parcelas fiscais específicas de cada mês.',
      'Verde (Real): evolução diária do patrimônio no mês atual.',
      'Azul tracejado: projeção diária até o dia 31; no último dia usa o saldo final da safra M.',
      'Roxo (Safra): curva interpolada entre saldos finais de M+1 e M+2 (marcos visuais, não lançamentos diários reais).',
    ],
    fonte: 'API: GET /api/projecoes/dashboard → safraPatrimonio (SaldoService.calcularProjecaoSafra).',
  },
  GASTOS_CATEGORIA: {
    titulo: 'Gastos por Categoria',
    itens: [
      'Cada fatia = soma das despesas confirmadas do mês calendário atual, agrupadas por categoria.',
      'Lançamentos sem categoria entram como "Sem categoria".',
      'Apenas transações do tipo DESPESA entram no total; receitas e investimentos são ignoradas.',
    ],
    fonte: 'Primário: GET /api/relatorios/categoria/mes-atual. Fallback: agregação local das transações do mês.',
  },
  RANKING_CATEGORIAS: {
    titulo: 'Ranking de categorias',
    itens: [
      'Barras proporcionais ao valor gasto em cada categoria no mês atual.',
      'Variação %: comparação com o total da mesma categoria no mês anterior (quando o relatório existe).',
      'Ícone de fogo: categoria com crescimento acima do limiar de alerta de hábito.',
    ],
    fonte: 'GET /api/relatorios/categoria/mes-atual e GET /api/relatorios/categoria?ano=&mes= (mês anterior).',
  },
  RENDA_CONFIG: {
    titulo: 'Configuração de Renda',
    itens: [
      'Fatia verde: salário líquido (bruto − descontos fixos cadastrados).',
      'Fatia âmbar: total de descontos fixos (INSS, IRRF, plano de saúde etc.).',
      'Percentual exibido = descontos ÷ salário bruto × 100.',
    ],
    fonte: 'GET /api/renda-config (usuario_renda_config).',
  },
  SCORE_EVOLUCAO: {
    titulo: 'Evolução do Score',
    itens: [
      'Cada ponto = scoreResultante após um evento registrado (pagamento em dia, estouro de orçamento, meta atingida etc.).',
      'Eixo Y fixo de 0 a 1000 pontos; nível (Bronze, Prata, Ouro…) deriva do score atual.',
      'Eventos listados abaixo do gráfico detalham motivo e delta de cada alteração.',
    ],
    fonte: 'GET /api/score e GET /api/score/historico.',
  },
  RENDA_CONTRACHEQUES: {
    titulo: 'Bruto vs. Líquido (contracheques)',
    itens: [
      'Barras por competência (mês/ano) dos últimos 12 contracheques importados.',
      'Barra clara: salário bruto extraído do PDF ou informado na importação.',
      'Barra escura: salário líquido após descontos do holerite.',
      'Dados só entram após confirmação da importação (não alteram renda automaticamente).',
    ],
    fonte: 'GET /api/renda-config/contracheques.',
  },
  RELATORIO_RESUMO: {
    titulo: 'Resumo do período',
    itens: [
      'Total Receitas: soma de transações RECEITA confirmadas no intervalo filtrado.',
      'Total Despesas: soma de transações DESPESA confirmadas no intervalo.',
      'Saldo: receitas − despesas do período (não inclui saldo de contas anteriores ao intervalo).',
      'Total Transações: quantidade de lançamentos retornados após filtros (tipo, cartão, datas).',
    ],
    fonte: 'GET /api/relatorios/mensal?ano=&mes= e GET /api/transacoes (período).',
  },
  RELATORIO_PIZZA: {
    titulo: 'Receitas vs Despesas (previsto)',
    itens: [
      'Gráfico planejado: duas fatias comparando total de receitas e total de despesas do período filtrado.',
      'Considerará apenas transações confirmadas no intervalo selecionado nos filtros acima.',
    ],
    fonte: 'Mesmos dados do resumo do relatório (em implementação).',
  },
  RELATORIO_LINHA: {
    titulo: 'Evolução temporal (previsto)',
    itens: [
      'Gráfico planejado: saldo acumulado dia a dia ou mês a mês no período filtrado.',
      'Cada ponto = receitas − despesas acumuladas até aquela data.',
    ],
    fonte: 'Transações do período (em implementação).',
  },
  RELATORIO_BARRAS: {
    titulo: 'Por categoria (previsto)',
    itens: [
      'Gráfico planejado: barras com despesas agrupadas por categoria no período filtrado.',
      'Equivalente ao gráfico de pizza do dashboard, porém para o intervalo customizado.',
    ],
    fonte: 'GET /api/relatorios/categoria (em implementação).',
  },
  RELATORIO_ROSCA: {
    titulo: 'Por cartão (previsto)',
    itens: [
      'Gráfico planejado: despesas vinculadas a cada cartão de crédito no período.',
      'Inclui compras na fatura e lançamentos diretos associados ao cartão.',
    ],
    fonte: 'Transações filtradas por cartaoId (em implementação).',
  },
  RELATORIO_IR_PDF: {
    titulo: 'Relatório para IR (PDF)',
    itens: [
      'Consolida receitas tributáveis, despesas dedutíveis e investimentos do ano-calendário selecionado.',
      'Usa transações confirmadas classificadas conforme regras fiscais do app.',
      'Documento auxiliar — não substitui declaração oficial na Receita Federal.',
    ],
    fonte: 'GET /api/relatorios/exportar-ir.pdf?ano=',
  },
  ORCAMENTO_FORECAST: {
    titulo: 'Previsão do mês (orçamentos)',
    itens: [
      'Gasto atual: despesas confirmadas no mês até hoje.',
      'Média diária: gasto atual ÷ dias decorridos.',
      'Gasto projetado: média diária × total de dias do mês.',
      'Renda considerada: salário líquido da configuração de renda ou receitas confirmadas no mês.',
      'Probabilidade de fechar no vermelho: estimativa do ForecastFinanceiroService com base no ritmo de gastos vs renda.',
    ],
    fonte: 'GET /api/orcamentos/forecast (ForecastFinanceiroService + SaldoService).',
  },
  ORCAMENTO_PROGRESSO: {
    titulo: 'Barra de uso do orçamento',
    itens: [
      'Percentual = valor gasto na categoria ÷ limite mensal cadastrado × 100 (máx. 100% na barra).',
      'Valor gasto: soma de despesas confirmadas da categoria no mês/ano do orçamento.',
      'Status (Dentro do limite / Atenção / Estourado) conforme faixas configuradas no backend.',
    ],
    fonte: 'GET /api/orcamentos?mes=&ano= (OrcamentoService).',
  },
  INVESTIMENTO_CARDS: {
    titulo: 'Cards de rendimento estimado',
    itens: [
      'Simulação educativa sobre saldo ocioso detectado em conta corrente.',
      'Poupança (~0,55%/mês), Tesouro Selic (~0,85%/mês) e CDB liquidez (~0,92%/mês) — taxas ilustrativas.',
      'Valor aplicável = min(saldo ocioso, R$ 10.000) para a simulação.',
    ],
    fonte: 'GET /api/projecoes/oportunidade-investimento (SaldoService.sugerirInvestimentoSaldo).',
  },
  INVESTIMENTO_SIMULADOR: {
    titulo: 'Simulador de montante',
    itens: [
      'Projeção local com taxa fixa de 0,92% ao mês sobre o valor do slider (composto simplificado).',
      'Não considera impostos, spread bancário nem inflação.',
      'Finalidade exclusivamente educativa.',
    ],
    fonte: 'Cálculo no frontend (investimentos.component.ts).',
  },
  TIMELINE_IMPACTO: {
    titulo: 'Timeline de Impacto',
    itens: [
      'Mostra quantos meses cada meta financeira levaria originalmente vs. com simulações ativas.',
      'Deslocamento = impacto mensal das simulações ÷ valor poupado mensal da meta.',
      'Só aparece quando há simulações de gasto ativas no dashboard.',
    ],
    fonte: 'GET /api/projecoes/dashboard → timelineImpacto (SimulacaoImpactoService).',
  },
  METAS_PROGRESSO: {
    titulo: 'Anel de progresso temporal',
    itens: [
      'Percentual = tempo decorrido ÷ prazo total da meta (meses desde criação até prazo).',
      'Não mede valor financeiro acumulado — apenas se você está adiantado ou atrasado no calendário da meta.',
      'Acima de 100%: prazo original já ultrapassado ou meta com progresso temporal acelerado.',
    ],
    fonte: 'GET /api/metas-financeiras (campos prazoMeses, dataCriacao).',
  },
  PAGAMENTO_FATURA_PROJECAO: {
    titulo: 'Patrimônio projetado após pagamento',
    itens: [
      'Exibido só quando conta e cartão são do mesmo banco/provedor.',
      'Parte de saldoProjetadoFimMes da previsão de fluxo (GET /api/projecoes/previsao-futuro).',
      'Subtrai o valor da fatura para estimar patrimônio líquido ao fim do mês após o débito.',
    ],
    fonte: 'ProjecaoDashboardService.previsaoFuturo() + valor da fatura selecionada.',
  },
};
