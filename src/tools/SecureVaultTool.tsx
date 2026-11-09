/**
 * @file src/tools/SecureVaultTool.tsx
 * @description Alatan Peti Kebal Rahsia (Secure Encrypted Vault).
 * 
 * FUNGSI UTAMA:
 * 1. Penyulitan perkakasan AES-GCM 256-bit menggunakan PIN (PBKDF2).
 * 2. Token Pengesah Kunci (Auth Verifier) — menghalang akses jika PIN tidak sepadan.
 * 3. Amaran dwi-PIN semasa pendaftaran kali pertama (mencegah salah taip).
 * 4. Fungsi 'Set Semula / Self-Destruct' — memadam bersih semua data jika terlupa PIN.
 * 5. Kunci automatik serta-merta apabila keluar daripada alatan.
 */

import React, { useState } from 'react';
import { 
  ArrowLeft, Lock, Unlock, Plus, Trash2, KeyRound, 
  FileText, Eye, EyeOff, Key, AlertTriangle, AlertOctagon 
} from 'lucide-react';
import { encryptData, decryptData } from '../services/cryptoUtils';
import { saveVaultItem, getVaultItems, deleteVaultItem, getStoredSetting, saveStoredSetting } from '../services/storage';
import { Language, VaultItem } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

interface VaultAuthVerifier {
  ciphertext: string;
  iv: string;
  salt: string;
}

const VERIFIER_KEY = 'omnikit_vault_auth_token';
const AUTH_SECRET_TEXT = 'VAULT_ACCESS_GRANTED_SECURE_256';

