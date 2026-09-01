describe('EdithService', () => {
  it('placeholder de contrato SSE/status', () => {
    const states = ['DISABLED', 'AVAILABLE', 'UNAVAILABLE'];
    expect(states).toContain('DISABLED');
  });
});
