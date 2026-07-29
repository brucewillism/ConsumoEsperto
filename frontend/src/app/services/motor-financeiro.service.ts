import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface MotorFinanceiroInteligente {
  perfilComportamental?: {
    perfil: string;
    confiancaPct: number;
    pontuacaoPorPerfil?: Record<string, number>;
  };
  forecastInteligente?: {
    saldoPrevisto: number;
    despesasPrevistas: number;
    receitasPrevistas: number;
    chanceMesPositivoPct: number;
    chanceChequeEspecialPct: number;
    chanceEstourarOrcamentoPct: number;
    explicacaoDeterministica: string;
    explicacaoNarrativa?: string;
  };
  scoreExplicavel?: {
    scoreTotal: number;
    componentes: Array<{
      nome: string;
      pontos: number;
      maximo: number;
      detalhe: string;
      comoRecuperar: string;
    }>;
  };
  metasInteligentes?: Array<{
    metaId: number;
    descricao: string;
    probabilidadeSucessoPct: number;
    ritmoAtualMensal: number;
    ritmoNecessarioMensal: number;
    diferencaMensal: number;
    recomendacaoDeterministica: string;
  }>;
  advisorInvestimento?: {
    perfilInvestidor: string;
    produtosCompativeis: string[];
    textoDeterministico: string;
    avisoLegal: string;
  };
  narrativaIa?: string;
  calculadoEm?: string;
}

@Injectable({ providedIn: 'root' })
export class MotorFinanceiroService {
  private readonly base = `${environment.apiUrl}/motor-financeiro`;

  constructor(private http: HttpClient) {}

  obter(narrativa = false): Observable<MotorFinanceiroInteligente> {
    return this.http.get<MotorFinanceiroInteligente>(this.base, {
      params: { narrativa: String(narrativa), persistirPerfil: 'true' },
    });
  }

  historicoPerfil(): Observable<Array<Record<string, unknown>>> {
    return this.http.get<Array<Record<string, unknown>>>(`${this.base}/perfil/historico`);
  }
}