export const SecureVaultTool: React.FC<Props> = ({ lang, onBack }) => {
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [isUnlocked, setIsUnlocked] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [items, setItems] = useState<VaultItem[]>([]);
  const [showAddModal, setShowAddModal] = useState(false);
  const [showResetModal, setShowResetModal] = useState(false);

  // Semak sama ada pengguna sudah pernah cipta PIN sebelum ini
  const [hasMasterPin, setHasMasterPin] = useState<boolean>(() => {
    return !!getStoredSetting<VaultAuthVerifier | null>(VERIFIER_KEY, null);
  });

  // Borang item baharu
  const [newTitle, setNewTitle] = useState('');
  const [newContent, setNewContent] = useState('');
  const [newType, setNewType] = useState<'note' | 'password' | 'file'>('note');

  // Paparan item didekripsi
  const [decryptedContents, setDecryptedContents] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  // 1. Cipta PIN Master Kali Pertama (Wajib 2 Kali)
  const handleCreateMasterPin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pin.length < 4) {
      setErrorMsg(lang === 'ms' ? 'PIN mestilah sekurang-kurangnya 4 digit.' : 'PIN must be at least 4 digits.');
      return;
    }
    if (pin !== confirmPin) {
      setErrorMsg(lang === 'ms' ? 'Pengesahan PIN tidak sepadan! Sila taip semula kedua-dua kotak.' : 'PIN confirmation does not match! Please retype.');
      return;
    }

    setLoading(true);
    setErrorMsg(null);
    try {
      // Sulitkan token rahsia menggunakan PIN baharu
      const verifier = await encryptData(AUTH_SECRET_TEXT, pin);
      saveStoredSetting(VERIFIER_KEY, verifier);
      setHasMasterPin(true);
      setIsUnlocked(true);
      setConfirmPin('');
    } catch (err) {
      console.error(err);
      setErrorMsg(lang === 'ms' ? 'Gagal menetapkan PIN keselamatan.' : 'Failed to setup security PIN.');
    } finally {
      setLoading(false);
    }
  };

  // 2. Buka Peti Kebal dengan Pengesahan Kunci
  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pin || pin.length < 4) {
      setErrorMsg(lang === 'ms' ? 'Sila masukkan PIN yang sah.' : 'Please enter a valid PIN.');
      return;
    }

    setErrorMsg(null);
    setLoading(true);

    try {
      const verifier = getStoredSetting<VaultAuthVerifier | null>(VERIFIER_KEY, null);

      if (verifier) {
        try {
          const decryptedToken = await decryptData(verifier.ciphertext, verifier.iv, verifier.salt, pin);
          if (decryptedToken !== AUTH_SECRET_TEXT) {
            throw new Error('PIN Salah');
          }
        } catch {
          setErrorMsg(lang === 'ms' ? 'PIN salah! Akses ditolak.' : 'Incorrect PIN! Access denied.');
          setLoading(false);
          return;
        }
      } else {
        // Kes migrasi dari versi lama
        const stored = await getVaultItems();
        if ((stored as VaultItem[]).length > 0) {
          const sample = (stored as VaultItem[])[0];
          try {
            await decryptData(sample.ciphertext, sample.iv, sample.salt, pin);
            const newVerifier = await encryptData(AUTH_SECRET_TEXT, pin);
            saveStoredSetting(VERIFIER_KEY, newVerifier);
            setHasMasterPin(true);
          } catch {
            setErrorMsg(lang === 'ms' ? 'PIN salah untuk fail sedia ada.' : 'Incorrect PIN for existing files.');
            setLoading(false);
            return;
          }
        } else {
          setHasMasterPin(false);
          setLoading(false);
          return;
        }
      }

      const stored = await getVaultItems();
      setItems(stored as VaultItem[]);
      setIsUnlocked(true);
    } catch (err) {
      console.error('Ralat storan:', err);
      setErrorMsg(lang === 'ms' ? 'Ralat mengakses storan.' : 'Failed to access storage.');
    } finally {
      setLoading(false);
    }
  };

  // 3. Fungsi Padam Bersih Semua Fail & Set Semula Peti Kebal (Self-Destruct Wipe)
  const handleWipeAndResetVault = async () => {
    setLoading(true);
    try {
      // Padam semua fail tersimpan daripada IndexedDB
      const stored = await getVaultItems();
      for (const item of (stored as VaultItem[])) {
        await deleteVaultItem(item.id);
      }

      // Padam token keselamatan PIN
      saveStoredSetting(VERIFIER_KEY, null);

      // Kemas kini state kembali ke mod persediaan awal
      setItems([]);
      setDecryptedContents({});
      setPin('');
      setConfirmPin('');
      setHasMasterPin(false);
      setIsUnlocked(false);
      setShowResetModal(false);
      setErrorMsg(null);
    } catch (err) {
      console.error('Ralat semasa membersihkan peti kebal:', err);
      setErrorMsg(lang === 'ms' ? 'Gagal membersihkan data.' : 'Failed to wipe data.');
    } finally {
      setLoading(false);
    }
  };

  // Simpan Item Baharu yang Tersulit
  const handleSaveItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim() || !newContent.trim()) return;

    try {
      const { ciphertext, iv, salt } = await encryptData(newContent, pin);
      const newItem: VaultItem = {
        id: Date.now().toString(),
        title: newTitle.trim(),
        type: newType,
        ciphertext,
        iv,
        salt,
        createdAt: Date.now(),
      };

      await saveVaultItem(newItem);
      setItems((prev) => [newItem, ...prev]);
      setShowAddModal(false);
      setNewTitle('');
      setNewContent('');
    } catch (err) {
      console.error('Ralat enkripsi:', err);
      setErrorMsg(lang === 'ms' ? 'Gagal menyulitkan item.' : 'Failed to encrypt item.');
    }
  };

  // Nyahsulitkan Item untuk Dipaparkan
  const toggleViewItem = async (item: VaultItem) => {
    if (decryptedContents[item.id]) {
      const next = { ...decryptedContents };
      delete next[item.id];
      setDecryptedContents(next);
      return;
    }

    try {
      const plain = await decryptData(item.ciphertext, item.iv, item.salt, pin);
      setDecryptedContents((prev) => ({ ...prev, [item.id]: plain }));
    } catch {
      alert(lang === 'ms' ? 'Gagal menyahsulitkan fail ini.' : 'Failed to decrypt this item.');
    }
  };

  // Padam Item daripada Peti Kebal
  const handleDeleteItem = async (id: string) => {
    if (confirm(lang === 'ms' ? 'Padam item ini secara kekal?' : 'Delete this item permanently?')) {
      await deleteVaultItem(id);
      setItems((prev) => prev.filter((i) => i.id !== id));
      const next = { ...decryptedContents };
      delete next[id];
      setDecryptedContents(next);
    }
  };

  // Kunci Semula Peti Kebal
  const handleLock = () => {
    setIsUnlocked(false);
    setPin('');
    setConfirmPin('');
    setErrorMsg(null);
    setDecryptedContents({});
    setItems([]);
  };

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              handleLock();
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Peti Kebal Rahsia (Vault)' : 'Secure Vault'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Penyulitan Perkakasan AES-256' : 'Hardware AES-256 Encryption'}
            </p>
          </div>
        </div>

        {isUnlocked && (
          <button
            onClick={handleLock}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-red-600 hover:bg-red-500 text-white text-xs font-semibold shadow transition"
          >
            <Lock className="w-3.5 h-3.5" />
            <span>{lang === 'ms' ? 'Kunci' : 'Lock'}</span>
          </button>
        )}
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col gap-5">
        {/* ===================== SKRIN KUNCI / DAFTAR PIN ===================== */}
        {!isUnlocked ? (
          <div className="flex flex-col items-center justify-center py-6 gap-4 text-center">
            <div className="w-16 h-16 rounded-3xl bg-blue-600/20 text-blue-400 flex items-center justify-center border border-blue-500/30 shadow-2xl">
              {hasMasterPin ? <Lock className="w-8 h-8" /> : <Key className="w-8 h-8 text-amber-400" />}
            </div>

            <div>
              <h3 className="text-xl font-bold text-white">
                {!hasMasterPin
                  ? lang === 'ms' ? 'Tetapkan PIN Peti Kebal' : 'Setup Vault PIN'
                  : lang === 'ms' ? 'Masukkan PIN Peti Kebal' : 'Enter Vault PIN'}
              </h3>
              <p className="text-xs text-slate-400 max-w-xs mt-1">
                {!hasMasterPin
                  ? lang === 'ms'
                    ? 'Cipta PIN peribadi untuk menyulitkan data secara luar talian pada peranti ini.'
                    : 'Create a master PIN to encrypt data locally on this device.'
                  : lang === 'ms'
                  ? 'Masukkan PIN yang tepat untuk menyahsulitkan peti kebal peribadi anda.'
                  : 'Enter your PIN to decrypt the private vault.'}
              </p>
            </div>

            {/* Kotak Amaran Kritikal Semasa Tetapan Kali Pertama */}
            {!hasMasterPin && (
              <div className="w-full p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-left flex gap-3">
                <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
                <div className="text-xs text-amber-200/90 leading-relaxed">
                  <span className="font-bold text-amber-400 block mb-0.5">
                    {lang === 'ms' ? 'PERINGATAN PENTING:' : 'CRITICAL WARNING:'}
                  </span>
                  {lang === 'ms'
                    ? 'Masukkan PIN anda sebanyak 2 KALI dengan teliti. Tiada pangkalan data awan untuk memulihkan kata laluan. Jika terlupa PIN atau menekan butang set semula nanti, SEMUA fail tersimpan akan DIPADAM KEKAL!'
                    : 'Enter your PIN TWICE carefully. There is no cloud recovery. If you forget your PIN or reset the vault, ALL stored files will be PERMANENTLY ERASED!'}
                </div>
              </div>
            )}

            <form
              onSubmit={!hasMasterPin ? handleCreateMasterPin : handleUnlock}
              className="w-full max-w-xs flex flex-col gap-3"
            >
              <input
                type="password"
                inputMode="numeric"
                maxLength={8}
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                placeholder={!hasMasterPin ? (lang === 'ms' ? '1. Masukkan PIN (4-8 digit)' : '1. Enter PIN (4-8 digits)') : (lang === 'ms' ? 'PIN 4-8 digit...' : 'Enter PIN...')}
                className="w-full p-3.5 text-center rounded-xl bg-slate-900 border border-slate-700 text-white text-lg font-mono tracking-widest focus:outline-none focus:border-blue-500"
                autoFocus
              />

              {!hasMasterPin && (
                <input
                  type="password"
                  inputMode="numeric"
                  maxLength={8}
                  value={confirmPin}
                  onChange={(e) => setConfirmPin(e.target.value)}
                  placeholder={lang === 'ms' ? '2. Sahkan PIN sekali lagi' : '2. Confirm PIN again'}
                  className="w-full p-3.5 text-center rounded-xl bg-slate-900 border border-slate-700 text-white text-lg font-mono tracking-widest focus:outline-none focus:border-blue-500"
                />
              )}

              {errorMsg && (
                <div className="p-2.5 rounded-xl bg-red-950/60 border border-red-700/50 text-red-200 text-xs animate-in fade-in">
                  {errorMsg}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-sm shadow-lg shadow-blue-900/40 active:scale-[0.98] transition flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {!hasMasterPin ? (
                  <>
                    <Key className="w-4 h-4" />
                    <span>{lang === 'ms' ? 'Sahkan PIN & Buka Peti' : 'Confirm PIN & Unlock'}</span>
                  </>
                ) : (
                  <>
                    <Unlock className="w-4 h-4" />
                    <span>{lang === 'ms' ? 'Buka Peti Kebal' : 'Unlock Vault'}</span>
                  </>
                )}
              </button>

              {/* Butang Lupa PIN / Reset Semula (Cuma keluar jika sudah ada master PIN) */}
              {hasMasterPin && (
                <button
                  type="button"
                  onClick={() => setShowResetModal(true)}
                  className="mt-2 text-xs text-rose-400 hover:text-rose-300 underline font-medium transition"
                >
                  {lang === 'ms' ? 'Terlupa PIN? Set Semula Peti Kebal' : 'Forgot PIN? Reset Vault'}
                </button>
              )}
            </form>
          </div>
        ) : (
          /* ===================== KANDUNGAN PETI KEBAL DIBUKA ===================== */
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                {lang === 'ms' ? `Kandungan Tersimpan (${items.length})` : `Stored Items (${items.length})`}
              </span>
              <button
                onClick={() => setShowAddModal(true)}
                className="flex items-center gap-1 py-1.5 px-3 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold transition"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>{lang === 'ms' ? 'Tambah Item' : 'Add Item'}</span>
              </button>
            </div>

            {items.length === 0 ? (
              <div className="p-8 text-center text-slate-500 text-xs bg-slate-900/40 rounded-2xl border border-slate-800">
                {lang === 'ms' ? 'Peti kebal anda kosong. Tekan "+ Tambah Item" untuk mula menyimpan rahsia.' : 'Your vault is empty. Tap "+ Add Item" to save secrets.'}
              </div>
            ) : (
              items.map((item) => (
                <div
                  key={item.id}
                  className="p-4 bg-[#121e36] rounded-2xl border border-slate-700/60 flex flex-col gap-2.5 shadow-sm"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      {item.type === 'password' ? (
                        <KeyRound className="w-4 h-4 text-amber-400" />
                      ) : (
                        <FileText className="w-4 h-4 text-cyan-400" />
                      )}
                      <span className="text-sm font-bold text-white">{item.title}</span>
                    </div>

                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => toggleViewItem(item)}
                        className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition"
                        title={decryptedContents[item.id] ? 'Sembunyi' : 'Lihat'}
                      >
                        {decryptedContents[item.id] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                      <button
                        onClick={() => handleDeleteItem(item.id)}
                        className="p-1.5 rounded-lg bg-slate-800 hover:bg-red-900/60 text-slate-400 hover:text-red-300 transition"
                        title="Padam"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  {decryptedContents[item.id] && (
                    <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-800 text-slate-200 text-xs font-mono break-all whitespace-pre-wrap animate-in fade-in">
                      {decryptedContents[item.id]}
                    </div>
                  )}

                  <div className="text-[10px] text-slate-500 font-mono">
                    {new Date(item.createdAt).toLocaleString(lang === 'ms' ? 'ms-MY' : 'en-US')}
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {/* Modal Amaran Set Semula / Padam Bersih (Wipe & Reset Modal) */}
        {showResetModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
            <div className="w-full max-w-sm rounded-2xl bg-[#121e36] border border-red-500/40 p-5 shadow-2xl flex flex-col gap-4 animate-in fade-in zoom-in-95">
              <div className="flex items-center gap-3 text-rose-400">
                <AlertOctagon className="w-7 h-7 shrink-0 text-red-500" />
                <h3 className="text-base font-bold text-white">
                  {lang === 'ms' ? 'Padam & Set Semula?' : 'Wipe & Reset Vault?'}
                </h3>
              </div>

              <p className="text-xs text-slate-300 leading-relaxed">
                {lang === 'ms'
                  ? 'Demi keselamatan data peribadi anda, menetapkan semula PIN akan MEMADAM SEMUA FAIL, NOTA, DAN KATA LALUAN di dalam peti kebal ini secara kekal. Tindakan ini tidak boleh diundur!'
                  : 'For your security, resetting the PIN will PERMANENTLY ERASE ALL STORED DATA in this vault. This action cannot be undone!'}
              </p>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowResetModal(false)}
                  className="flex-1 py-2.5 rounded-xl bg-slate-800 text-slate-300 text-xs font-semibold hover:bg-slate-700 transition"
                >
                  {lang === 'ms' ? 'Batal' : 'Cancel'}
                </button>
                <button
                  type="button"
                  disabled={loading}
                  onClick={handleWipeAndResetVault}
                  className="flex-1 py-2.5 rounded-xl bg-red-600 hover:bg-red-500 text-white text-xs font-semibold shadow-lg shadow-red-900/50 transition"
                >
                  {lang === 'ms' ? 'Ya, Padam Semua' : 'Yes, Wipe All'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Modal Tambah Item Baharu */}
        {showAddModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
            <div className="w-full max-w-sm rounded-2xl bg-[#121e36] border border-slate-700 p-5 shadow-2xl flex flex-col gap-4 animate-in fade-in zoom-in-95">
              <h3 className="text-base font-bold text-white">
                {lang === 'ms' ? 'Simpan Maklumat Rahsia' : 'Save Encrypted Secret'}
              </h3>

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setNewType('note')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-medium border ${
                    newType === 'note' ? 'bg-blue-600 border-blue-500 text-white' : 'bg-slate-900 border-slate-700 text-slate-400'
                  }`}
                >
                  {lang === 'ms' ? 'Nota Rahsia' : 'Secret Note'}
                </button>
                <button
                  type="button"
                  onClick={() => setNewType('password')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-medium border ${
                    newType === 'password' ? 'bg-blue-600 border-blue-500 text-white' : 'bg-slate-900 border-slate-700 text-slate-400'
                  }`}
                >
                  {lang === 'ms' ? 'Kata Laluan' : 'Password'}
                </button>
              </div>

              <input
                type="text"
                placeholder={lang === 'ms' ? 'Tajuk (cth: Akaun Bank / Nota Rahsia)' : 'Title (e.g. Bank Account / Note)'}
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-700 text-white text-xs focus:outline-none focus:border-blue-500"
              />

              <textarea
                placeholder={lang === 'ms' ? 'Kandungan rahsia yang ingin disulitkan...' : 'Secret content to encrypt...'}
                rows={4}
                value={newContent}
                onChange={(e) => setNewContent(e.target.value)}
                className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-700 text-white text-xs focus:outline-none focus:border-blue-500"
              />

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="flex-1 py-2.5 rounded-xl bg-slate-800 text-slate-300 text-xs font-semibold hover:bg-slate-700 transition"
                >
                  {lang === 'ms' ? 'Batal' : 'Cancel'}
                </button>
                <button
                  type="button"
                  onClick={handleSaveItem}
                  className="flex-1 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow transition"
                >
                  {lang === 'ms' ? 'Sulitkan & Simpan' : 'Encrypt & Save'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
