import { test, expect } from '@playwright/test';

/**
 * E2E mínimo E.D.I.T.H. — requer EDITH_ENABLED=true e mock na borda externa em CI.
 * Localmente permanece skipped quando assistente desabilitado.
 */
test.describe('E.D.I.T.H. assistente (mock externo)', () => {
  test.skip(true, 'Ativar quando pipeline CI mockar EDITH_BASE_URL');

  test('login e status do assistente', async ({ page }) => {
    await page.goto('/');
    // placeholder — expandir quando mock EDITH estiver no workflow e2e-ci.yml
    await expect(page).toHaveTitle(/Consumo/i);
  });
});
