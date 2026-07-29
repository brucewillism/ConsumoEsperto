import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

test.describe.configure({ timeout: 60_000 });

const suffix = Date.now();
const password = 'SenhaTeste123!';
const api = () => process.env.E2E_API_URL ?? 'http://localhost:18081';

async function waitMatDialog(page: Page) {
  const dlg = page.locator('.cdk-overlay-container mat-dialog-container');
  await expect(dlg).toBeVisible({ timeout: 25_000 });
  await expect(dlg.locator('[mat-dialog-title], h2').first()).toBeVisible({ timeout: 10_000 });
  return dlg;
}

async function waitAgModal(page: Page) {
  const modal = page.locator('.agendamentos-page .modal');
  await expect(modal).toBeVisible({ timeout: 20_000 });
  return modal;
}

async function registrarViaApi(request: APIRequestContext, tag: string) {
  const email = `e2e.${tag}.${suffix}@test.local`;
  const reg = await request.post(`${api()}/api/auth/registro`, {
    data: { username: email, email, password, nome: `E2E ${tag}` },
  });
  expect(reg.ok()).toBeTruthy();
  const login = await request.post(`${api()}/api/auth/login`, {
    data: { username: email, password },
  });
  expect(login.ok()).toBeTruthy();
  const { token } = await login.json();
  return { email, token, headers: { Authorization: `Bearer ${token}` } };
}

async function loginUi(page: Page, email: string, pass = password) {
  await page.goto('/login');
  await page.locator('#login, input[formcontrolname="login"]').fill(email);
  await page.locator('input[formcontrolname="password"]').fill(pass);
  await page.locator('button.login-btn, button[type="submit"]').click();
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 25_000 });
}

async function seedBasico(request: APIRequestContext, auth: { headers: Record<string, string> }) {
  const cat = await request.post(`${api()}/api/categorias`, {
    headers: auth.headers,
    data: { nome: `Cat ${suffix}`, descricao: '', cor: '#336699', icone: 'tag' },
  });
  expect(cat.ok()).toBeTruthy();
  const catId = (await cat.json()).id;

  const conta = await request.post(`${api()}/api/contas-bancarias`, {
    headers: auth.headers,
    data: { nome: 'Conta E2E', tipo: 'CORRENTE', saldoAtual: 5000, limiteChequeEspecial: 0, ativa: true, padrao: true },
  });
  expect(conta.ok()).toBeTruthy();
  const contaId = (await conta.json()).id;

  const cartao = await request.post(`${api()}/api/cartoes-credito`, {
    headers: auth.headers,
    data: {
      nome: 'Cartao E2E',
      banco: 'Teste',
      numeroCartao: '4111111111111111',
      limiteCredito: 5000,
      limiteDisponivel: 5000,
      diaVencimento: 10,
      ativo: true,
    },
  });
  expect(cartao.ok()).toBeTruthy();
  const cartaoId = (await cartao.json()).id;

  return { catId, contaId, cartaoId };
}

