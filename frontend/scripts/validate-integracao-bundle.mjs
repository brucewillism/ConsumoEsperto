/**
 * Falha se o dist de integração contiver domínio de produção conhecido.
 * Uso: node scripts/validate-integracao-bundle.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const distDir = path.join(__dirname, '..', 'dist', 'frontend', 'browser');
const forbidden = ['consumoesperto.brucew07.com.br', 'brucew07.com.br/api'];
const required = 'localhost:18081';

if (!fs.existsSync(distDir)) {
  console.error(`Dist ausente: ${distDir}`);
  process.exit(1);
}

const files = fs.readdirSync(distDir).filter((f) => /\.(js|html|css)$/i.test(f));
let hits = [];
for (const file of files) {
  const content = fs.readFileSync(path.join(distDir, file), 'utf8');
  for (const token of forbidden) {
    if (content.includes(token)) hits.push({ file, token });
  }
}

if (hits.length) {
  console.error('Bundle de integração contém URL de produção:');
  for (const h of hits) console.error(`  ${h.file}: ${h.token}`);
  process.exit(1);
}

const mainJs = files.find((f) => f.startsWith('main-') && f.endsWith('.js'));
if (mainJs) {
  const main = fs.readFileSync(path.join(distDir, mainJs), 'utf8');
  if (!main.includes(required)) {
    console.error(`Bundle de integração não referencia API local (${required}) em ${mainJs}`);
    process.exit(1);
  }
}

console.log(`OK: ${files.length} arquivos verificados em dist/frontend/browser`);
