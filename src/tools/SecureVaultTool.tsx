/**
 * @file src/tools/SecureVaultTool.tsx
 * @description Alatan Peti Kebal Rahsia (Secure Encrypted Vault).
 * 
 * FUNGSI UTAMA:
 * 1. Penyulitan gred tentera AES-GCM 256-bit dijana daripada PIN/Kata Laluan menggunakan PBKDF2.
 * 2. Menyimpan nota sulit, kata laluan, dan fail foto secara selamat di dalam storan IndexedDB luar talian.
 * 3. Kunci automatik (Auto-lock) serta-merta apabila pengguna meninggalkan alatan.
 * 4. Tiada data dihantar ke mana-mana pelayan luar — 100% peribadi pada perkakasan telefon pengguna.
 */

import React, { useState, useEffect } from 'react';
import { ArrowLeft, Lock, Unlock, Plus, Trash2, KeyRound, FileText, Image as ImageIcon, Eye, EyeOff, ShieldCheck } from 'lucide-react';
import { encryptData, decryptData } from '../services/cryptoUtils';
import { saveVaultItem, getVaultItems, deleteVaultItem } from '../services/storage';
import { Language, VaultItem } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const SecureVaultTool: React.FC<Props> = ({ lang, onBack }) => {
  const [pin, setPin] = useState('');
  const [isUnlocked, setIsUnlocked] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [items, setItems] = useState<VaultItem[]>([]);
  const [showAddModal, setShowAddModal] = useState(false);

  // Borang item baharu
  const [newTitle, setNewTitle] = useState('');
  const [newContent, setNewContent] = useState('');
  const [newType, setNewType] = useState<'note' | 'password' | 'file'>('note');

  // Paparan item didekripsi
  const [decryptedContents, setDecryptedContents] = useState<Record<string, string>>({});
  const [loadingItems, setLoadingItems] = useState(false);

  // Buka Peti Kebal dengan PIN
  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pin || pin.length < 4) {
      setErrorMsg(lang === 'ms' ? 'PIN mestilah sekurang-kurangnya 4 aksara.' : 'PIN must be at least 4 characters.');
      return;
    }

    setErrorMsg(null);
    setLoadingItems(true);
    try {
      const stored = await getVaultItems();
      setItems(stored as VaultItem[]);
      setIsUnlocked(true);
    } catch (err) {
      console.error('Ralat storan:', err);
      setErrorMsg(lang === 'ms' ? 'Gagal mengakses storan selamat.' : 'Failed to access secure storage.');
    } finally {
      setLoadingItems(false);
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

  // Nyahsulitkan (Decrypt) Item untuk Dipaparkan
  const toggleViewItem = async (item: VaultItem) => {
    if (decryptedContents[item.id]) {
      // Sembunyikan jika sudah dibuka
      const next = { ...decryptedContents };
      delete next[item.id];
      setDecryptedContents(next);
      return;
    }

    try {
      const plain = await decryptData(item.ciphertext, item.iv, item.salt, pin);
      setDecryptedContents((prev) => ({ ...prev, [item.id]: plain }));
    } catch {
      alert(lang === 'ms' ? 'PIN tidak sepadan untuk menyahsulitkan fail ini.' : 'Invalid PIN for this item.');
    }
  };

  // Padam Item daripada Peti Kebal
  const handleDeleteItem = async (id: string) => {
    if (confirm(lang === 'ms' ? 'Adakah anda pasti mahu memadamkan item ini?' : 'Delete this item permanently?')) {
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
              {lang === 'ms' ? 'Simpan nota & fail tersulit (AES-256)' : 'Encrypted notes & secret files'}
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
        {/* ===================== SKRIN KUNCI / LOGIN ===================== */}
        {!isUnlocked ? (
          <div className="flex flex-col items-center justify-center py-12 gap-5 text-center">
            <div className="w-20 h-20 rounded-3xl bg-blue-600/20 text-blue-400 flex items-center justify-center border border-blue-500/30 shadow-2xl">
              <Lock className="w-10 h-10" />
            </div>

            <div>
              <h3 className="text-xl font-bold text-white">
                {lang === 'ms' ? 'Masukkan PIN Peti Kebal' : 'Enter Vault PIN'}
              </h3>
              <p className="text-xs text-slate-400 max-w-xs mt-1">
                {lang === 'ms'
                  ? 'Data disulitkan secara tempatan dengan AES-GCM 256-bit menggunakan PIN ini.'
                  : 'Data is encrypted locally with AES-GCM 256 using this PIN.'}
              </p>
            </div>

            <form onSubmit={handleUnlock} className="w-full max-w-xs flex flex-col gap-3">
              <input
                type="password"
                inputMode="numeric"
                maxLength={12}
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                placeholder={lang === 'ms' ? 'PIN 4-8 digit...' : '4-8 digit PIN...'}
                className="w-full p-3.5 text-center rounded-xl bg-slate-900 border border-slate-700 text-white text-lg font-mono tracking-widest focus:outline-none focus:border-blue-500"
                autoFocus
              />

              {errorMsg && (
                <div className="p-2.5 rounded-xl bg-red-950/60 border border-red-700/50 text-red-200 text-xs">
                  {errorMsg}
                </div>
              )}

              <button
                type="submit"
                className="w-full py-3.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-sm shadow-lg shadow-blue-900/40 active:scale-[0.98] transition flex items-center justify-center gap-2"
              >
                <Unlock className="w-4 h-4" />
                {lang === 'ms' ? 'Buka Peti Kebal' : 'Unlock Vault'}
              </button>
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

        {/* Modal Tambah Item Baharu */}
        {showAddModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
            <div className="w-full max-w-sm rounded-2xl bg-[#121e36] border border-slate-700 p-5 shadow-2xl flex flex-col gap-4">
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
                  className="flex-1 py-2.5 rounded-xl bg-slate-800 text-slate-300 text-xs font-semibold"
                >
                  {lang === 'ms' ? 'Batal' : 'Cancel'}
                </button>
                <button
                  type="button"
                  onClick={handleSaveItem}
                  className="flex-1 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold"
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