test.describe('Fluxos críticos UI', () => {
  test('logout encerra sessão', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'logout');
    await loginUi(page, auth.email);
    await page.goto('/dashboard');
    await page.locator('button.logout-btn, button:has-text("Sair")').first().click();
    await expect(page).toHaveURL(/login/, { timeout: 15_000 });
    await page.goto('/relatorios');
    await expect(page).toHaveURL(/login/, { timeout: 15_000 });
  });

  test('criar categoria pela UI', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'cat');
    await loginUi(page, auth.email);
    await page.goto('/categorias');
    await expect(page.locator('.categorias-page')).toBeVisible({ timeout: 15_000 });
    await page.getByRole('button', { name: /Nova categoria/i }).click();
    const dlg = await waitMatDialog(page);
    await dlg.getByRole('textbox', { name: /Nome/i }).fill(`UI Cat ${suffix}`);
    await dlg.getByRole('button', { name: /^Salvar$/ }).click();
    await expect(page.getByText(`UI Cat ${suffix}`)).toBeVisible({ timeout: 20_000 });
  });

  test('criar conta pela UI', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'conta');
    await loginUi(page, auth.email);
    await page.goto('/contas');
    await expect(page.locator('.contas-page')).toBeVisible({ timeout: 15_000 });
    await page.getByRole('button', { name: /Nova conta/i }).click();
    const dlg = await waitMatDialog(page);
    await dlg.getByRole('textbox', { name: /^Nome$/i }).fill(`Conta UI ${suffix}`);
    await dlg.getByRole('textbox', { name: /Saldo inicial/i }).fill('1000');
    await dlg.getByRole('button', { name: /^Salvar$/ }).click();
    await expect(page.getByText(`Conta UI ${suffix}`)).toBeVisible({ timeout: 20_000 });
  });

  test('criar cartão pela UI', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'cartao');
    await loginUi(page, auth.email);
    await page.goto('/cartoes');
    await page.getByRole('button', { name: /Novo Cartão/i }).click();
    const dlg = await waitMatDialog(page);
    await dlg.locator('mat-select[formcontrolname="banco"]').click();
    await page.locator('mat-option').first().click();
    await dlg.getByRole('textbox', { name: /Número do Cartão/i }).fill('4111111111111111');
    await dlg.getByRole('textbox', { name: /Nome do Titular/i }).fill(`Titular ${suffix}`);
    await dlg.getByRole('textbox', { name: /Limite de Crédito/i }).fill('3000');
    await dlg.getByRole('textbox', { name: /Data de Vencimento/i }).click();
    await page.locator('.mat-calendar-body-cell-content').first().click();
    await dlg.getByRole('button', { name: /Adicionar Cartão/i }).click();
    await expect(page.locator('mat-card').filter({ hasText: /4111|Titular/ }).first()).toBeVisible({ timeout: 25_000 });
  });

  test('criar transação pela UI', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'tx');
    const seed = await seedBasico(request, auth);
    await loginUi(page, auth.email);
    await page.goto('/transacoes');
    await page.getByRole('button', { name: /Nova Transação/i }).click();
    const dlg = await waitMatDialog(page);
    await dlg.getByRole('textbox', { name: /Descrição/i }).fill('Despesa UI');
    await dlg.getByRole('textbox', { name: /Valor/i }).fill('42,50');
    await dlg.locator('mat-select[formcontrolname="categoriaId"]').click();
    await page.locator('mat-option').filter({ hasText: `Cat ${suffix}` }).click();
    await dlg.getByRole('button', { name: /^Salvar$/ }).click();
    await expect(page.getByText('Despesa UI')).toBeVisible({ timeout: 25_000 });
    expect(seed.catId).toBeTruthy();
  });

  test('criar transação parcelada via API autenticada', async ({ request }) => {
    const auth = await registrarViaApi(request, 'parcelada');
    const seed = await seedBasico(request, auth);
    const dt = new Date().toISOString().slice(0, 19);
    const tx = await request.post(`${api()}/api/transacoes`, {
      headers: auth.headers,
      data: {
        descricao: 'Parcelada E2E',
        valor: 300,
        tipoTransacao: 'DESPESA',
        categoriaId: seed.catId,
        contaBancariaId: seed.contaId,
        dataTransacao: dt,
        statusConferencia: 'CONFIRMADA',
        numeroParcelas: 3,
        parcelaAtual: 1,
      },
    });
    expect(tx.ok()).toBeTruthy();
    const body = await tx.json();
    expect(body.numeroParcelas ?? body.parcelaAtual).toBeTruthy();
  });

  test('relatórios exibe quatro gráficos com dados', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'graficos');
    const seed = await seedBasico(request, auth);
    const dt = new Date().toISOString().slice(0, 19);
    await request.post(`${api()}/api/transacoes`, {
      headers: auth.headers,
      data: {
        descricao: 'Receita graf',
        valor: 200,
        tipoTransacao: 'RECEITA',
        categoriaId: seed.catId,
        contaBancariaId: seed.contaId,
        dataTransacao: dt,
        statusConferencia: 'CONFIRMADA',
      },
    });
    await request.post(`${api()}/api/transacoes`, {
      headers: auth.headers,
      data: {
        descricao: 'Despesa graf',
        valor: 100,
        tipoTransacao: 'DESPESA',
        categoriaId: seed.catId,
        contaBancariaId: seed.contaId,
        dataTransacao: dt,
        statusConferencia: 'CONFIRMADA',
      },
    });
    await loginUi(page, auth.email);
    await page.goto('/relatorios');
    await page.getByRole('button', { name: /Gerar Relatório/i }).click();
    for (const id of ['RELATORIO_PIZZA', 'RELATORIO_LINHA', 'RELATORIO_BARRAS', 'RELATORIO_ROSCA']) {
      await expect(page.locator(`app-chart-metodologia#${id}`)).toBeVisible({ timeout: 40_000 });
    }
    await expect(page.locator('.chart-container canvas')).toHaveCount(4, { timeout: 40_000 });
  });

  test('aplicar filtros no relatório', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'filtros');
    await seedBasico(request, auth);
    await loginUi(page, auth.email);
    await page.goto('/relatorios');
    await page.locator('mat-select[formcontrolname="tipoTransacao"]').click();
    await page.locator('mat-option').filter({ hasText: 'Despesas' }).click();
    await page.getByRole('button', { name: /Gerar Relatório/i }).click();
    await expect(page.locator('app-relatorios')).toBeVisible();
    await expect(page.locator('.chart-container, app-chart-metodologia').first()).toBeVisible({ timeout: 20_000 });
  });

  test('exportar CSV com download válido', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'csv');
    const seed = await seedBasico(request, auth);
    const dt = new Date().toISOString().slice(0, 19);
    await request.post(`${api()}/api/transacoes`, {
      headers: auth.headers,
      data: {
        descricao: 'Linha CSV',
        valor: 10,
        tipoTransacao: 'DESPESA',
        categoriaId: seed.catId,
        contaBancariaId: seed.contaId,
        dataTransacao: dt,
        statusConferencia: 'CONFIRMADA',
      },
    });
    await loginUi(page, auth.email);
    await page.goto('/relatorios');
    const downloadPromise = page.waitForEvent('download', { timeout: 30_000 });
    await page.getByRole('button', { name: /Exportar CSV/i }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.csv$/i);
    const tmp = path.join(os.tmpdir(), `e2e-${suffix}.csv`);
    await download.saveAs(tmp);
    const stat = fs.statSync(tmp);
    expect(stat.size).toBeGreaterThan(10);
    const content = fs.readFileSync(tmp, 'utf8');
    expect(content).toContain('DESPESA');
    fs.unlinkSync(tmp);
  });

  test('exportar PDF mensal com %PDF', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'pdf');
    const seed = await seedBasico(request, auth);
    const dt = new Date().toISOString().slice(0, 19);
    await request.post(`${api()}/api/transacoes`, {
      headers: auth.headers,
      data: {
        descricao: 'Linha PDF',
        valor: 15,
        tipoTransacao: 'DESPESA',
        categoriaId: seed.catId,
        contaBancariaId: seed.contaId,
        dataTransacao: dt,
        statusConferencia: 'CONFIRMADA',
      },
    });
    await loginUi(page, auth.email);
    await page.goto('/relatorios');
    const downloadPromise = page.waitForEvent('download', { timeout: 60_000 });
    await page.getByRole('button', { name: /Exportar PDF mensal/i }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.pdf$/i);
    const tmp = path.join(os.tmpdir(), `e2e-${suffix}.pdf`);
    await download.saveAs(tmp);
    const buf = fs.readFileSync(tmp);
    expect(buf.slice(0, 4).toString()).toBe('%PDF');
    expect(buf.length).toBeGreaterThan(100);
    fs.unlinkSync(tmp);
  });

  test('agendamento: criar, editar, pausar, ativar, executar, histórico, cancelar', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'ag');
    await seedBasico(request, auth);
    await loginUi(page, auth.email);
    await page.goto('/agendamentos');
    await page.getByRole('button', { name: /Novo agendamento/i }).click();
    const modal = await waitAgModal(page);
    await modal.locator('input').first().fill(`Benef ${suffix}`);
    await modal.locator('input[type="number"]').fill('55');
    await modal.locator('mat-select').first().click();
    await page.locator('mat-option').first().click();
    const venc = new Date();
    venc.setDate(venc.getDate() + 7);
    await modal.locator('input[type="date"]').fill(venc.toISOString().slice(0, 10));
    await modal.locator('.modal-acoes button.mat-primary').click();
    await expect(page.getByText(`Benef ${suffix}`)).toBeVisible({ timeout: 25_000 });

    await page.getByRole('button', { name: 'Editar' }).first().click();
    const editModal = await waitAgModal(page);
    await editModal.locator('input').first().fill(`Benef Edit ${suffix}`);
    await editModal.locator('.modal-acoes button.mat-primary').click();
    await expect(page.getByText(`Benef Edit ${suffix}`)).toBeVisible({ timeout: 20_000 });

    await page.getByRole('button', { name: 'Pausar' }).first().click();
    await expect(page.getByRole('button', { name: 'Ativar' }).first()).toBeVisible({ timeout: 20_000 });

    await page.getByRole('button', { name: 'Ativar' }).first().click();
    await page.getByRole('button', { name: 'Executar' }).first().click();
    await page.getByRole('tab', { name: 'Histórico' }).click();
    await expect(page.locator('.historico-list .hist-item, .hist-item').first()).toBeVisible({ timeout: 30_000 });

    await page.getByRole('tab', { name: 'Ativos' }).click();
    page.once('dialog', (d) => d.accept());
    await page.getByRole('button', { name: 'Cancelar' }).first().click();
  });

  test('sessão inválida redireciona ao login', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'sessao');
    await loginUi(page, auth.email);
    await page.evaluate(() => localStorage.setItem('token', 'token-invalido-e2e'));
    await page.goto('/relatorios');
    await expect(page).toHaveURL(/login/, { timeout: 20_000 });
  });

  test('isolamento visual entre usuários', async ({ page, request }) => {
    const authA = await registrarViaApi(request, 'isoA');
    const authB = await registrarViaApi(request, 'isoB');
    const seedA = await seedBasico(request, authA);
    await request.post(`${api()}/api/transacoes`, {
      headers: authA.headers,
      data: {
        descricao: 'Segredo777',
        valor: 777,
        tipoTransacao: 'DESPESA',
        categoriaId: seedA.catId,
        contaBancariaId: seedA.contaId,
        dataTransacao: new Date().toISOString().slice(0, 19),
        statusConferencia: 'CONFIRMADA',
      },
    });
    await loginUi(page, authB.email);
    await page.goto('/transacoes');
    await expect(page.getByText('Segredo777')).not.toBeVisible({ timeout: 10_000 });
  });
});
