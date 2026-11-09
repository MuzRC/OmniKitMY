/**
 * @file tests/crypto.test.ts
 * @description Ujian Unit bagi Enjin Kriptografi Peti Kebal (Vault AES-GCM Encryption Tests).
 */

import { encryptData, decryptData } from '../src/services/cryptoUtils';

export async function testEncryptionAndDecryption() {
  const secretMessage = 'NotaRahsia123#OmniKitMY';
  const pin = '8822';

  // 1. Enkripsi
  const encrypted = await encryptData(secretMessage, pin);
  if (!encrypted.ciphertext || !encrypted.iv || !encrypted.salt) {
    throw new Error('Hasil enkripsi tidak lengkap');
  }

  // 2. Dekripsi dengan PIN betul
  const decrypted = await decryptData(encrypted.ciphertext, encrypted.iv, encrypted.salt, pin);
  if (decrypted !== secretMessage) {
    throw new Error(`Mesej didekripsi '${decrypted}' tidak sepadan dengan '${secretMessage}'`);
  }

  // 3. Dekripsi dengan PIN salah sepatutnya gagal
  let failedAsExpected = false;
  try {
    await decryptData(encrypted.ciphertext, encrypted.iv, encrypted.salt, '9999');
  } catch {
    failedAsExpected = true;
  }

  if (!failedAsExpected) {
    throw new Error('Dekripsi dengan PIN salah sepatutnya gagal tetapi berjaya');
  }

  return true;
}
