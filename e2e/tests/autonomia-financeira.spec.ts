import { test, expect, type Page, type APIRequestContext } from '@playwright/test';

test.describe.configure({ timeout: 90_000 });

const suffix = Date.now();
const password = 'SenhaTeste123!';
const api = () => process.env.E2E_API_URL ?? 'http://localhost:18081';

async function registrarELogin(request: APIRequestContext, page: Page, tag: string) {
  const email = `e2e.autonomy.${tag}.${suffix}@test.local`;
  const reg = await request.post(`${api()}/api/auth/registro`, {
    data: { username: email, email, password, nome: `E2E Autonomy ${tag}` },
  });
  expect(reg.ok(), `registro ${reg.status()}`).toBeTruthy();
  await page.goto('/login');
  await page.keyboard.press('Escape').catch(() => undefined);
  await page.locator('#login, input[formcontrolname="login"]').fill(email);
  await page.locator('input[formcontrolname="password"]').fill(password);
  await page.locator('button.login-btn, button[type="submit"]').click({ force: true });
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 25_000 });
  const login = await request.post(`${api()}/api/auth/login`, {
    data: { username: email, password },
  });
  expect(login.ok()).toBeTruthy();
  const { token } = await login.json();
  return { email, token };
}

test.describe('Autonomia financeira (backend real)', () => {
  test('perfil ativa níveis e revisão/dashboard leem o backend', async ({ page, request }) => {
    const { token } = await registrarELogin(request, page, 'ui');
    const auth = { Authorization: `Bearer ${token}` };

    await page.goto('/perfil');
    const box = page.locator('.alert-jarvis', { hasText: 'Autonomia financeira' });
    await expect(box.locator('#nivelAutonomia')).toBeVisible({ timeout: 20_000 });

    for (const nivel of ['AUTONOMOUS_SAFE', 'ASSISTED', 'MANUAL'] as const) {
      await box.locator('#nivelAutonomia').selectOption(nivel);
      await box.getByRole('button', { name: /Guardar/i }).click();
      await expect(box.locator('#nivelAutonomia')).toHaveValue(nivel, { timeout: 15_000 });
    }

    const prefs = await request.get(`${api()}/api/autonomy/preferencias`, { headers: auth });
    expect(prefs.ok()).toBeTruthy();
    const prefJson = await prefs.json();
    expect(prefJson.nivel).toBe('MANUAL');

    await page.goto('/dashboard');
    await expect(page.locator('.dashboard-container, app-dashboard').first()).toBeVisible({ timeout: 20_000 });

    await page.goto('/revisao');
    await expect(page.locator('h1')).toContainText(/Revisão/i, { timeout: 20_000 });
    await expect(page.locator('.review-page')).toBeVisible();
  });

  test('ingestão real → revisão → correção de categoria → regra local', async ({ page, request }) => {
    const { token } = await registrarELogin(request, page, 'fluxo');
    const auth = { Authorization: `Bearer ${token}` };

    await page.goto('/perfil');
    const box = page.locator('.alert-jarvis', { hasText: 'Autonomia financeira' });
    await expect(box.locator('#nivelAutonomia')).toBeVisible({ timeout: 20_000 });
    await box.locator('#nivelAutonomia').selectOption('AUTONOMOUS_SAFE');
    const classificar = box.locator('input[name="classificarAuto"]');
    if (!(await classificar.isChecked())) {
      await classificar.check();
    }
    const aprender = box.locator('input[name="aprenderCategorias"]');
    if (!(await aprender.isChecked())) {
      await aprender.check();
    }
    await box.getByRole('button', { name: /Guardar/i }).click();
    await expect(box.locator('#nivelAutonomia')).toHaveValue('AUTONOMOUS_SAFE', { timeout: 15_000 });

    const catComb = await request.post(`${api()}/api/categorias`, {
      headers: auth,
      data: { nome: 'Combustivel', descricao: 'E2E', cor: '#cc5500', icone: 'gas' },
    });
    const catTransp = await request.post(`${api()}/api/categorias`, {
      headers: auth,
      data: { nome: 'Transporte', descricao: 'E2E', cor: '#336699', icone: 'bus' },
    });
    expect(catComb.ok()).toBeTruthy();
    expect(catTransp.ok()).toBeTruthy();
    const combustivel = await catComb.json();
    const transporte = await catTransp.json();

    const device = await request.post(`${api()}/api/mobile-capture/devices`, {
      headers: auth,
      data: { name: 'iPhone E2E', platform: 'IOS_SHORTCUTS' },
    });
    expect(device.ok()).toBeTruthy();
    const { deviceToken } = await device.json();

    const ingest = await request.post(`${api()}/api/ingestion/mobile/transactions`, {
      headers: { 'X-CE-Device-Token': deviceToken, 'Content-Type': 'application/json' },
      data: {
        source: 'IOS_WALLET',
        merchant: 'PADARIA CENTRAL E2E',
        amount: 22.4,
        occurred_at: new Date().toISOString().slice(0, 19),
        client_event_id: `e2e-padaria-${suffix}`,
      },
    });
    expect(ingest.ok(), `ingest ${ingest.status}`).toBeTruthy();
    const ingested = await ingest.json();
    expect(ingested.transacaoId).toBeTruthy();

    await page.waitForTimeout(8000);

    await page.goto('/dashboard');
    await expect(page.locator('.dashboard-container, app-dashboard').first()).toBeVisible({ timeout: 20_000 });

    await page.goto('/revisao');
    await expect(page.locator('h1')).toContainText(/Revisão/i, { timeout: 20_000 });
    const reviewHint = page.locator('.hint, .event-card');
    await expect(reviewHint.first()).toBeVisible({ timeout: 15_000 });

    const tx = await request.get(`${api()}/api/transacoes/${ingested.transacaoId}`, { headers: auth });
    expect(tx.ok()).toBeTruthy();
    const txJson = await tx.json();
    txJson.categoriaId = transporte.id;
    const put = await request.put(`${api()}/api/transacoes/${ingested.transacaoId}`, {
      headers: auth,
      data: txJson,
    });
    expect(put.ok(), `put categoria ${put.status}`).toBeTruthy();

    await page.goto('/transacoes');
    await expect(page.getByRole('heading', { name: /Transações/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/PADARIA CENTRAL/i).filter({ visible: true }).first()).toBeVisible({ timeout: 20_000 });

    await page.waitForTimeout(3000);
    const ingest2 = await request.post(`${api()}/api/ingestion/mobile/transactions`, {
      headers: { 'X-CE-Device-Token': deviceToken, 'Content-Type': 'application/json' },
      data: {
        source: 'IOS_WALLET',
        merchant: 'PADARIA CENTRAL E2E',
        amount: 18.0,
        occurred_at: new Date().toISOString().slice(0, 19),
        client_event_id: `e2e-padaria-2-${suffix}`,
      },
    });
    expect(ingest2.ok()).toBeTruthy();
    const ingested2 = await ingest2.json();
    await expect.poll(async () => {
      const tx2 = await request.get(`${api()}/api/transacoes/${ingested2.transacaoId}`, { headers: auth });
      const tx2json = await tx2.json();
      return tx2json.categoriaId;
    }, { timeout: 20_000 }).toBe(transporte.id);
  });
});
