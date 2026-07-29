import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(join(dir, 'chart-metodologias.ts'), 'utf8');

assert.ok(!src.includes('em implementação'), 'Metodologias não devem marcar relatórios como em implementação');
assert.ok(src.includes('RELATORIO_PIZZA'), 'Deve documentar gráfico pizza');
assert.ok(src.includes('Transações do período'), 'Fonte do gráfico pizza deve referenciar transações');

console.log('chart-metodologias.spec: OK');
