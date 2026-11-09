/**
 * @file src/services/cryptoUtils.ts
 * @description Utiliti Kriptografi Piawaian Web Crypto API (PBKDF2 + AES-GCM 256-bit).
 * Digunakan untuk menyulitkan (encrypt) dan menyahsulit (decrypt) nota rahsia serta fail peti kebal.
 */

// Menjana Kunci Kripto daripada Kata Laluan / PIN menggunakan PBKDF2
async function deriveKey(password: string, salt: Uint8Array): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    enc.encode(password),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: salt as BufferSource,
      iterations: 100000,
      hash: 'SHA-256',
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

// Menukar ArrayBuffer ke rentetan Base64
function bufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

// Menukar rentetan Base64 ke Uint8Array
function base64ToBuffer(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Menyulitkan teks biasa dengan kata laluan/PIN
 */
export async function encryptData(
  plainText: string,
  pin: string
): Promise<{ ciphertext: string; iv: string; salt: string }> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(pin, salt);

  const enc = new TextEncoder();
  const encodedData = enc.encode(plainText);

  const encryptedBuffer = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    encodedData
  );

  return {
    ciphertext: bufferToBase64(encryptedBuffer),
    iv: bufferToBase64(iv.buffer),
    salt: bufferToBase64(salt.buffer),
  };
}

/**
 * Menyahsulitkan data tersulit kembali kepada teks biasa
 */
export async function decryptData(
  ciphertext: string,
  ivBase64: string,
  saltBase64: string,
  pin: string
): Promise<string> {
  const salt = base64ToBuffer(saltBase64);
  const iv = base64ToBuffer(ivBase64);
  const key = await deriveKey(pin, salt);
  const encryptedBytes = base64ToBuffer(ciphertext);

  const decryptedBuffer = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: iv as BufferSource },
    key,
    encryptedBytes as BufferSource
  );

  const dec = new TextDecoder();
  return dec.decode(decryptedBuffer);
}
