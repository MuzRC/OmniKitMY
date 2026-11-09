/**
 * @file src/tools/SoundMeter.tsx
 * @description Alatan Pengukur Tahap Hingar Bunyi (Sound & Noise Decibel Meter).
 * 
 * FUNGSI UTAMA:
 * 1. Mengukur tahap kelantangan desibel (dB SPL) masa nyata menggunakan mikrofon peranti.
 * 2. Menganalisis isyarat audio secara langsung dengan Web Audio API (RMS & AnalyserNode).
 * 3. Menjana graf sejarah tahap bunyi dan tolok separa bulat beranimasi.
 * 4. Menyimpan rekod Min, Purata (Avg), dan Maksimum (Max / Peak dB).
 * 5. Menyediakan rujukan skala kesihatan pendengaran (WHO safety scale).
 */

import React, { useState, useEffect, useRef } from 'react';
import { ArrowLeft, Mic, MicOff, Volume2, RotateCcw, AlertCircle, Award } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const SoundMeter: React.FC<Props> = ({ lang, onBack }) => {
  const [isRecording, setIsRecording] = useState(false);
  const [currentDb, setCurrentDb] = useState(0);
  const [minDb, setMinDb] = useState<number | null>(null);
  const [maxDb, setMaxDb] = useState<number | null>(null);
  const [avgDb, setAvgDb] = useState(0);
  const [micError, setMicError] = useState<string | null>(null);

  const audioCtxRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const animFrameRef = useRef<number | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const samplesCountRef = useRef(0);
  const totalDbSumRef = useRef(0);
  const historyDataRef = useRef<number[]>([]);

  // Mulakan Perekod Audio Mikrofon
  const startListening = async () => {
    setMicError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: false,
          noiseSuppression: false,
          autoGainControl: false,
        },
      });
      streamRef.current = stream;

      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new AudioContextClass();
      if (ctx.state === 'suspended') {
        await ctx.resume();
      }
      audioCtxRef.current = ctx;

      const source = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = 0.4;
      source.connect(analyser);
      analyserRef.current = analyser;

      setIsRecording(true);
      resetStats();
      analyzeAudio();
    } catch (err) {
      console.error('Akses mikrofon gagal:', err);
      setMicError(
        lang === 'ms'
          ? 'Kebenaran mikrofon tidak diberikan. Sila benarkan akses mikrofon untuk mengukur bunyi.'
          : 'Microphone access denied. Please allow microphone permissions.'
      );
      setIsRecording(false);
    }
  };

  // Hentikan Perekodan
  const stopListening = () => {
    if (animFrameRef.current) {
      cancelAnimationFrame(animFrameRef.current);
      animFrameRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    if (audioCtxRef.current) {
      audioCtxRef.current.close();
      audioCtxRef.current = null;
    }
    setIsRecording(false);
    setCurrentDb(0);
  };

  // Set semula statistik ukuran
  const resetStats = () => {
    setMinDb(null);
    setMaxDb(null);
    setAvgDb(0);
    samplesCountRef.current = 0;
    totalDbSumRef.current = 0;
    historyDataRef.current = [];
  };

  // Gelung analisis audio masa nyata
  const analyzeAudio = () => {
    const analyser = analyserRef.current;
    if (!analyser) return;

    const buffer = new Float32Array(analyser.fftSize);

    const update = () => {
      analyser.getFloatTimeDomainData(buffer);

      // Kira RMS (Root Mean Square)
      let sumSquares = 0;
      for (let i = 0; i < buffer.length; i++) {
        sumSquares += buffer[i] * buffer[i];
      }
      const rms = Math.sqrt(sumSquares / buffer.length);

      // Formula penentukuran desibel untuk telefon pintar (~30dB minimum ambien ke 110dB)
      let db = 0;
      if (rms > 0.00001) {
        db = 20 * Math.log10(rms) + 95;
      } else {
        db = 28;
      }
      // Hadkan antara 25dB dan 115dB
      db = Math.max(25, Math.min(115, Math.round(db)));

      setCurrentDb(db);

      // Kemas kini Min, Max, Avg
      setMinDb((prev) => (prev === null ? db : Math.min(prev, db)));
      setMaxDb((prev) => (prev === null ? db : Math.max(prev, db)));

      samplesCountRef.current += 1;
      totalDbSumRef.current += db;
      setAvgDb(Math.round(totalDbSumRef.current / samplesCountRef.current));

      // Simpan sejarah untuk graf
      historyDataRef.current.push(db);
      if (historyDataRef.current.length > 50) {
        historyDataRef.current.shift();
      }

      drawWaveform(historyDataRef.current);
      animFrameRef.current = requestAnimationFrame(update);
    };

    update();
  };

  // Lukis graf sejarah desibel pada canvas
  const drawWaveform = (history: number[]) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (history.length < 2) return;

    ctx.beginPath();
    ctx.strokeStyle = '#06b6d4';
    ctx.lineWidth = 2.5;

    const step = canvas.width / (history.length - 1);
    for (let i = 0; i < history.length; i++) {
      const val = history[i];
      // Normalisasi nilai antara 30dB ke 110dB ke tinggi canvas
      const normalizedY = canvas.height - ((val - 25) / (115 - 25)) * canvas.height;
      const x = i * step;
      if (i === 0) {
        ctx.moveTo(x, normalizedY);
      } else {
        ctx.lineTo(x, normalizedY);
      }
    }
    ctx.stroke();

    // Garis ambang bahaya 85dB
    const dangerY = canvas.height - ((85 - 25) / (115 - 25)) * canvas.height;
    ctx.beginPath();
    ctx.strokeStyle = 'rgba(239, 68, 68, 0.4)';
    ctx.setLineDash([4, 4]);
    ctx.moveTo(0, dangerY);
    ctx.lineTo(canvas.width, dangerY);
    ctx.stroke();
    ctx.setLineDash([]);
  };

  // Rujukan Tahap Bunyi
  const getNoiseLevelDescription = (db: number) => {
    if (db < 40) return { label: lang === 'ms' ? 'Sangat Tenang (Bilik Tidur)' : 'Very Quiet (Bedroom)', color: 'text-emerald-400' };
    if (db < 60) return { label: lang === 'ms' ? 'Sederhana (Perbualan Normal)' : 'Moderate (Normal Chat)', color: 'text-blue-400' };
    if (db < 75) return { label: lang === 'ms' ? 'Bising (Pejabat / Restoran)' : 'Loud (Office / Busy Area)', color: 'text-yellow-400' };
    if (db < 85) return { label: lang === 'ms' ? 'Sangat Bising (Lalulintas)' : 'Very Loud (City Traffic)', color: 'text-orange-400' };
    return { label: lang === 'ms' ? 'Bahaya untuk Pendengaran (>85dB)' : 'Dangerous Hearing Risk (>85dB)', color: 'text-red-400 font-bold' };
  };

  useEffect(() => {
    return () => {
      stopListening();
    };
  }, []);

  const desc = getNoiseLevelDescription(currentDb);

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              stopListening();
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Pengukur Bunyi (dB)' : 'Sound Meter (dB)'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Ukur tahap desibel kelantangan masa nyata' : 'Real-time decibel noise level monitor'}
            </p>
          </div>
        </div>

        {isRecording && (
          <button
            onClick={resetStats}
            className="p-2 rounded-xl bg-slate-800 text-slate-300 hover:text-white transition"
            title="Set Semula Ukuran"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
        )}
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {micError && (
          <div className="w-full p-3 rounded-xl bg-red-950/60 border border-red-700/50 text-red-200 text-xs">
            {micError}
          </div>
        )}

        {/* Paparan Desibel Utama Berpusat */}
        <div className="relative w-64 h-64 flex flex-col items-center justify-center rounded-full bg-gradient-to-b from-[#131f3b] to-[#0d162a] border-4 border-slate-700 shadow-2xl p-6 text-center">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest mb-1">
            {lang === 'ms' ? 'Tahap Bunyi Semasa' : 'Sound Pressure'}
          </span>
          <div className="text-6xl font-black font-mono text-white tracking-tight tabular-nums drop-shadow-md">
            {currentDb}
          </div>
          <span className="text-sm font-bold text-cyan-400 mt-1">dB (SPL)</span>

          <div className={`text-xs mt-3 px-3 py-1 rounded-full bg-slate-900/80 border border-slate-800 ${desc.color}`}>
            {desc.label}
          </div>
        </div>

        {/* Ringkasan Statistik (Min, Avg, Max) */}
        <div className="w-full grid grid-cols-3 gap-2.5">
          <div className="p-3 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-[11px] text-slate-400 uppercase block font-semibold">Min</span>
            <span className="text-xl font-bold font-mono text-emerald-400 tabular-nums">
              {minDb !== null ? `${minDb} dB` : '--'}
            </span>
          </div>

          <div className="p-3 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-[11px] text-slate-400 uppercase block font-semibold">{lang === 'ms' ? 'Purata' : 'Avg'}</span>
            <span className="text-xl font-bold font-mono text-cyan-400 tabular-nums">
              {avgDb > 0 ? `${avgDb} dB` : '--'}
            </span>
          </div>

          <div className="p-3 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-[11px] text-slate-400 uppercase block font-semibold">{lang === 'ms' ? 'Maks (Peak)' : 'Max'}</span>
            <span className="text-xl font-bold font-mono text-red-400 tabular-nums">
              {maxDb !== null ? `${maxDb} dB` : '--'}
            </span>
          </div>
        </div>

        {/* Graf Gelombang Masa Nyata */}
        <div className="w-full p-3.5 rounded-2xl bg-[#121e36] border border-slate-700/60">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
            <span>{lang === 'ms' ? 'Graf Aliran Desibel' : 'Live Decibel Flow'}</span>
            <span className="text-red-400 font-mono text-[10px]">{lang === 'ms' ? 'Garis Merah: Had 85 dB' : 'Red line: 85 dB Limit'}</span>
          </div>
          <canvas
            ref={canvasRef}
            width={340}
            height={90}
            className="w-full h-24 bg-slate-950/70 rounded-xl"
          />
        </div>

        {/* Butang Kawalan Mikrofon */}
        <div className="w-full">
          {!isRecording ? (
            <button
              onClick={startListening}
              className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 text-white font-bold text-base shadow-xl shadow-blue-900/40 active:scale-[0.98] transition flex items-center justify-center gap-3"
            >
              <Mic className="w-5 h-5" />
              {lang === 'ms' ? 'Mula Mengukur Bunyi' : 'Start Measuring Noise'}
            </button>
          ) : (
            <button
              onClick={stopListening}
              className="w-full py-4 px-6 rounded-2xl bg-red-600 hover:bg-red-500 text-white font-bold text-base shadow-xl active:scale-[0.98] transition flex items-center justify-center gap-3"
            >
              <MicOff className="w-5 h-5" />
              {lang === 'ms' ? 'Hentikan Ukuran' : 'Stop Measuring'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
