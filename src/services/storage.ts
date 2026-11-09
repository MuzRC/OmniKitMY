/**
 * @file src/services/storage.ts
 * @description Pengurus storan luar talian (Offline Storage Manager) untuk OmniKitMY.
 * Menggunakan IndexedDB untuk simpanan data bersaiz besar/fail dan LocalStorage untuk tetapan pantas.
 */

const DB_NAME = 'OmniKitMY_DB';
const DB_VERSION = 1;
const STORE_VAULT = 'vault_items';
const STORE_HISTORY = 'tool_history';

// Inisialisasi IndexedDB
function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (!window.indexedDB) {
      reject(new Error('IndexedDB tidak disokong pada pelayar ini.'));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event: IDBVersionChangeEvent) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(STORE_VAULT)) {
        db.createObjectStore(STORE_VAULT, { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains(STORE_HISTORY)) {
        db.createObjectStore(STORE_HISTORY, { keyPath: 'id', autoIncrement: true });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

// Pengurusan Kegemaran (Favorites) menggunakan LocalStorage
const FAVORITES_KEY = 'omnikitmy_favorites';

export function getFavorites(): string[] {
  try {
    const raw = localStorage.getItem(FAVORITES_KEY);
    return raw ? JSON.parse(raw) : ['qr-scanner', 'speaker-cleaner', 'leveler', 'sound-meter'];
  } catch {
    return ['qr-scanner', 'speaker-cleaner', 'leveler', 'sound-meter'];
  }
}

export function toggleFavorite(toolId: string): string[] {
  const current = getFavorites();
  const exists = current.includes(toolId);
  const updated = exists ? current.filter((id) => id !== toolId) : [...current, toolId];
  try {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify(updated));
  } catch (e) {
    console.error('Gagal menyimpan kegemaran:', e);
  }
  return updated;
}

// Pengurusan Tetapan Aplikasi (Bahasa & Tema)
export function getStoredSetting<T>(key: string, defaultValue: T): T {
  try {
    const raw = localStorage.getItem(`omnikitmy_${key}`);
    return raw !== null ? (JSON.parse(raw) as T) : defaultValue;
  } catch {
    return defaultValue;
  }
}

export function saveStoredSetting<T>(key: string, value: T): void {
  try {
    localStorage.setItem(`omnikitmy_${key}`, JSON.stringify(value));
  } catch (e) {
    console.error('Gagal menyimpan tetapan:', e);
  }
}

// Operasi Peti Kebal (Vault) di IndexedDB
export async function saveVaultItem(item: { id: string; title: string; ciphertext: string; iv: string; salt: string; createdAt: number; type: string }): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_VAULT, 'readwrite');
    const store = tx.objectStore(STORE_VAULT);
    const req = store.put(item);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

export async function getVaultItems(): Promise<Array<{ id: string; title: string; ciphertext: string; iv: string; salt: string; createdAt: number; type: string }>> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_VAULT, 'readonly');
    const store = tx.objectStore(STORE_VAULT);
    const req = store.getAll();
    req.onsuccess = () => resolve(req.result || []);
    req.onerror = () => reject(req.error);
  });
}

export async function deleteVaultItem(id: string): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_VAULT, 'readwrite');
    const store = tx.objectStore(STORE_VAULT);
    const req = store.delete(id);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}
