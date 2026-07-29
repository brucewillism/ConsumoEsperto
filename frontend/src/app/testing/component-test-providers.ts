import { EnvironmentProviders, Provider } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { WhatsappParidadeService } from '../services/whatsapp-paridade.service';

/** Providers comuns para componentes que incluem WhatsappParityHintComponent. */
export function whatsappParityTestProviders(): Array<Provider | EnvironmentProviders> {
  return [
    provideRouter([]),
    {
      provide: WhatsappParidadeService,
      useValue: {
        listarPorRota: () => of({ itens: [] }),
        listarTudo: () => of({ itens: [] }),
        invalidarCache: () => undefined,
      },
    },
  ];
}
