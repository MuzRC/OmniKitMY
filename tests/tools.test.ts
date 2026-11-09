/**
 * @file tests/tools.test.ts
 * @description Ujian Unit bagi Integriti Pendaftaran Alatan (Tool Registry & Metadata Integrity).
 */

import { ALL_TOOLS } from '../src/tools/allToolsRegistry';

export function testToolRegistryIntegrity() {
  if (ALL_TOOLS.length < 15) {
    throw new Error(`Jumlah alatan berdaftar mestilah sekurang-kurangnya 15, tetapi hanya ${ALL_TOOLS.length} ditemui.`);
  }

  const ids = new Set<string>();
  for (const tool of ALL_TOOLS) {
    // 1. Semak keunikan ID
    if (ids.has(tool.id)) {
      throw new Error(`ID alatan berulang dikesan: ${tool.id}`);
    }
    ids.add(tool.id);

    // 2. Semak dwibahasa nama & deskripsi
    if (!tool.name.ms || !tool.name.en) {
      throw new Error(`Nama dwibahasa tidak lengkap untuk alatan: ${tool.id}`);
    }
    if (!tool.description.ms || !tool.description.en) {
      throw new Error(`Deskripsi dwibahasa tidak lengkap untuk alatan: ${tool.id}`);
    }

    // 3. Semak kewujudan komponen React
    if (typeof tool.component !== 'function') {
      throw new Error(`Komponen tidak sah untuk alatan: ${tool.id}`);
    }
  }

  return true;
}
