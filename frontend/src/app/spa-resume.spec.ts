import { isSafeSpaResumePath, restoreSpaResumePath } from './spa-resume';

describe('spa-resume', () => {
  it('aceita caminhos relativos da SPA', () => {
    expect(isSafeSpaResumePath('/login')).toBeTrue();
    expect(isSafeSpaResumePath('/transacoes?conta=1')).toBeTrue();
  });

  it('rejeita open redirect', () => {
    expect(isSafeSpaResumePath('https://evil.example/phish')).toBeFalse();
    expect(isSafeSpaResumePath('//evil.example')).toBeFalse();
  });

  it('restaura o caminho a partir de ce_resume', () => {
    const replaceState = spyOn(window.history, 'replaceState');
    const restored = restoreSpaResumePath('?ce_resume=%2Fdashboard');
    expect(restored).toBe('/dashboard');
    expect(replaceState).toHaveBeenCalled();
  });
});
