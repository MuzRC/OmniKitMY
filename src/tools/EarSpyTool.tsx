/**
 * @file src/tools/EarSpyTool.tsx
 * @description Alatan Penguat Pendengaran & Perisik Telinga (Ear Spy & Sound Amplifier).
 * 
 * FUNGSI UTAMA:
 * 1. Menangkap audio mikrofon peranti dan menguatkan frekuensi pertuturan vokal manusia.
 * 2. Penapis Jalur Audio (BiquadFilterNode Bandpass) untuk menapis desiran angin dan bunyi deruman rendah.
 * 3. Pelaras Kuasa Penguat Gandaan (Gain Amplifier) dari 1.0x hingga 5.0x.
 * 4. Pengehad Keselamatan Audio (Dynamic Compressor Limiter) untuk mengelakkan bunyi terlampau kuat.
 */

import React, { useState, useEffect, useRef } from 'react';
import { ArrowLeft, Headphones, Mic, Volume2, ShieldAlert, Play, Square } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const EarSpyTool: React.FC<Props> = ({ lang, onBack }) => {
  const [isActive, setIsActive] = useState<boolean>(false);
  const [boostLevel, setBoostLevel] = useState<number>(2.5);
  const [filterVoice, setFilterVoice] = useState<boolean>(true);

  const audioCtxRef = useRef<AudioContext | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const gainNodeRef = useRef<GainNode | null>(null);

  const startAmplifier = async () => {
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
      if (ctx.state === 'suspended') await ctx.resume();
      audioCtxRef.current = ctx;

      const source = ctx.createMediaStreamSource(stream);

      // Penapis suara manusia (Bandpass 300Hz - 3400Hz)
      const filter = ctx.createBiquadFilter();
      filter.type = 'bandpass';
      filter.frequency.setValueAtTime(1500, ctx.currentTime);
      filter.Q.setValueAtTime(1.0, ctx.currentTime);

      // Pemampat dinamik untuk lindungi telinga
      const compressor = ctx.createDynamicsCompressor();
      compressor.threshold.setValueAtTime(-18, ctx.currentTime);
      compressor.knee.setValueAtTime(12, ctx.currentTime);
      compressor.ratio.setValueAtTime(8, ctx.currentTime);

      const gain = ctx.createGain();
      gain.gain.setValueAtTime(boostLevel, ctx.currentTime);
      gainNodeRef.current = gain;

      if (filterVoice) {
        source.connect(filter);
        filter.connect(compressor);
      } else {
        source.connect(compressor);
      }

      compressor.connect(gain);
      gain.connect(ctx.destination);

      setIsActive(true);
    } catch (err) {
      console.error('Gagal mulakan audio:', err);
      alert(lang === 'ms' ? 'Sila benarkan akses mikrofon.' : 'Please allow microphone access.');
    }
  };

  const stopAmplifier = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    if (audioCtxRef.current) {
      audioCtxRef.current.close();
      audioCtxRef.current = null;
    }
    setIsActive(false);
  };

  useEffect(() => {
    if (gainNodeRef.current && audioCtxRef.current) {
      gainNodeRef.current.gain.setTargetAtTime(boostLevel, audioCtxRef.current.currentTime, 0.05);
    }
  }, [boostLevel]);

  useEffect(() => {
    return () => {
      stopAmplifier();
    };
  }, []);

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              stopAmplifier();
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Perisik Telinga (Ear Spy)' : 'Ear Spy Amplifier'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Kuatkan pendengaran suara melalui fon kepala' : 'Amplify ambient voice via headphones'}
            </p>
          </div>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Peringatan Fon Kepala Wajib */}
        <div className="w-full p-4 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-start gap-3">
          <Headphones className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
          <div className="text-xs text-amber-200/90 leading-relaxed">
            <strong>{lang === 'ms' ? 'PENTING:' : 'IMPORTANT:'}</strong>{' '}
            {lang === 'ms'
              ? 'Pastikan anda memakai fon kepala (earphones / headphones). Jika menggunakan pembesar suara biasa, bunyi dengungan maklum balas (feedback echo) akan berlaku.'
              : 'Please plug in earphones or headphones to prevent loud acoustic feedback loop.'}
          </div>
        </div>

        {/* Paparan Visual Status Utama */}
        <div className="relative w-64 h-64 rounded-full bg-gradient-to-b from-[#131f3b] to-[#0d162a] border-4 border-slate-700 shadow-2xl flex flex-col items-center justify-center p-6 text-center">
          <Headphones className={`w-14 h-14 mb-2 transition-transform duration-300 ${isActive ? 'text-cyan-400 scale-110 animate-pulse' : 'text-slate-500'}`} />
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest">
            {lang === 'ms' ? 'Gandaan Suara' : 'Amplification'}
          </span>
          <div className="text-5xl font-black font-mono text-white tracking-tight tabular-nums mt-1">
            {boostLevel.toFixed(1)}x
          </div>
          <span className="text-xs text-cyan-300 font-semibold mt-1">
            {isActive ? (lang === 'ms' ? '● Aktif Menyaring' : '● Live Amplifying') : (lang === 'ms' ? 'Tidak Aktif' : 'Standby')}
          </span>
        </div>

        {/* Pelaras Kuasa Penguat Gandaan */}
        <div className="w-full flex flex-col gap-2">
          <div className="flex justify-between text-xs text-slate-400 font-semibold">
            <span>{lang === 'ms' ? 'Tahap Kuasa Gandaan (Boost):' : 'Boost Multiplier:'}</span>
            <span className="font-mono text-cyan-400">{boostLevel.toFixed(1)}x</span>
          </div>
          <input
            type="range"
            min="1.0"
            max="5.0"
            step="0.1"
            value={boostLevel}
            onChange={(e) => setBoostLevel(parseFloat(e.target.value))}
            className="w-full h-2.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400"
          />
        </div>

        {/* Butang Togol Penguat */}
        <div className="w-full">
          {!isActive ? (
            <button
              onClick={startAmplifier}
              className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-bold text-base shadow-xl flex items-center justify-center gap-2 active:scale-[0.98] transition"
            >
              <Play className="w-5 h-5 fill-white" />
              {lang === 'ms' ? 'Aktifkan Penguat Suara' : 'Start Sound Amplifier'}
            </button>
          ) : (
            <button
              onClick={stopAmplifier}
              className="w-full py-4 px-6 rounded-2xl bg-red-600 hover:bg-red-500 text-white font-bold text-base shadow-xl active:scale-[0.98] transition flex items-center justify-center gap-2"
            >
              <Square className="w-5 h-5 fill-white" />
              {lang === 'ms' ? 'Hentikan Penguat' : 'Stop Amplifier'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
