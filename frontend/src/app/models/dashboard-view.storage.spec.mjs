import { describe, it } from 'node:test';
import assert from 'node:assert/strict';

const KEY = 'ce.dashboard.viewMode';

function ler(storage) {
  const raw = storage.getItem(KEY);
  return raw === 'GENERAL' ? 'GENERAL' : 'MONTHLY';
}

function gravar(mode, storage) {
  storage.setItem(KEY, mode);
}

describe('persistência da visão do dashboard', () => {
  it('padrão Mensal e persiste Geral', () => {
    const mem = {};
    const storage = {
      getItem: (k) => mem[k] ?? null,
      setItem: (k, v) => { mem[k] = v; },
    };
    assert.equal(ler(storage), 'MONTHLY');
    gravar('GENERAL', storage);
    assert.equal(ler(storage), 'GENERAL');
    assert.equal(mem[KEY], 'GENERAL');
  });
});
