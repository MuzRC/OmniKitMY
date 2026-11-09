/**
 * @file src/tools/DeveloperTools.tsx
 * @description Alatan Pembangun & Keselamatan (Developer Tools & Utilities).
 * 
 * FUNGSI UTAMA:
 * 1. Penjana UUID v4 selamat menggunakan kriptografi `crypto.randomUUID()`.
 * 2. Penjana Hash Kriptografi SHA-256 & SHA-512 masa nyata melalui Web Crypto API.
 * 3. Penguji Ungkapan Nalar (Regex Tester) dengan pemadanan teks interaktif.
 * 4. Pemformat & Pengesah JSON (JSON Beautifier & Minifier).
 * 5. Penukar Cap Masa Unix (Unix Timestamp to DateTime Converter).
 */

import React, { useState } from 'react';
import { ArrowLeft, Code, Copy, Check, Hash, RefreshCw, Terminal, CheckCircle2, AlertCircle } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

type Tab = 'uuid' | 'hash' | 'json' | 'regex' | 'timestamp';

export const DeveloperTools: React.FC<Props> = ({ lang, onBack }) => {
  const [activeTab, setActiveTab] = useState<Tab>('uuid');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Keadaan UUID
  const [uuids, setUuids] = useState<string[]>([crypto.randomUUID()]);

  // Keadaan Hashing
  const [hashInput, setHashInput] = useState<string>('OmniKitMY');
  const [sha256Hash, setSha256Hash] = useState<string>('');
  const [sha512Hash, setSha512Hash] = useState<string>('');

  // Keadaan JSON
  const [jsonInput, setJsonInput] = useState<string>('{"app":"OmniKitMY","version":1.0,"features":["offline","security"]}');
  const [jsonError, setJsonError] = useState<string | null>(null);

  // Keadaan Regex
  const [regexPattern, setRegexPattern] = useState<string>('\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b');
  const [regexFlags, setRegexFlags] = useState<string>('g');
  const [regexText, setRegexText] = useState<string>('Hubungi sokongan di info@omnikitmy.app atau admin@google.com');

  // Keadaan Cap Masa
  const [timestampInput, setTimestampInput] = useState<string>(Math.floor(Date.now() / 1000).toString());

  const copyVal = (val: string, key: string) => {
    navigator.clipboard.writeText(val);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 1500);
  };

  // Kira Hash Kripto SHA-256 & SHA-512
  const calculateHashes = async (text: string) => {
    setHashInput(text);
    const enc = new TextEncoder();
    const data = enc.encode(text);

    // SHA-256
    const hash256 = await crypto.subtle.digest('SHA-256', data);
    setSha256Hash(
      Array.from(new Uint8Array(hash256))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('')
    );

    // SHA-512
    const hash512 = await crypto.subtle.digest('SHA-512', data);
    setSha512Hash(
      Array.from(new Uint8Array(hash512))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('')
    );
  };

  // Format JSON
  const formatJson = () => {
    try {
      const parsed = JSON.parse(jsonInput);
      setJsonInput(JSON.stringify(parsed, null, 2));
      setJsonError(null);
    } catch (e: unknown) {
      setJsonError((e as Error).message);
    }
  };

  const minifyJson = () => {
    try {
      const parsed = JSON.parse(jsonInput);
      setJsonInput(JSON.stringify(parsed));
      setJsonError(null);
    } catch (e: unknown) {
      setJsonError((e as Error).message);
    }
  };

  // Jana Senarai UUID
  const generateUuids = (count = 5) => {
    const list = Array.from({ length: count }, () => crypto.randomUUID());
    setUuids(list);
  };

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Alatan Pembangun (DevTools)' : 'Developer Tools'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'UUID, Hash Kripto, Regex, JSON & Cap Masa' : 'UUID, Crypto Hashes, Regex, JSON & Timestamps'}
            </p>
          </div>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col gap-5">
        {/* Tab Pemilih */}
        <div className="grid grid-cols-5 gap-1 p-1 bg-slate-900/90 rounded-2xl border border-slate-700/60 text-center">
          {[
            { id: 'uuid', label: 'UUID' },
            { id: 'hash', label: 'Hash' },
            { id: 'json', label: 'JSON' },
            { id: 'regex', label: 'Regex' },
            { id: 'timestamp', label: 'Masa' },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => {
                setActiveTab(tab.id as Tab);
                if (tab.id === 'hash' && !sha256Hash) {
                  calculateHashes(hashInput);
                }
              }}
              className={`py-2 px-1 rounded-xl text-xs font-semibold transition ${
                activeTab === tab.id
                  ? 'bg-blue-600 text-white shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* ===================== TAB 1: UUID GENERATOR ===================== */}
        {activeTab === 'uuid' && (
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-300">
                {lang === 'ms' ? 'Jana Kunci UUID v4 Asli' : 'Generate Secure UUID v4'}
              </span>
              <button
                onClick={() => generateUuids(5)}
                className="flex items-center gap-1.5 py-1.5 px-3 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow transition"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>{lang === 'ms' ? 'Jana 5 Baharu' : 'Generate 5'}</span>
              </button>
            </div>

            <div className="flex flex-col gap-2">
              {uuids.map((id, index) => (
                <div
                  key={index}
                  className="p-3 bg-[#121e36] rounded-xl border border-slate-700/60 flex items-center justify-between gap-2"
                >
                  <span className="font-mono text-xs text-cyan-300 break-all">{id}</span>
                  <button
                    onClick={() => copyVal(id, `uuid_${index}`)}
                    className="p-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white shrink-0"
                  >
                    {copiedKey === `uuid_${index}` ? (
                      <Check className="w-4 h-4 text-emerald-400" />
                    ) : (
                      <Copy className="w-4 h-4" />
                    )}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ===================== TAB 2: HASH GENERATOR ===================== */}
        {activeTab === 'hash' && (
          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
              <label className="text-xs font-semibold text-slate-300">
                {lang === 'ms' ? 'Teks untuk Dihash:' : 'Text to Hash:'}
              </label>
              <input
                type="text"
                value={hashInput}
                onChange={(e) => calculateHashes(e.target.value)}
                className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-sm focus:outline-none focus:border-blue-500"
              />
            </div>

            <div className="flex flex-col gap-3">
              <div className="p-3 bg-[#121e36] rounded-xl border border-slate-700/60 flex flex-col gap-1.5">
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span className="font-bold text-slate-300">SHA-256</span>
                  <button
                    onClick={() => copyVal(sha256Hash, 'sha256')}
                    className="text-cyan-400 hover:underline flex items-center gap-1"
                  >
                    {copiedKey === 'sha256' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    {lang === 'ms' ? 'Salin' : 'Copy'}
                  </button>
                </div>
                <div className="p-2 bg-slate-900 rounded-lg font-mono text-[11px] text-slate-200 break-all select-all">
                  {sha256Hash || 'Kira hash...'}
                </div>
              </div>

              <div className="p-3 bg-[#121e36] rounded-xl border border-slate-700/60 flex flex-col gap-1.5">
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span className="font-bold text-slate-300">SHA-512</span>
                  <button
                    onClick={() => copyVal(sha512Hash, 'sha512')}
                    className="text-cyan-400 hover:underline flex items-center gap-1"
                  >
                    {copiedKey === 'sha512' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    {lang === 'ms' ? 'Salin' : 'Copy'}
                  </button>
                </div>
                <div className="p-2 bg-slate-900 rounded-lg font-mono text-[10px] text-slate-200 break-all select-all">
                  {sha512Hash || 'Kira hash...'}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ===================== TAB 3: JSON FORMATTER ===================== */}
        {activeTab === 'json' && (
          <div className="flex flex-col gap-3">
            <div className="flex gap-2">
              <button
                onClick={formatJson}
                className="flex-1 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold"
              >
                {lang === 'ms' ? 'Kemaskan (Beautify)' : 'Beautify JSON'}
              </button>
              <button
                onClick={minifyJson}
                className="flex-1 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold"
              >
                {lang === 'ms' ? 'Mampatkan (Minify)' : 'Minify JSON'}
              </button>
              <button
                onClick={() => copyVal(jsonInput, 'json_content')}
                className="p-2 rounded-xl bg-slate-800 text-slate-300 hover:text-white"
                title="Salin JSON"
              >
                {copiedKey === 'json_content' ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>

            <textarea
              value={jsonInput}
              onChange={(e) => {
                setJsonInput(e.target.value);
                setJsonError(null);
              }}
              rows={10}
              className="w-full p-3 rounded-2xl bg-slate-900 border border-slate-700 text-white font-mono text-xs focus:outline-none focus:border-blue-500"
            />

            {jsonError && (
              <div className="p-3 rounded-xl bg-red-950/60 border border-red-700/50 text-red-200 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
                <span>{jsonError}</span>
              </div>
            )}
          </div>
        )}

        {/* ===================== TAB 4: REGEX TESTER ===================== */}
        {activeTab === 'regex' && (
          <div className="flex flex-col gap-4">
            <div className="p-3 bg-[#121e36] rounded-xl border border-slate-700/60 flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <span className="text-slate-400 font-mono text-sm">/</span>
                <input
                  type="text"
                  value={regexPattern}
                  onChange={(e) => setRegexPattern(e.target.value)}
                  placeholder="Regex pattern..."
                  className="flex-1 p-2 rounded-lg bg-slate-900 border border-slate-700 text-cyan-300 font-mono text-xs focus:outline-none"
                />
                <span className="text-slate-400 font-mono text-sm">/</span>
                <input
                  type="text"
                  value={regexFlags}
                  onChange={(e) => setRegexFlags(e.target.value)}
                  className="w-12 p-2 rounded-lg bg-slate-900 border border-slate-700 text-slate-300 font-mono text-xs text-center focus:outline-none"
                />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-slate-400">
                {lang === 'ms' ? 'Teks untuk Diuji:' : 'Test String:'}
              </label>
              <textarea
                value={regexText}
                onChange={(e) => setRegexText(e.target.value)}
                rows={4}
                className="w-full p-3 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-xs focus:outline-none"
              />
            </div>

            <div className="p-3 bg-[#121e36] rounded-xl border border-slate-700/60 flex flex-col gap-1.5">
              <span className="text-xs font-bold text-slate-300">
                {lang === 'ms' ? 'Padanan Ditemui (Matches):' : 'Matches Found:'}
              </span>
              <div className="flex flex-wrap gap-1.5 mt-1">
                {(() => {
                  try {
                    const re = new RegExp(regexPattern, regexFlags);
                    const matches = regexText.match(re);
                    if (!matches || matches.length === 0) {
                      return <span className="text-xs text-slate-500">{lang === 'ms' ? 'Tiada padanan' : 'No matches'}</span>;
                    }
                    return matches.map((m, idx) => (
                      <span key={idx} className="px-2 py-1 rounded bg-blue-600/30 border border-blue-500/50 text-cyan-300 font-mono text-xs">
                        {m}
                      </span>
                    ));
                  } catch (e: unknown) {
                    return <span className="text-xs text-red-400">{(e as Error).message}</span>;
                  }
                })()}
              </div>
            </div>
          </div>
        )}

        {/* ===================== TAB 5: TIMESTAMP ===================== */}
        {activeTab === 'timestamp' && (
          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold text-slate-300">
                  {lang === 'ms' ? 'Cap Masa Unix (Saat):' : 'Unix Timestamp (Seconds):'}
                </label>
                <button
                  onClick={() => setTimestampInput(Math.floor(Date.now() / 1000).toString())}
                  className="text-xs text-cyan-400 hover:underline"
                >
                  {lang === 'ms' ? 'Guna Waktu Sekarang' : 'Use Current Time'}
                </button>
              </div>
              <input
                type="number"
                value={timestampInput}
                onChange={(e) => setTimestampInput(e.target.value)}
                className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-sm focus:outline-none"
              />
            </div>

            <div className="p-4 bg-[#121e36] rounded-2xl border border-slate-700/60 flex flex-col gap-3">
              <div>
                <span className="text-[11px] text-slate-400 block">{lang === 'ms' ? 'Waktu Tempatan Malaysia (MYT)' : 'Local Time'}</span>
                <span className="text-base font-bold font-mono text-white">
                  {new Date(parseInt(timestampInput || '0') * 1000).toLocaleString(lang === 'ms' ? 'ms-MY' : 'en-US')}
                </span>
              </div>
              <div>
                <span className="text-[11px] text-slate-400 block">ISO 8601 UTC</span>
                <span className="text-sm font-mono text-cyan-400">
                  {new Date(parseInt(timestampInput || '0') * 1000).toISOString()}
                </span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
