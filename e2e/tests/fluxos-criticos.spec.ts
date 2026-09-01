import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

test.describe.configure({ timeout: 60_000 });

const suffix = Date.now();
const password = 'SenhaTeste123!';
const api = () => process.env.E2E_API_URL ?? 'http://127.0.0.1:18081';

async function waitMatDialog(page: Page) {
  // Material MDC: container é .mat-mdc-dialog-container e nem sempre expõe role=dialog
  const dlg = page
    .locator('.mat-mdc-dialog-container, mat-dialog-container, .ce-form-dialog, [role="dialog"]')
    .last();
  await expect(dlg).toBeVisible({ timeout: 25_000 });
  await expect(
    dlg.locator('mat-dialog-content, [mat-dialog-content], .ce-form-body, input, textarea, mat-select').first()
  ).toBeVisible({ timeout: 15_000 });
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
    await dlg.locator('input[formcontrolname="nome"]').fill(`UI Cat ${suffix}`);
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
    await dlg.locator('input[formcontrolname="nome"]').fill(`Conta UI ${suffix}`);
    const saldo = dlg.locator('input[formcontrolname="saldoAtual"], input[formcontrolname="saldoInicial"]');
    if (await saldo.count()) {
      await saldo.first().fill('1000');
    }
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
    await dlg.locator('input[formcontrolname="numero"]').fill('4111111111111111');
    await dlg.locator('input[formcontrolname="titular"]').fill(`Titular ${suffix}`);
    await dlg.locator('input[formcontrolname="limite"]').fill('3000');
    await dlg.locator('mat-datepicker-toggle button').click();
    await page
      .locator('.cdk-overlay-container .mat-calendar-body-cell:not(.mat-calendar-body-disabled) .mat-calendar-body-cell-content')
      .first()
      .click();
    await dlg.getByRole('button', { name: /Adicionar Cartão/i }).click();
    await expect(page.locator('.cartao-item').filter({ hasText: /4111|Titular/ }).first()).toBeVisible({
      timeout: 25_000,
    });
  });

  test('criar transação pela UI', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'tx');
    const seed = await seedBasico(request, auth);
    await loginUi(page, auth.email);
    await page.goto('/transacoes');
    await page.getByRole('button', { name: /Nova Transação/i }).click();
    const dlg = await waitMatDialog(page);
    await dlg.locator('input[formcontrolname="descricao"]').fill('Despesa UI');
    await dlg.locator('input[formcontrolname="valor"]').fill('42,50');
    await dlg.locator('mat-select[formcontrolname="categoriaId"]').click();
    await page.locator('mat-option').filter({ hasText: `Cat ${suffix}` }).click();
    await dlg.locator('mat-select[formcontrolname="contaBancariaId"]').click();
    await page.locator('mat-option').filter({ hasText: 'Conta E2E' }).click();
    const savePromise = page.waitForResponse(
      (r) => r.url().includes('/api/transacoes') && r.request().method() === 'POST' && r.status() < 400
    );
    await dlg.getByRole('button', { name: /^Salvar$/ }).click();
    await savePromise;
    // Lista desktop usa .descricao-texto; mobile fica oculta no viewport padrão do Playwright
    await expect(page.locator('.descricao-texto', { hasText: 'Despesa UI' })).toBeVisible({ timeout: 25_000 });
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
    await expect(page.getByText(/Análises e Gráficos/i)).toBeVisible({ timeout: 40_000 });
    // Os 4 gráficos vivem em abas; só a aba selecionada monta o conteúdo no DOM
    const abas: Array<[RegExp, string]> = [
      [/Receitas vs Despesas/i, 'RELATORIO_PIZZA'],
      [/Evolução Temporal/i, 'RELATORIO_LINHA'],
      [/Por Categoria/i, 'RELATORIO_BARRAS'],
      [/Por Cartão/i, 'RELATORIO_ROSCA'],
    ];
    for (const [rotulo, id] of abas) {
      await page.getByRole('tab', { name: rotulo }).click();
      await expect(page.locator(`#${id}`)).toBeVisible({ timeout: 40_000 });
      await expect(page.locator('.chart-container canvas, .chart-container .chart-empty').first()).toBeVisible({
        timeout: 40_000,
      });
    }
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

  test('usuário comum é bloqueado no painel admin', async ({ page, request }) => {
    const auth = await registrarViaApi(request, 'admin');

    // API: endpoints administrativos negam usuário comum (403)
    const apiRes = await request.get(`${api()}/api/admin/evolution/health`, { headers: auth.headers });
    expect(apiRes.status()).toBe(403);

    // UI: AdminGuard redireciona a rota /admin/evolution para fora do painel
    await loginUi(page, auth.email);
    await page.goto('/admin/evolution');
    await expect(page).not.toHaveURL(/admin\/evolution/, { timeout: 20_000 });
  });

  test('família: membro não pode administrar o grupo', async ({ page, request }) => {
    const owner = await registrarViaApi(request, 'famOwner');
    const member = await registrarViaApi(request, 'famMember');

    // Owner cria o grupo e convida o membro
    const criar = await request.post(`${api()}/api/familia`, {
      headers: owner.headers,
      data: { nome: `Familia E2E ${suffix}` },
    });
    expect(criar.ok()).toBeTruthy();
    const convite = await request.post(`${api()}/api/familia/convites`, {
      headers: owner.headers,
      data: { email: member.email },
    });
    expect(convite.ok()).toBeTruthy();

    // Membro localiza e aceita o convite
    const pendentes = await request.get(`${api()}/api/familia/convites`, { headers: member.headers });
    expect(pendentes.ok()).toBeTruthy();
    const lista = await pendentes.json();
    expect(lista.length).toBeGreaterThan(0);
    const aceite = await request.post(`${api()}/api/familia/convites/${lista[0].id}/responder`, {
      headers: member.headers,
      data: { aceitar: true },
    });
    expect(aceite.ok()).toBeTruthy();

    // API: membro não pode renomear nem convidar (403)
    const renomear = await request.put(`${api()}/api/familia`, {
      headers: member.headers,
      data: { nome: 'Nome Indevido' },
    });
    expect(renomear.status()).toBe(403);
    const convidar = await request.post(`${api()}/api/familia/convites`, {
      headers: member.headers,
      data: { email: `intruso.${suffix}@test.local` },
    });
    expect(convidar.status()).toBe(403);

    // UI: membro vê o grupo, sem controles de administração
    await loginUi(page, member.email);
    const grupoApi = await request.get(`${api()}/api/familia`, { headers: member.headers });
    expect(grupoApi.status()).toBe(200);
    await page.goto('/familia');
    await page.waitForResponse(
      (r) => r.url().includes('/api/familia') && !r.url().includes('/convites') && r.status() === 200,
      { timeout: 30_000 }
    );
    await expect(page.locator('.familia-page')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/2 membro\(s\)/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /Renomear grupo/i })).toHaveCount(0);
    await expect(page.getByText(/Convidar parceiro/i)).toHaveCount(0);
  });
});
