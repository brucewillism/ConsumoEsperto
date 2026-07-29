import { test, expect, type Page, type APIRequestContext } from '@playwright/test';

const suffix = Date.now();
const email = `e2e.${suffix}@test.local`;
const password = 'SenhaTeste123!';
const api = () => process.env.E2E_API_URL ?? 'http://127.0.0.1:18081';

async function registrarViaApi(request: APIRequestContext, addr = email) {
  return request.post(`${api()}/api/auth/registro`, {
    data: { username: addr, email: addr, password, nome: `E2E ${suffix}` },
  });
}

async function loginUi(page: Page, addr = email, pass = password) {
  await page.goto('/login');
  await page.locator('#login, input[formcontrolname="login"]').fill(addr);
  await page.locator('input[formcontrolname="password"]').fill(pass);
  await page.locator('button.login-btn, button[type="submit"]').click();
}

test.describe('ConsumoEsperto E2E', () => {
  test('login exibe formulÃ¡rio', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('#login, input[formcontrolname="login"]')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('input[formcontrolname="password"]')).toBeVisible();
  });

  test('rotas protegidas redirecionam sem sessÃ£o', async ({ page }) => {
    await page.goto('/relatorios');
    await expect(page).toHaveURL(/login/, { timeout: 15_000 });
  });

  test('registro via API e login UI', async ({ page, request }) => {
    const reg = await registrarViaApi(request);
    expect(reg.ok()).toBeTruthy();
    await loginUi(page);
    await expect(page).not.toHaveURL(/\/login$/, { timeout: 25_000 });
  });

  test('motor financeiro exige autenticaÃ§Ã£o', async ({ page }) => {
    await page.goto('/motor-financeiro');
    await expect(page).toHaveURL(/login/, { timeout: 15_000 });
  });

  test('pÃ¡gina de registro acessÃ­vel', async ({ page }) => {
    await page.goto('/register');
    await expect(page.locator('input[formcontrolname="email"], input[type="email"]').first()).toBeVisible({ timeout: 15_000 });
  });

  test('fluxo autenticado: relatÃ³rios e motor financeiro', async ({ page, request }) => {
    const addr = `e2e.rel.${suffix}@test.local`;
    const reg = await registrarViaApi(request, addr);
    expect(reg.ok()).toBeTruthy();
    await loginUi(page, addr);
    await expect(page).not.toHaveURL(/\/login$/, { timeout: 25_000 });

    await page.goto('/relatorios');
    await expect(page.locator('app-relatorios')).toBeVisible({ timeout: 20_000 });

    await page.goto('/motor-financeiro');
    await expect(page.locator('app-motor-financeiro')).toBeVisible({ timeout: 20_000 });
  });

  test('agendamentos abre autenticado', async ({ page, request }) => {
    const addr = `e2e.ag.${suffix}@test.local`;
    expect((await registrarViaApi(request, addr)).ok()).toBeTruthy();
    await loginUi(page, addr);
    await expect(page).not.toHaveURL(/\/login$/, { timeout: 25_000 });
    await page.goto('/agendamentos');
    await expect(page.locator('app-agendamentos')).toBeVisible({ timeout: 20_000 });
  });

  test('reload em rota autenticada mantÃ©m sessÃ£o em relatÃ³rios', async ({ page, request }) => {
    const addr = `e2e.refresh.${suffix}@test.local`;
    const reg = await registrarViaApi(request, addr);
    expect(reg.ok()).toBeTruthy();
    await loginUi(page, addr);
    await expect(page).not.toHaveURL(/\/login$/, { timeout: 25_000 });

    await page.goto('/relatorios');
    await expect(page.locator('app-relatorios')).toBeVisible({ timeout: 20_000 });

    await page.reload();
    await expect(page).not.toHaveURL(/login/, { timeout: 20_000 });
    await expect(page).toHaveURL(/\/relatorios/, { timeout: 15_000 });
    await expect(page.locator('app-relatorios')).toBeVisible({ timeout: 20_000 });
  });
});
