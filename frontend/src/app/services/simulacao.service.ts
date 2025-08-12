import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SimulacaoCartao {
  cartaoId: number;
  valorCompra: number;
  parcelas: number;
  taxaJuros: number;
}

export interface SimulacaoInvestimento {
  valorInicial: number;
  aporteMensal: number;
  taxaRetorno: number;
  periodo: number;
}

export interface SimulacaoFinanciamento {
  valorBem: number;
  entrada: number;
  taxaJuros: number;
  prazo: number;
}

export interface ResultadoSimulacaoCartao {
  valorCompra: number;
  parcelas: number;
  valorParcela: number;
  totalComJuros: number;
  jurosTotais: number;
  limiteDisponivel: number;
  percentualUtilizacao: number;
}

export interface ResultadoSimulacaoInvestimento {
  valorInicial: number;
  totalAportes: number;
  jurosCompostos: number;
  valorFinal: number;
  projecaoAnual: any[];
}

export interface ResultadoSimulacaoFinanciamento {
  valorBem: number;
  entrada: number;
  valorFinanciado: number;
  valorParcela: number;
  totalPagar: number;
  totalJuros: number;
}

@Injectable({
  providedIn: 'root'
})
export class SimulacaoService {
  private apiUrl = `${environment.apiUrl}/simulacoes`;

  constructor(private http: HttpClient) { }

  simularCartao(simulacao: SimulacaoCartao): Observable<ResultadoSimulacaoCartao> {
    return this.http.post<ResultadoSimulacaoCartao>(`${this.apiUrl}/cartao`, simulacao);
  }

  simularInvestimento(simulacao: SimulacaoInvestimento): Observable<ResultadoSimulacaoInvestimento> {
    return this.http.post<ResultadoSimulacaoInvestimento>(`${this.apiUrl}/investimento`, simulacao);
  }

  simularFinanciamento(simulacao: SimulacaoFinanciamento): Observable<ResultadoSimulacaoFinanciamento> {
    return this.http.post<ResultadoSimulacaoFinanciamento>(`${this.apiUrl}/financiamento`, simulacao);
  }

  // Simulações locais para quando a API não estiver disponível
  simularCartaoLocal(simulacao: SimulacaoCartao, cartao: any): ResultadoSimulacaoCartao {
    const valorCompra = simulacao.valorCompra;
    const parcelas = simulacao.parcelas;
    const taxaJuros = simulacao.taxaJuros / 100;
    
    let valorParcela: number;
    let totalComJuros: number;
    let jurosTotais: number;
    
    if (parcelas <= 3) {
      // Sem juros
      valorParcela = valorCompra / parcelas;
      totalComJuros = valorCompra;
      jurosTotais = 0;
    } else {
      // Com juros
      valorParcela = valorCompra * (taxaJuros * Math.pow(1 + taxaJuros, parcelas)) / (Math.pow(1 + taxaJuros, parcelas) - 1);
      totalComJuros = valorParcela * parcelas;
      jurosTotais = totalComJuros - valorCompra;
    }
    
    const limiteDisponivel = cartao.limite - cartao.limiteUtilizado;
    const percentualUtilizacao = ((cartao.limiteUtilizado + valorCompra) / cartao.limite) * 100;
    
    return {
      valorCompra,
      parcelas,
      valorParcela,
      totalComJuros,
      jurosTotais,
      limiteDisponivel,
      percentualUtilizacao
    };
  }

  simularInvestimentoLocal(simulacao: SimulacaoInvestimento): ResultadoSimulacaoInvestimento {
    const valorInicial = simulacao.valorInicial;
    const aporteMensal = simulacao.aporteMensal || 0;
    const taxaRetorno = simulacao.taxaRetorno / 100;
    const periodo = simulacao.periodo;
    
    const taxaMensal = Math.pow(1 + taxaRetorno, 1/12) - 1;
    const totalAportes = valorInicial + (aporteMensal * periodo * 12);
    
    // Cálculo do valor final com juros compostos
    let valorFinal = valorInicial;
    const projecaoAnual: any[] = [];
    
    for (let ano = 1; ano <= periodo; ano++) {
      const saldoInicial = valorFinal;
      const aportes = aporteMensal * 12;
      
      // Aplicar juros compostos mensalmente
      for (let mes = 1; mes <= 12; mes++) {
        valorFinal = (valorFinal + aporteMensal) * (1 + taxaMensal);
      }
      
      const juros = valorFinal - saldoInicial - aportes;
      
      projecaoAnual.push({
        ano,
        saldoInicial,
        aportes,
        juros,
        saldoFinal: valorFinal
      });
    }
    
    const jurosCompostos = valorFinal - totalAportes;
    
    return {
      valorInicial,
      totalAportes,
      jurosCompostos,
      valorFinal,
      projecaoAnual
    };
  }

  simularFinanciamentoLocal(simulacao: SimulacaoFinanciamento): ResultadoSimulacaoFinanciamento {
    const valorBem = simulacao.valorBem;
    const entrada = simulacao.entrada || 0;
    const taxaJuros = simulacao.taxaJuros / 100;
    const prazo = simulacao.prazo;
    
    const valorFinanciado = valorBem - entrada;
    
    // Cálculo da parcela usando a fórmula de financiamento
    const valorParcela = valorFinanciado * (taxaJuros * Math.pow(1 + taxaJuros, prazo)) / (Math.pow(1 + taxaJuros, prazo) - 1);
    const totalPagar = valorParcela * prazo;
    const totalJuros = totalPagar - valorFinanciado;
    
    return {
      valorBem,
      entrada,
      valorFinanciado,
      valorParcela,
      totalPagar,
      totalJuros
    };
  }
}
