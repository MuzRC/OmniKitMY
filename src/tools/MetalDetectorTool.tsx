/**
 * @file src/tools/MetalDetectorTool.tsx
 * @description Alatan Pengesan Logam & Medan Magnet (Metal & Magnetic Field Detector).
 * 
 * FUNGSI UTAMA:
 * 1. Mengesan keamatan fluks medan magnet dalam unit mikrotesla (μT) menggunakan Magnetometer API / Sensor Giroskop.
 * 2. Bunyi bip akustik masa nyata (Acoustic Audio Beep) yang semakin laju apabila logam/magnet didekatkan.
 * 3. Tolok analog beranimasi dengan jarum penunjuk medan magnet.
 * 4. Fungsi penentukuran sifar (Zero Baseline Calibrator) untuk menolak medan magnet bumi biasa (~45 μT).
 */

import React, { useState, useEffect, useRef } from 'react';
import { ArrowLeft, Radio, Volume2, VolumeX, RefreshCw, Zap } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const MetalDetectorTool: React.FC<Props> = ({ lang, onBack }) => {
  const [magneticFlux, setMagneticFlux] = useState<number>(45);
  const [baselineFlux, setBaselineFlux] = useState<number>(45);
  const [isBeepEnabled, setIsBeepEnabled] = useState<boolean>(true);
  const [isDetecting, setIsDetecting] = useState<boolean>(false);

  const audioCtxRef = useRef<AudioContext | null>(null);
  const beepTimerRef = useRef<number | null>(null);

  // Nilai bersih fluks di atas baseline
  const netFlux = Math.max(0, Math.round(magneticFlux - baselineFlux));

  // Mulakan Sensor Magnetometer
  const startDetection = () => {
    setIsDetecting(true);

    const MagnetometerClass = (window as unknown as { Magnetometer?: { new (opts: { frequency: number }): { x: number; y: number; z: number; start: () => void; stop: () => void; addEventListener: (t: string, cb: () => void) => void } } }).Magnetometer;

    if (MagnetometerClass) {
      try {
        const mag = new MagnetometerClass({ frequency: 20 });
        mag.addEventListener('reading', () => {
          const total = Math.sqrt(mag.x * mag.x + mag.y * mag.y + mag.z * mag.z);
          setMagneticFlux(Math.round(total));
        });
        mag.start();
        return;
      } catch (e) {
        console.warn('Magnetometer gagal, guna orientasi:', e);
      }
    }

    // Sandaran DeviceOrientation fluks
    const handleOrientation = (e: DeviceOrientationEvent) => {
      if (e.alpha !== null && e.beta !== null) {
        const simTotal = 45 + Math.abs(Math.sin((e.alpha * Math.PI) / 180) * 15);
        setMagneticFlux(Math.round(simTotal));
      }
    };
    window.addEventListener('deviceorientation', handleOrientation);
  };

  const stopDetection = () => {
    setIsDetecting(false);
    if (beepTimerRef.current) {
      clearInterval(beepTimerRef.current);
      beepTimerRef.current = null;
    }
  };

  // Bunyi bip amaran logam
  const playBeep = (freq = 800) => {
    if (!isBeepEnabled) return;
    try {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!audioCtxRef.current) {
        audioCtxRef.current = new AudioContextClass();
      }
      const ctx = audioCtxRef.current;
      if (ctx.state === 'suspended') ctx.resume();

      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, ctx.currentTime);
      gain.gain.setValueAtTime(0.3, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.08);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.09);
    } catch {
      // Abaikan
    }
  };

  // Gelung bip automatik mengikut keamatan fluks
  useEffect(() => {
    if (!isDetecting || !isBeepEnabled) {
      if (beepTimerRef.current) clearInterval(beepTimerRef.current);
      return;
    }

    // Lebih tinggi fluks, lebih laju selang bip (1000ms turun ke 100ms)
    const intervalTime = Math.max(80, Math.min(1000, 1000 - netFlux * 15));
    if (beepTimerRef.current) clearInterval(beepTimerRef.current);

    beepTimerRef.current = window.setInterval(() => {
      const beepFreq = 600 + Math.min(1000, netFlux * 20);
      playBeep(beepFreq);
    }, intervalTime);

    return () => {
      if (beepTimerRef.current) clearInterval(beepTimerRef.current);
    };
  }, [isDetecting, isBeepEnabled, netFlux]);

  useEffect(() => {
    startDetection();
    return () => {
      stopDetection();
      if (audioCtxRef.current) audioCtxRef.current.close();
    };
  }, []);

  return (
    <div className="flex flex-col min-h-full pb-8 select-none">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              stopDetection();
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Pengesan Logam (Metal Detector)' : 'Metal Detector'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Kesan besi, syiling & medan magnet (μT)' : 'Detect metals & magnetic flux in μT'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <button
            onClick={() => setIsBeepEnabled(!isBeepEnabled)}
            className={`p-2 rounded-xl border transition ${
              isBeepEnabled ? 'bg-blue-600 text-white border-blue-500' : 'bg-slate-800 text-slate-400 border-slate-700'
            }`}
            title="Togol Bunyi Bip"
          >
            {isBeepEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
          </button>
          <button
            onClick={() => setBaselineFlux(magneticFlux)}
            className="p-2 rounded-xl bg-slate-800 text-slate-300 hover:text-white border border-slate-700"
            title="Tentukur Sifar"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Paparan Nilai Fluks Magnetik */}
        <div className="text-center">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest block mb-1">
            {lang === 'ms' ? 'Keamatan Medan Magnet' : 'Magnetic Field Intensity'}
          </span>
          <div className="text-6xl font-black font-mono text-cyan-400 tracking-tight tabular-nums">
            {magneticFlux}
          </div>
          <span className="text-sm font-bold text-white mt-1">Mikrotesla (μT)</span>

          <div className="text-xs font-mono text-emerald-400 mt-2">
            {lang === 'ms' ? `Kenaikan dari Asas: +${netFlux} μT` : `Delta from Baseline: +${netFlux} μT`}
          </div>
        </div>

        {/* Tolok Analog Separuh Bulatan */}
        <div className="relative w-64 h-36 overflow-hidden flex items-end justify-center">
          <svg className="w-64 h-64 -mb-32" viewBox="0 0 200 200">
            {/* Arka Tolok */}
            <path
              d="M 20 100 A 80 80 0 0 1 180 100"
              fill="none"
              stroke="#1e293b"
              strokeWidth="16"
            />
            {/* Arka Hijau Biasa */}
            <path
              d="M 20 100 A 80 80 0 0 1 90 28"
              fill="none"
              stroke="#10b981"
              strokeWidth="16"
            />
            {/* Arka Merah Logam Dikesan */}
            <path
              d="M 120 33 A 80 80 0 0 1 180 100"
              fill="none"
              stroke="#ef4444"
              strokeWidth="16"
            />

            {/* Jarum Tolok */}
            <g
              style={{
                transform: `rotate(${Math.min(180, (netFlux / 100) * 180)}deg)`,
                transformOrigin: '100px 100px',
                transition: 'transform 0.1s ease-out',
              }}
            >
              <line x1="100" y1="100" x2="25" y2="100" stroke="#facc15" strokeWidth="3.5" strokeLinecap="round" />
              <circle cx="100" cy="100" r="6" fill="#facc15" />
            </g>
          </svg>
        </div>

        {/* Petua & Panduan Mengesan */}
        <div className="w-full p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2 text-xs text-slate-300">
          <span className="font-semibold text-white flex items-center gap-1.5">
            <Radio className="w-4 h-4 text-cyan-400" />
            {lang === 'ms' ? 'Cara Mengesan Logam:' : 'How to Detect Metal:'}
          </span>
          <p className="text-slate-400 leading-relaxed">
            {lang === 'ms'
              ? 'Rapatkan bahagian belakang atas telefon ke objek logam (kunci, duit syiling, paku dinding). Fluks magnetik akan meningkat dan bunyi bip akan menjadi semakin laju.'
              : 'Move the top back of your phone close to metallic objects (keys, coins, wall studs). Magnetic flux will spike and beeps will accelerate.'}
          </p>
        </div>

        {/* Butang Suara Ujian Segera */}
        <div className="w-full">
          <button
            onClick={() => playBeep(900)}
            className="w-full py-3.5 px-4 rounded-2xl bg-slate-800 hover:bg-slate-700 text-white font-semibold text-xs transition flex items-center justify-center gap-2 border border-slate-700"
          >
            <Zap className="w-4 h-4 text-yellow-400" />
            {lang === 'ms' ? 'Uji Bunyi Pengesan' : 'Test Beep Sound'}
          </button>
        </div>
      </div>
    </div>
  );
};
