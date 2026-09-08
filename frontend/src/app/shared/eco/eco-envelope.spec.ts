import { ecoTraceId, ecoSpanId, ecoUlid } from './eco-envelope';

describe('eco-envelope', () => {
  it('gera ULID Crockford com prefixo de contrato', () => {
    expect(ecoUlid().length).toBe(26);
    expect(ecoTraceId().startsWith('tr_')).toBeTrue();
    expect(ecoSpanId().startsWith('sp_')).toBeTrue();
    expect(ecoTraceId().length).toBe(29);
  });
});
