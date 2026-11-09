/**
 * @file tests/runTests.ts
 * @description Pelaksana Suite Ujian Unit OmniKitMY (Automated Unit Test Runner).
 * Menjalankan semua ujian penukaran, integriti alatan, dan penyulitan data.
 */

import { testLengthConversions, testWeightConversions, testTemperatureConversions, testDataConversions } from './converter.test';
import { testToolRegistryIntegrity } from './tools.test';
import { testEncryptionAndDecryption } from './crypto.test';

async function runAllTests() {
  console.log('\n========================================');
  console.log('   OMNIKITMY AUTOMATED UNIT TEST SUITE  ');
  console.log('========================================\n');

  let passed = 0;
  let failed = 0;

  async function execute(name: string, fn: () => Promise<boolean> | boolean) {
    try {
      await fn();
      console.log(` \x1b[32m✔ [LULUS]\x1b[0m ${name}`);
      passed++;
    } catch (err: unknown) {
      console.error(` \x1b[31m✖ [GAGAL]\x1b[0m ${name}: ${(err as Error).message}`);
      failed++;
    }
  }

  // 1. Ujian Penukaran Unit
  await execute('Ujian Penukaran Panjang (Meter, Km, Inci, dsb)', testLengthConversions);
  await execute('Ujian Penukaran Berat (Kg, Gram, Paun/lbs)', testWeightConversions);
  await execute('Ujian Penukaran Suhu (Celsius, Fahrenheit, Kelvin)', testTemperatureConversions);
  await execute('Ujian Penukaran Saiz Data (KB, MB, GB)', testDataConversions);

  // 2. Ujian Integriti Pendaftaran Alatan
  await execute('Ujian Integriti & Dwibahasa Pendaftaran Alatan (20 Alatan)', testToolRegistryIntegrity);

  // 3. Ujian Kriptografi AES-GCM Peti Kebal
  await execute('Ujian Enkripsi & Dekripsi AES-GCM 256-bit Peti Kebal', testEncryptionAndDecryption);

  console.log('\n----------------------------------------');
  console.log(`Jumlah Ujian: ${passed + failed} | Lulus: \x1b[32m${passed}\x1b[0m | Gagal: \x1b[31m${failed}\x1b[0m`);
  console.log('----------------------------------------\n');

  if (failed > 0) {
    process.exit(1);
  }
}

runAllTests();
