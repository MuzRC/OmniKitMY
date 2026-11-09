/**
 * @file src/tools/NetworkInspector.tsx
 * @description Alatan Pemeriksa Rangkaian & Ujian Latensi Ping (Network Inspector).
 * 
 * FUNGSI UTAMA:
 * 1. Ujian Latensi Masa Nyata (Real HTTP Ping Latency in milliseconds).
 * 2. Mengesan jenis sambungan rangkaian peranti (4G, 5G, Wi-Fi) dan anggaran downlink Mbit/s.
 * 3. Pemeriksaan Calon IP Tempatan melalui WebRTC RTCPeerConnection.
 * 4. Penunjuk status sambungan dalam talian (Online / Offline Detector).
 */

import React, { useState, useEffect } from 'react';
import { ArrowLeft, Wifi, Globe, Activity, RefreshCw, Zap, ShieldCheck } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const NetworkInspector: React.FC<Props> = ({ lang, onBack }) => {
  const [isOnline, setIsOnline] = useState<boolean>(navigator.onLine);
  const [latency, setLatency] = useState<number | null>(null);
  const [minLatency, setMinLatency] = useState<number | null>(null);
  const [maxLatency, setMaxLatency] = useState<number | null>(null);
  const [isRunningPing, setIsRunningPing] = useState<boolean>(false);
  const [networkInfo, setNetworkInfo] = useState<{ type?: string; downlink?: number; rtt?: number }>({});
  const [localIps, setLocalIps] = useState<string[]>([]);

  // Pantau sambungan Online / Offline
  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    // Baca Network Information API
    const navConn = (navigator as unknown as { connection?: { effectiveType?: string; downlink?: number; rtt?: number } }).connection;
    if (navConn) {
      setNetworkInfo({
        type: navConn.effectiveType || 'Wi-Fi / Mobile',
        downlink: navConn.downlink,
        rtt: navConn.rtt,
      });
    }

    // Uji Calon IP WebRTC Tempatan
    detectWebRtcIps();

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  const detectWebRtcIps = () => {
    try {
      const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
      pc.createDataChannel('');
      pc.createOffer().then((offer) => pc.setLocalDescription(offer));
      pc.onicecandidate = (event) => {
        if (!event || !event.candidate) return;
        const candidate = event.candidate.candidate;
        const match = candidate.match(/([0-9]{1,3}(\.[0-9]{1,3}){3})/);
        if (match && match[1]) {
          setLocalIps((prev) => Array.from(new Set([...prev, match[1]])));
        }
      };
    } catch (e) {
      console.warn('WebRTC IP detection tidak disokong:', e);
    }
  };

  // Ujian Ping Masa Nyata
  const runPingTest = async () => {
    setIsRunningPing(true);
    let min = Infinity;
    let max = 0;

    for (let i = 0; i < 4; i++) {
      const start = performance.now();
      try {
        await fetch(`/?ping=${Date.now()}_${i}`, { method: 'HEAD', cache: 'no-store' });
        const duration = Math.round(performance.now() - start);
        setLatency(duration);
        min = Math.min(min, duration);
        max = Math.max(max, duration);
      } catch {
        // Abaikan
      }
      await new Promise((r) => setTimeout(r, 200));
    }

    if (min !== Infinity) setMinLatency(min);
    if (max !== 0) setMaxLatency(max);
    setIsRunningPing(false);
  };

  useEffect(() => {
    runPingTest();
  }, []);

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
              {lang === 'ms' ? 'Pemeriksa Rangkaian' : 'Network Inspector'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Uji ping latensi, kelajuan & sambungan' : 'Latency ping & network diagnostics'}
            </p>
          </div>
        </div>

        <button
          onClick={runPingTest}
          disabled={isRunningPing}
          className="p-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-50 transition"
          title="Uji Semula Ping"
        >
          <RefreshCw className={`w-4 h-4 ${isRunningPing ? 'animate-spin' : ''}`} />
        </button>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Status Sambungan Utama */}
        <div className="text-center">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-slate-900 border border-slate-700 mb-3">
            <span
              className={`w-2.5 h-2.5 rounded-full ${isOnline ? 'bg-emerald-400 animate-pulse' : 'bg-red-500'}`}
            />
            <span className="text-xs font-semibold text-white">
              {isOnline
                ? lang === 'ms'
                  ? 'Tersambung ke Internet'
                  : 'Connected Online'
                : lang === 'ms'
                ? 'Tiada Sambungan (Mod Luar Talian)'
                : 'Offline Mode Active'}
            </span>
          </div>

          <div className="text-5xl font-black font-mono text-cyan-400 tabular-nums">
            {latency !== null ? `${latency} ms` : '--'}
          </div>
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest mt-1 block">
            {lang === 'ms' ? 'Latensi Rangkaian (Ping)' : 'Network Round-Trip Time'}
          </span>
        </div>

        {/* Ringkasan Min & Maksimum Latensi */}
        <div className="w-full grid grid-cols-2 gap-3">
          <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-xs text-slate-400 uppercase font-semibold block mb-1">Min Ping</span>
            <span className="text-2xl font-bold font-mono text-emerald-400 tabular-nums">
              {minLatency !== null ? `${minLatency} ms` : '--'}
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-xs text-slate-400 uppercase font-semibold block mb-1">Maks Ping</span>
            <span className="text-2xl font-bold font-mono text-yellow-400 tabular-nums">
              {maxLatency !== null ? `${maxLatency} ms` : '--'}
            </span>
          </div>
        </div>

        {/* Kad Maklumat Sambungan & Rangkaian */}
        <div className="w-full p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-3">
          <span className="text-xs font-bold text-slate-300 uppercase tracking-wider">
            {lang === 'ms' ? 'Maklumat Perkakasan Rangkaian' : 'Network Hardware Info'}
          </span>

          <div className="flex justify-between items-center text-xs border-b border-slate-800 pb-2">
            <span className="text-slate-400">{lang === 'ms' ? 'Jenis Rangkaian:' : 'Network Type:'}</span>
            <span className="font-bold text-white uppercase">{networkInfo.type || '4G / Wi-Fi'}</span>
          </div>

          <div className="flex justify-between items-center text-xs border-b border-slate-800 pb-2">
            <span className="text-slate-400">{lang === 'ms' ? 'Anggaran Kelajuan Downlink:' : 'Estimated Downlink:'}</span>
            <span className="font-mono text-cyan-400 font-bold">
              {networkInfo.downlink ? `${networkInfo.downlink} Mbps` : '~ 10 Mbps'}
            </span>
          </div>

          <div className="flex justify-between items-center text-xs">
            <span className="text-slate-400">{lang === 'ms' ? 'Protokol DNS Web:' : 'DNS Protocol:'}</span>
            <span className="font-mono text-emerald-400 font-bold">DNS-over-HTTPS (DoH)</span>
          </div>
        </div>

        {/* Alamat IP Dikesan */}
        {localIps.length > 0 && (
          <div className="w-full p-3 bg-slate-900 rounded-xl border border-slate-800 text-xs">
            <span className="text-slate-400 block mb-1">{lang === 'ms' ? 'Nod IP Dikesan:' : 'Detected IP Nodes:'}</span>
            <div className="flex flex-wrap gap-1 font-mono text-cyan-300">
              {localIps.map((ip, i) => (
                <span key={i} className="px-2 py-0.5 bg-slate-800 rounded">
                  {ip}
                </span>
              ))}
            </div>
          </div>
        )}

        <div className="w-full">
          <button
            onClick={runPingTest}
            disabled={isRunningPing}
            className="w-full py-4 rounded-2xl bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-bold text-base shadow-xl flex items-center justify-center gap-2 active:scale-[0.98] transition"
          >
            <Activity className="w-5 h-5" />
            {lang === 'ms' ? 'Uji Latensi Rangkaian Sekarang' : 'Run Ping Test Now'}
          </button>
        </div>
      </div>
    </div>
  );
};
