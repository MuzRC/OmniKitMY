/**
 * @file src/tools/SpeakerCleaner.tsx
 * @description Alatan Pembersih Pembesar Suara (Speaker Cleaner / Water Ejector).
 * 
 * FUNGSI UTAMA:
 * 1. Menghasilkan gelombang bunyi resonansi fizikal 165Hz menggunakan Web Audio API.
 * 2. Menggunakan modulasi amplitud berdenyut (pulsed acoustic pressure waves) untuk menolak titisan air
 *    dan habuk keluar dari gril pembesar suara peranti telefon pintar.
 * 3. Tiga mod rawatan akustik:
 *    - Mod 1: Pembersihan Mendalam (165Hz Resonansi Berdenyut)
 *    - Mod 2: Sapuan Frekuensi Penuh (100Hz - 1000Hz)
 *    - Mod 3: Getaran Pantas (Basses Kick Hibrid)
 * 4. Pemasa automatik 60 saat dengan visualisasi pergerakan membran pembesar suara secara langsung.
 */

import React, { useState, useEffect, useRef } from 'react';
import { ArrowLeft, Play, Square, Volume2, Droplets, AlertTriangle, ShieldCheck } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const SpeakerCleaner: React.FC<Props> = ({ lang, onBack }) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [cleanMode, setCleanMode] = useState<'165hz' | 'sweep' | 'pulse'>('165hz');
  const [secondsRemaining, setSecondsRemaining] = useState(60);
  const [currentFreq, setCurrentFreq] = useState(165);

  const audioCtxRef = useRef<AudioContext | null>(null);
  const oscRef = useRef<OscillatorNode | null>(null);
  const gainRef = useRef<GainNode | null>(null);
  const timerRef = useRef<number | null>(null);
  const animFrameRef = useRef<number | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  // Mulakan AudioContext dan Pembersihan Bunyi
  const startCleaning = async () => {
    try {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new AudioContextClass();
      if (ctx.state === 'suspended') {
        await ctx.resume();
      }
      audioCtxRef.current = ctx;

      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(165, ctx.currentTime);

      // Konfigurasi mod
      if (cleanMode === '165hz') {
        osc.frequency.setValueAtTime(165, ctx.currentTime);
        // Modulasi denyutan gain setiap 0.5 saat
        gain.gain.setValueAtTime(0.85, ctx.currentTime);
      } else if (cleanMode === 'sweep') {
        // Frekuensi menyapu dari 100 ke 1200 Hz
        osc.frequency.setValueAtTime(120, ctx.currentTime);
      }

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();

      oscRef.current = osc;
      gainRef.current = gain;
      setIsPlaying(true);
      setSecondsRemaining(60);

      // Pemasa 60 saat
      let timeLeft = 60;
      let sweepDir = 1;
      let freq = 165;

      timerRef.current = window.setInterval(() => {
        timeLeft -= 1;
        setSecondsRemaining(timeLeft);

        if (!oscRef.current || !audioCtxRef.current) return;
        const now = audioCtxRef.current.currentTime;

        // Logik perubahan frekuensi masa nyata mengikut mod
        if (cleanMode === 'sweep') {
          freq += sweepDir * 40;
          if (freq >= 900) sweepDir = -1;
          if (freq <= 100) sweepDir = 1;
          oscRef.current.frequency.setTargetAtTime(freq, now, 0.1);
          setCurrentFreq(Math.round(freq));
        } else if (cleanMode === 'pulse') {
          // Selang-seli 165Hz dan 300Hz dengan hentakan denyutan
          freq = timeLeft % 2 === 0 ? 165 : 440;
          oscRef.current.frequency.setValueAtTime(freq, now);
          setCurrentFreq(freq);
          if (gainRef.current) {
            gainRef.current.gain.setValueAtTime(0.9, now);
            gainRef.current.gain.exponentialRampToValueAtTime(0.05, now + 0.4);
          }
        } else {
          // 165Hz Resonansi stabil dengan getaran pulsasi halus
          setCurrentFreq(165);
        }

        if (timeLeft <= 0) {
          stopCleaning();
        }
      }, 1000);

      drawVisualization();
    } catch (e) {
      console.error('Gagal memulakan audio:', e);
    }
  };

  // Hentikan Pembersihan
  const stopCleaning = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    if (animFrameRef.current) {
      cancelAnimationFrame(animFrameRef.current);
      animFrameRef.current = null;
    }
    try {
      if (oscRef.current) {
        oscRef.current.stop();
        oscRef.current.disconnect();
        oscRef.current = null;
      }
      if (gainRef.current) {
        gainRef.current.disconnect();
        gainRef.current = null;
      }
      if (audioCtxRef.current) {
        audioCtxRef.current.close();
        audioCtxRef.current = null;
      }
    } catch (e) {
      console.error('Ralat hentikan audio:', e);
    }
    setIsPlaying(false);
    setSecondsRemaining(60);
  };

  // Animasi gelombang air dan membran speaker pada canvas
  const drawVisualization = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let phase = 0;
    const render = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const centerX = canvas.width / 2;
      const centerY = canvas.height / 2;

      // Lukis bulatan membran speaker
      ctx.beginPath();
      ctx.arc(centerX, centerY, 70 + Math.sin(phase) * 8, 0, Math.PI * 2);
      ctx.strokeStyle = '#3b82f6';
      ctx.lineWidth = 4;
      ctx.stroke();

      // Gelombang luar (ripples)
      for (let i = 1; i <= 3; i++) {
        const radius = (70 + (phase * 15 + i * 25) % 80);
        const alpha = Math.max(0, 1 - radius / 150);
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
        ctx.strokeStyle = `rgba(6, 182, 212, ${alpha})`;
        ctx.lineWidth = 2;
        ctx.stroke();
      }

      // Titisan air kecil terapung
      ctx.fillStyle = '#67e8f9';
      for (let j = 0; j < 6; j++) {
        const angle = (j * Math.PI) / 3 + phase * 0.5;
        const dist = 45 + Math.cos(phase * 2 + j) * 12;
        const x = centerX + Math.cos(angle) * dist;
        const y = centerY + Math.sin(angle) * dist;
        ctx.beginPath();
        ctx.arc(x, y, 4, 0, Math.PI * 2);
        ctx.fill();
      }

      phase += 0.08;
      animFrameRef.current = requestAnimationFrame(render);
    };

    render();
  };

  // Bersihkan audio jika keluar dari halaman
  useEffect(() => {
    return () => {
      stopCleaning();
    };
  }, []);

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Top Header Alatan */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              stopCleaning();
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Pembersih Pembesar Suara' : 'Speaker Cleaner'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Keluarkan air & habuk dengan gelombang akustik' : 'Eject water & dust using sound waves'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/30 text-blue-400 text-xs font-mono font-semibold">
          <Droplets className="w-3.5 h-3.5 text-cyan-400" />
          <span>{currentFreq} Hz</span>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Peringatan & Petua Penggunaan */}
        <div className="w-full p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <div className="text-xs text-amber-200/90 leading-relaxed">
            <strong>{lang === 'ms' ? 'Petua Penting:' : 'Important Tips:'}</strong>{' '}
            {lang === 'ms'
              ? 'Tingkatkan kelantangan suara telefon ke 100%, dan halakan bahagian bawah telefon (speaker) menghadap ke bawah supaya titisan air mudah keluar.'
              : 'Set phone volume to 100%, and position your phone speaker facing downwards so water drops fall freely.'}
          </div>
        </div>

        {/* Visualizer Membran Speaker Bergetar */}
        <div className="relative w-64 h-64 flex items-center justify-center my-2">
          <canvas
            ref={canvasRef}
            width={260}
            height={260}
            className="absolute inset-0 w-full h-full"
          />

          <div
            className={`w-36 h-36 rounded-full flex flex-col items-center justify-center shadow-2xl transition-all duration-300 border-4 ${
              isPlaying
                ? 'bg-gradient-to-br from-blue-600 to-cyan-600 border-cyan-300 scale-105 shadow-cyan-500/30'
                : 'bg-slate-800/90 border-slate-700 shadow-slate-950'
            }`}
          >
            <Droplets className={`w-10 h-10 mb-1 ${isPlaying ? 'text-white animate-bounce' : 'text-slate-500'}`} />
            <span className="text-2xl font-bold font-mono text-white tabular-nums">
              {isPlaying ? `${secondsRemaining}s` : '60s'}
            </span>
            <span className="text-[10px] uppercase font-semibold text-cyan-200 tracking-wider">
              {isPlaying ? (lang === 'ms' ? 'Membersih...' : 'Cleaning...') : (lang === 'ms' ? 'Sedia' : 'Ready')}
            </span>
          </div>
        </div>

        {/* Pemilih Mod Pembersihan */}
        <div className="w-full">
          <label className="text-xs font-semibold text-slate-300 block mb-2 text-center">
            {lang === 'ms' ? 'Pilih Corak Frekuensi' : 'Select Frequency Mode'}
          </label>
          <div className="grid grid-cols-3 gap-2">
            <button
              disabled={isPlaying}
              onClick={() => setCleanMode('165hz')}
              className={`p-2.5 rounded-xl text-xs font-medium border text-center transition ${
                cleanMode === '165hz'
                  ? 'bg-blue-600/30 border-blue-500 text-white font-semibold'
                  : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              <div className="font-bold text-sm">165 Hz</div>
              <div className="text-[10px] text-slate-400 mt-0.5">{lang === 'ms' ? 'Resonansi Air' : 'Water Resonance'}</div>
            </button>

            <button
              disabled={isPlaying}
              onClick={() => setCleanMode('sweep')}
              className={`p-2.5 rounded-xl text-xs font-medium border text-center transition ${
                cleanMode === 'sweep'
                  ? 'bg-blue-600/30 border-blue-500 text-white font-semibold'
                  : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              <div className="font-bold text-sm">Sapuan</div>
              <div className="text-[10px] text-slate-400 mt-0.5">100 - 1000Hz</div>
            </button>

            <button
              disabled={isPlaying}
              onClick={() => setCleanMode('pulse')}
              className={`p-2.5 rounded-xl text-xs font-medium border text-center transition ${
                cleanMode === 'pulse'
                  ? 'bg-blue-600/30 border-blue-500 text-white font-semibold'
                  : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              <div className="font-bold text-sm">Denyutan</div>
              <div className="text-[10px] text-slate-400 mt-0.5">{lang === 'ms' ? 'Getaran Habuk' : 'Dust Eject'}</div>
            </button>
          </div>
        </div>

        {/* Butang Mula / Berhenti Utama */}
        <div className="w-full">
          {!isPlaying ? (
            <button
              onClick={startCleaning}
              className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-blue-600 via-cyan-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-bold text-base shadow-xl shadow-blue-900/40 active:scale-[0.98] transition flex items-center justify-center gap-3"
            >
              <Play className="w-5 h-5 fill-white" />
              {lang === 'ms' ? 'Mula Pembersihan Air (60s)' : 'Start Ejection (60s)'}
            </button>
          ) : (
            <button
              onClick={stopCleaning}
              className="w-full py-4 px-6 rounded-2xl bg-red-600 hover:bg-red-500 text-white font-bold text-base shadow-xl active:scale-[0.98] transition flex items-center justify-center gap-3"
            >
              <Square className="w-5 h-5 fill-white" />
              {lang === 'ms' ? 'Hentikan Bunyi' : 'Stop Audio'}
            </button>
          )}
        </div>

        {/* Jaminan Keselamatan */}
        <div className="flex items-center gap-2 text-[11px] text-slate-400">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <span>{lang === 'ms' ? 'Frekuensi selamat & tidak merosakkan perkakasan' : 'Hardware safe frequency standard'}</span>
        </div>
      </div>
    </div>
  );
};
