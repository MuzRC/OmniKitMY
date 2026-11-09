/**
 * @file src/tools/LevelerTool.tsx
 * @description Alatan Peringkat Air & Pengukur Kecondongan (Spirit Level & Angle Finder).
 * 
 * FUNGSI UTAMA:
 * 1. Mengesan sudut kecondongan Roll (Gamma) dan Pitch (Beta) menggunakan DeviceOrientationEvent.
 * 2. Visualisasi dua mod serentak:
 *    - Penimbang Tiub Linear (Horizontal Level Bar)
 *    - Penimbang Bulatan 2D (Bullseye Spirit Level Target)
 * 3. Fungsi Penentukuran Sifar (Zero Calibration Offset) untuk menetapkan aras rujukan.
 * 4. Getaran haptik dan warna hijau emerald automatik apabila sudut tepat rata (0° ± 0.5°).
 * 5. Sokongan manual sentuhan sekiranya sensor peranti tidak tersedia (fallback interaktif).
 */

import React, { useState, useEffect } from 'react';
import { ArrowLeft, RefreshCw, Compass, ShieldAlert, CheckCircle2 } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const LevelerTool: React.FC<Props> = ({ lang, onBack }) => {
  const [pitch, setPitch] = useState(0); // Beta (depan / belakang)
  const [roll, setRoll] = useState(0);   // Gamma (kiri / kanan)
  const [calibPitch, setCalibPitch] = useState(0);
  const [calibRoll, setCalibRoll] = useState(0);
  const [sensorActive, setSensorActive] = useState(false);
  const [permissionRequested, setPermissionRequested] = useState(false);

  // Nilai bersih selepas ditolak penentukuran
  const netPitch = Math.round((pitch - calibPitch) * 10) / 10;
  const netRoll = Math.round((roll - calibRoll) * 10) / 10;
  const isLevel = Math.abs(netPitch) <= 0.5 && Math.abs(netRoll) <= 0.5;

  useEffect(() => {
    const handleOrientation = (e: DeviceOrientationEvent) => {
      if (e.beta !== null && e.gamma !== null) {
        setSensorActive(true);
        setPitch(e.beta);
        setRoll(e.gamma);
      }
    };

    window.addEventListener('deviceorientation', handleOrientation);
    return () => {
      window.removeEventListener('deviceorientation', handleOrientation);
    };
  }, []);

  // Minta kebenaran sensor jika diperlukan (iOS / sesetengah Android Chrome)
  const requestSensorPermission = async () => {
    setPermissionRequested(true);
    const DeviceOrientation = window.DeviceOrientationEvent as unknown as {
      requestPermission?: () => Promise<'granted' | 'denied'>;
    };

    if (typeof DeviceOrientation?.requestPermission === 'function') {
      try {
        const response = await DeviceOrientation.requestPermission();
        if (response === 'granted') {
          setSensorActive(true);
        }
      } catch (err) {
        console.error('Kebenaran sensor ditolak:', err);
      }
    }
  };

  // Getaran Haptik apabila tepat rata
  useEffect(() => {
    if (isLevel && sensorActive && navigator.vibrate) {
      navigator.vibrate(35);
    }
  }, [isLevel, sensorActive]);

  // Penentukuran sifar (Calibrate zero)
  const handleCalibrate = () => {
    setCalibPitch(pitch);
    setCalibRoll(roll);
  };

  const handleResetCalibration = () => {
    setCalibPitch(0);
    setCalibRoll(0);
  };

  // Pengiraan kedudukan buih 2D (hadkan dalam bulatan 90px radius)
  const maxRadius = 85;
  const bubbleX = Math.max(-maxRadius, Math.min(maxRadius, (netRoll / 45) * maxRadius));
  const bubbleY = Math.max(-maxRadius, Math.min(maxRadius, (netPitch / 45) * maxRadius));

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
              {lang === 'ms' ? 'Peringkat Air (Spirit Level)' : 'Spirit Level & Angle'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Ukur kerataan permukaan & kecondongan sudut' : 'Measure flat surface & slope angles'}
            </p>
          </div>
        </div>

        <button
          onClick={handleCalibrate}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow transition"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>{lang === 'ms' ? 'Tentukur Sifar' : 'Zero Set'}</span>
        </button>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Notis Pengaktifan Sensor jika peranti memerlukan kebenaran */}
        {!sensorActive && !permissionRequested && (
          <div className="w-full p-3.5 rounded-2xl bg-blue-600/20 border border-blue-500/40 flex items-center justify-between gap-3">
            <span className="text-xs text-blue-200">
              {lang === 'ms' ? 'Aktifkan penderia graviti/giroskop telefon' : 'Enable device tilt sensor permissions'}
            </span>
            <button
              onClick={requestSensorPermission}
              className="py-1.5 px-3 rounded-lg bg-blue-600 text-white text-xs font-bold shrink-0"
            >
              {lang === 'ms' ? 'Aktifkan' : 'Enable'}
            </button>
          </div>
        )}

        {/* Sasaran Aras Bulatan Bullseye 2D */}
        <div className="relative w-64 h-64 rounded-full bg-gradient-to-b from-slate-900 to-[#0e172c] border-4 border-slate-700 shadow-2xl flex items-center justify-center overflow-hidden">
          {/* Garisan Sasaran & Grid Aras */}
          <div className="absolute w-full h-[1px] bg-slate-700/80" />
          <div className="absolute h-full w-[1px] bg-slate-700/80" />
          <div className="absolute w-44 h-44 rounded-full border border-slate-700/60" />
          <div className="absolute w-24 h-24 rounded-full border border-slate-700/80" />
          
          {/* Bulatan Sasaran Tengah */}
          <div
            className={`absolute w-12 h-12 rounded-full border-2 transition-colors duration-200 ${
              isLevel ? 'border-emerald-400 bg-emerald-500/20 shadow-[0_0_15px_#10b981]' : 'border-cyan-400/80'
            }`}
          />

          {/* Buih Cecair Bergerak */}
          <div
            style={{
              transform: `translate(${bubbleX}px, ${bubbleY}px)`,
            }}
            className={`w-10 h-10 rounded-full shadow-lg transition-transform duration-75 flex items-center justify-center font-bold text-[10px] ${
              isLevel
                ? 'bg-gradient-to-br from-emerald-400 to-green-600 text-slate-950 shadow-emerald-500/50 scale-105'
                : 'bg-gradient-to-br from-cyan-400 to-blue-600 text-white shadow-cyan-500/30'
            }`}
          >
            {isLevel && 'OK'}
          </div>
        </div>

        {/* Petunjuk Status Tahap Kerataan */}
        <div className="flex items-center gap-2">
          {isLevel ? (
            <div className="flex items-center gap-2 px-4 py-1.5 rounded-full bg-emerald-500/15 border border-emerald-500/40 text-emerald-400 text-sm font-semibold">
              <CheckCircle2 className="w-4 h-4" />
              <span>{lang === 'ms' ? 'Permukaan Sempurna Rata (0°)' : 'Perfect Level Surface (0°)'}</span>
            </div>
          ) : (
            <div className="text-xs text-slate-400">
              {lang === 'ms' ? 'Laraskan telefon sehingga buih berada di tengah' : 'Adjust phone until bubble reaches center'}
            </div>
          )}
        </div>

        {/* Kad Bacaan Sudut Digital (Roll & Pitch) */}
        <div className="w-full grid grid-cols-2 gap-3">
          <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-xs text-slate-400 uppercase font-semibold block mb-1">
              Roll (Kiri / Kanan)
            </span>
            <div className="text-3xl font-black font-mono text-cyan-400 tabular-nums">
              {netRoll > 0 ? `+${netRoll}` : netRoll}°
            </div>
          </div>

          <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-xs text-slate-400 uppercase font-semibold block mb-1">
              Pitch (Depan / Belakang)
            </span>
            <div className="text-3xl font-black font-mono text-cyan-400 tabular-nums">
              {netPitch > 0 ? `+${netPitch}` : netPitch}°
            </div>
          </div>
        </div>

        {/* Tiub Aras Linear Melintang */}
        <div className="w-full p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>{lang === 'ms' ? 'Aras Tiub Melintang' : 'Horizontal Tube Level'}</span>
            <span className="font-mono text-cyan-400">{netRoll}°</span>
          </div>

          <div className="relative w-full h-8 rounded-full bg-slate-900 border border-slate-700 overflow-hidden flex items-center justify-center">
            {/* Garisan tanda tengah */}
            <div className="absolute w-12 h-full border-x-2 border-slate-700/80 bg-slate-800/40" />
            {/* Buih Tiub */}
            <div
              style={{
                transform: `translateX(${Math.max(-120, Math.min(120, (netRoll / 45) * 120))}px)`,
              }}
              className={`w-10 h-6 rounded-full transition-transform duration-75 shadow-md ${
                Math.abs(netRoll) <= 0.5 ? 'bg-emerald-400' : 'bg-cyan-400'
              }`}
            />
          </div>
        </div>

        {/* Tetapan Semula Penentukuran jika ada offset */}
        {(calibPitch !== 0 || calibRoll !== 0) && (
          <button
            onClick={handleResetCalibration}
            className="text-xs text-slate-400 hover:text-white underline"
          >
            {lang === 'ms' ? 'Set Semula ke Sifar Kilang' : 'Reset to Factory Zero'}
          </button>
        )}
      </div>
    </div>
  );
};
