/**
 * @file src/tools/ToneGeneratorTool.tsx
 * @description Alatan Penjana Nada & Frekuensi Audio (Tone Generator & Frequency Synth).
 * 
 * FUNGSI UTAMA:
 * 1. Menjana gelombang frekuensi audio daripada 20 Hz hingga 20,000 Hz (had pendengaran manusia).
 * 2. 4 bentuk gelombang akustik: Sine (Sinus), Square (Segi Empat), Triangle (Segitiga), Sawtooth (Gigi Gergaji).
 * 3. Prasetel frekuensi piawai (432Hz Harmonik, 440Hz Nada Konsert A, 528Hz Solfeggio, 1000Hz Ujian Beep).
 * 4. Kawalan kelantangan bunyi selamat dan visualisasi bentuk gelombang secara langsung pada kanvas.
 */

import React, { useState, useEffect, useRef } from 'react';
import { ArrowLeft, Play, Square, Volume2, Activity, Zap } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const ToneGeneratorTool: React.FC<Props> = ({ lang, onBack }) => {
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [frequency, setFrequency] = useState<number>(440);
  const [waveType, setWaveType] = useState<OscillatorType>('sine');
  const [volume, setVolume] = useState<number>(0.5);

  const audioCtxRef = useRef<AudioContext | null>(null);
  const oscRef = useRef<OscillatorNode | null>(null);
  const gainRef = useRef<GainNode | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animFrameRef = useRef<number | null>(null);

  const startTone = async () => {
    try {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new AudioContextClass();
      if (ctx.state === 'suspended') {
        await ctx.resume();
      }
      audioCtxRef.current = ctx;

      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = waveType;
      osc.frequency.setValueAtTime(frequency, ctx.currentTime);
      gain.gain.setValueAtTime(volume, ctx.currentTime);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();

      oscRef.current = osc;
      gainRef.current = gain;
      setIsPlaying(true);
      drawWave();
    } catch (e) {
      console.error('Gagal menjana audio:', e);
    }
  };

  const stopTone = () => {
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
      console.error('Ralat hentikan nada:', e);
    }
    setIsPlaying(false);
  };

  // Kemas kini frekuensi secara langsung jika sedang bermain
  useEffect(() => {
    if (oscRef.current && audioCtxRef.current) {
      oscRef.current.frequency.setTargetAtTime(frequency, audioCtxRef.current.currentTime, 0.05);
    }
  }, [frequency]);

  // Kemas kini bentuk gelombang
  useEffect(() => {
    if (oscRef.current) {
      oscRef.current.type = waveType;
    }
  }, [waveType]);

  // Kemas kini kelantangan
  useEffect(() => {
    if (gainRef.current && audioCtxRef.current) {
      gainRef.current.gain.setTargetAtTime(volume, audioCtxRef.current.currentTime, 0.05);
    }
  }, [volume]);

  // Lukis bentuk gelombang pada kanvas
  const drawWave = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let phase = 0;
    const render = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.beginPath();
      ctx.strokeStyle = '#06b6d4';
      ctx.lineWidth = 2.5;

      const midY = canvas.height / 2;
      const width = canvas.width;
      const amp = (canvas.height / 2.5) * volume;

      for (let x = 0; x < width; x++) {
        let y = midY;
        const t = (x / width) * 4 * Math.PI + phase;

        if (waveType === 'sine') {
          y = midY + Math.sin(t) * amp;
        } else if (waveType === 'square') {
          y = midY + (Math.sin(t) >= 0 ? amp : -amp);
        } else if (waveType === 'triangle') {
          y = midY + (Math.asin(Math.sin(t)) / (Math.PI / 2)) * amp;
        } else if (waveType === 'sawtooth') {
          y = midY + (((t % (Math.PI * 2)) / Math.PI) - 1) * amp;
        }

        if (x === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();

      phase += 0.15;
      animFrameRef.current = requestAnimationFrame(render);
    };

    render();
  };

  useEffect(() => {
    return () => {
      stopTone();
    };
  }, []);

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              stopTone();
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Penjana Nada (Tone Generator)' : 'Tone Generator'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Jana frekuensi bunyi dari 20 Hz hingga 20,000 Hz' : 'Generate frequencies from 20 to 20,000 Hz'}
            </p>
          </div>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Paparan Nilai Frekuensi Utama */}
        <div className="text-center">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest block mb-1">
            {lang === 'ms' ? 'Frekuensi Semasa' : 'Current Frequency'}
          </span>
          <div className="text-6xl font-black font-mono text-cyan-400 tracking-tight tabular-nums">
            {frequency}
          </div>
          <span className="text-sm font-bold text-white mt-1">Hertz (Hz)</span>
        </div>

        {/* Visualizer Bentuk Gelombang Kanvas */}
        <div className="w-full p-3 rounded-2xl bg-[#121e36] border border-slate-700/60 shadow-inner">
          <canvas
            ref={canvasRef}
            width={340}
            height={90}
            className="w-full h-24 bg-slate-950/80 rounded-xl"
          />
        </div>

        {/* Peluncur Frekuensi (Slider) */}
        <div className="w-full flex flex-col gap-2">
          <div className="flex justify-between text-xs text-slate-400 font-mono">
            <span>20 Hz</span>
            <span>440 Hz</span>
            <span>20,000 Hz</span>
          </div>
          <input
            type="range"
            min="20"
            max="10000"
            step="1"
            value={frequency}
            onChange={(e) => setFrequency(parseInt(e.target.value))}
            className="w-full h-2.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400"
          />
        </div>

        {/* Pemilih Bentuk Gelombang (Waveform) */}
        <div className="w-full">
          <label className="text-xs font-semibold text-slate-300 block mb-2 text-center">
            {lang === 'ms' ? 'Bentuk Gelombang' : 'Waveform'}
          </label>
          <div className="grid grid-cols-4 gap-2">
            {[
              { id: 'sine', label: 'Sine' },
              { id: 'square', label: 'Square' },
              { id: 'triangle', label: 'Triangle' },
              { id: 'sawtooth', label: 'Sawtooth' },
            ].map((wave) => (
              <button
                key={wave.id}
                onClick={() => setWaveType(wave.id as OscillatorType)}
                className={`p-2.5 rounded-xl text-xs font-semibold border transition ${
                  waveType === wave.id
                    ? 'bg-blue-600 border-blue-500 text-white shadow-md'
                    : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                {wave.label}
              </button>
            ))}
          </div>
        </div>

        {/* Prasetel Frekuensi Popular */}
        <div className="w-full">
          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-2">
            {lang === 'ms' ? 'Prasetel Frekuensi Pantas' : 'Quick Frequency Presets'}
          </span>
          <div className="grid grid-cols-4 gap-2">
            {[
              { hz: 165, name: '165 Hz (Speaker)' },
              { hz: 432, name: '432 Hz (Harmonik)' },
              { hz: 440, name: '440 Hz (Konsert A)' },
              { hz: 1000, name: '1 kHz (Ujian)' },
            ].map((preset) => (
              <button
                key={preset.hz}
                onClick={() => setFrequency(preset.hz)}
                className="p-2 rounded-xl bg-slate-900 border border-slate-800 hover:border-cyan-500/50 text-slate-200 text-xs font-mono transition text-center"
              >
                {preset.hz} Hz
              </button>
            ))}
          </div>
        </div>

        {/* Butang Mula / Henti */}
        <div className="w-full">
          {!isPlaying ? (
            <button
              onClick={startTone}
              className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 text-white font-bold text-base shadow-xl shadow-blue-900/40 active:scale-[0.98] transition flex items-center justify-center gap-3"
            >
              <Play className="w-5 h-5 fill-white" />
              {lang === 'ms' ? 'Mainkan Nada Frekuensi' : 'Play Audio Tone'}
            </button>
          ) : (
            <button
              onClick={stopTone}
              className="w-full py-4 px-6 rounded-2xl bg-red-600 hover:bg-red-500 text-white font-bold text-base shadow-xl active:scale-[0.98] transition flex items-center justify-center gap-3"
            >
              <Square className="w-5 h-5 fill-white" />
              {lang === 'ms' ? 'Hentikan Nada' : 'Stop Tone'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
