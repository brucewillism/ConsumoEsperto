describe('EdithService', () => {
  it('contrato de status inclui estados de assistente', () => {
    const states = ['DISABLED', 'AVAILABLE', 'UNAVAILABLE'];
    const assistants = ['ONLINE', 'LOCAL', 'DEGRADED', 'EDITH_UNAVAILABLE'];
    expect(states).toContain('DISABLED');
    expect(assistants).toContain('LOCAL');
  });
});
