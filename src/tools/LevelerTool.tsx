/**
 * @file src/tools/LevelerTool.tsx
 * @description Alatan Peringkat Air Pintar 3-Dalam-1 (Spirit Level & Angle Finder).
 * 
 * FUNGSI UTAMA:
 * 1. Pengesanan Automatik 3 Mod:
 *    - Mod Baring (Flat Surface / Meja)
 *    - Mod Melintang (Landscape Edge / Ukur Rak guna Sisi Panjang)
 *    - Mod Menegak (Portrait Plumb / Ukur Tiang & Dinding Tegak)
 * 2. Visualisasi Bullseye 2D & Tiub Aras Cecair yang menyesuaikan paksi graviti.
 * 3. Penentukuran Sifar mengikut mod aktif (Zero Calibration Offset).
 * 4. Getaran haptik & visual hijau emerald bila sudut tepat rata (toleransi ±0.5°).
 */

import React, { useState, useEffect, useMemo } from 'react';
import { ArrowLeft, RefreshCw, CheckCircle2 } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

type LevelMode = 'flat' | 'portrait' | 'landscape';

export const LevelerTool: React.FC<Props> = ({ lang, onBack }) => {
  const [pitch, setPitch] = useState(0); // Beta (depan / belakang)
  const [roll, setRoll] = useState(0);   // Gamma (kiri / kanan)
  const [calibPitch, setCalibPitch] = useState(0);
  const [calibRoll, setCalibRoll] = useState(0);
  const [sensorActive, setSensorActive] = useState(false);
  const [permissionRequested, setPermissionRequested] = useState(false);

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

  // Minta kebenaran sensor jika diperlukan (iOS / sesetengah pelayar Android)
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

  // Nilai bersih sudut mentah selepas ditolak penentukuran
  const rawPitch = pitch - calibPitch;
  const rawRoll = roll - calibRoll;

  // 1. Kenal pasti orientasi telefon secara automatik
  const mode: LevelMode = useMemo(() => {
    const absRoll = Math.abs(rawRoll);
    const absPitch = Math.abs(rawPitch);

    if (absRoll >= 45) return 'landscape';
    if (absPitch >= 45) return 'portrait';
    return 'flat';
  }, [rawRoll, rawPitch]);

  // 2. Kira sudut aktif & status aras mengikut orientasi
  const { primaryAngle, secondaryAngle, isLevel, bubbleX, bubbleY, maxRadius } = useMemo(() => {
    const radius = 85;
    let pAngle = 0;
    let sAngle = 0;
    let level = false;
    let bx = 0;
    let by = 0;

    if (mode === 'landscape') {
      // Mod Melintang: Ukur kelurusan rak guna sisi panjang telefon
      // Sudut utama ialah Pitch (kelurusan tepi). Sudut kedua ialah kecondongan muka telefon.
      pAngle = Math.round(rawPitch * 10) / 10;
      sAngle = Math.round((Math.abs(rawRoll) - 90) * 10) / 10;
      level = Math.abs(pAngle) <= 0.5;

      bx = Math.max(-radius, Math.min(radius, (pAngle / 20) * radius));
      by = Math.max(-radius, Math.min(radius, (sAngle / 20) * radius));
    } else if (mode === 'portrait') {
      // Mod Menegak: Ukur tiang atau dinding tegak (Plumb)
      // Sudut utama ialah Roll (kiri/kanan). Sudut kedua ialah condong dinding (Pitch menghampiri ±90°).
      pAngle = Math.round(rawRoll * 10) / 10;
      const targetPitch = rawPitch >= 0 ? 90 : -90;
      sAngle = Math.round((rawPitch - targetPitch) * 10) / 10;
      level = Math.abs(pAngle) <= 0.5;

      bx = Math.max(-radius, Math.min(radius, (pAngle / 20) * radius));
      by = Math.max(-radius, Math.min(radius, (sAngle / 20) * radius));
    } else {
      // Mod Baring: Telefon diletak rata di atas meja (Bullseye)
      pAngle = Math.round(rawRoll * 10) / 10;
      sAngle = Math.round(rawPitch * 10) / 10;
      level = Math.abs(pAngle) <= 0.5 && Math.abs(sAngle) <= 0.5;

      bx = Math.max(-radius, Math.min(radius, (pAngle / 45) * radius));
      by = Math.max(-radius, Math.min(radius, (sAngle / 45) * radius));
    }

    return {
      primaryAngle: pAngle,
      secondaryAngle: sAngle,
      isLevel: level,
      bubbleX: bx,
      bubbleY: by,
      maxRadius: radius,
    };
  }, [mode, rawPitch, rawRoll]);

  // Getaran Haptik apabila tepat rata
  useEffect(() => {
    if (isLevel && sensorActive && navigator.vibrate) {
      navigator.vibrate(35);
    }
  }, [isLevel, sensorActive]);

  // Penentukuran sifar (Calibrate zero mengikut keadaan semasa)
  const handleCalibrate = () => {
    setCalibPitch(pitch);
    setCalibRoll(roll);
  };

  const handleResetCalibration = () => {
    setCalibPitch(0);
    setCalibRoll(0);
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
              {lang === 'ms' ? 'Peringkat Air (Spirit Level)' : 'Spirit Level & Angle'}
            </h2>
            <p className="text-xs text-slate-400">
              {mode === 'landscape'
                ? lang === 'ms' ? 'Mod Melintang (Sisi Panjang)' : 'Landscape Mode (Edge)'
                : mode === 'portrait'
                ? lang === 'ms' ? 'Mod Menegak (Dinding / Tiang)' : 'Portrait Mode (Plumb)'
                : lang === 'ms' ? 'Mod Baring (Permukaan Rata)' : 'Surface Mode (Flat)'}
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

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-5">
        {/* Notis Pengaktifan Sensor jika belum aktif */}
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
              <span>
                {mode === 'portrait'
                  ? lang === 'ms' ? 'Tegak Sempurna (Plumb 0°)' : 'Perfect Plumb (0°)'
                  : lang === 'ms' ? 'Permukaan Sempurna Rata (0°)' : 'Perfect Level Surface (0°)'}
              </span>
            </div>
          ) : (
            <div className="text-xs text-slate-400">
              {lang === 'ms' ? 'Laraskan telefon sehingga buih berada di tengah' : 'Adjust phone until bubble reaches center'}
            </div>
          )}
        </div>

        {/* Kad Bacaan Sudut Digital Dinamik */}
        <div className="w-full grid grid-cols-2 gap-3">
          <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-xs text-slate-400 uppercase font-semibold block mb-1">
              {mode === 'landscape'
                ? (lang === 'ms' ? 'Aras Sisi Melintang' : 'Edge Level')
                : mode === 'portrait'
                ? (lang === 'ms' ? 'Aras Sisi Bawah' : 'Bottom Level')
                : 'Roll (Kiri / Kanan)'}
            </span>
            <div className={`text-3xl font-black font-mono tabular-nums ${isLevel ? 'text-emerald-400' : 'text-cyan-400'}`}>
              {primaryAngle > 0 ? `+${primaryAngle}` : primaryAngle}°
            </div>
          </div>

          <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-xs text-slate-400 uppercase font-semibold block mb-1">
              {mode === 'landscape'
                ? (lang === 'ms' ? 'Condong Muka' : 'Face Lean')
                : mode === 'portrait'
                ? (lang === 'ms' ? 'Condong Dinding' : 'Wall Plumb')
                : 'Pitch (Depan / Belakang)'}
            </span>
            <div className="text-3xl font-black font-mono text-cyan-400 tabular-nums">
              {secondaryAngle > 0 ? `+${secondaryAngle}` : secondaryAngle}°
            </div>
          </div>
        </div>

        {/* Tiub Aras Cecair Melintang Dinamik */}
        <div className="w-full p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>
              {mode === 'landscape'
                ? (lang === 'ms' ? 'Tiub Aras Sisi Melintang' : 'Edge Level Tube')
                : mode === 'portrait'
                ? (lang === 'ms' ? 'Tiub Aras Sisi Menegak' : 'Plumb Level Tube')
                : (lang === 'ms' ? 'Tiub Aras Melintang' : 'Horizontal Tube Level')}
            </span>
            <span className="font-mono text-cyan-400">{primaryAngle}°</span>
          </div>

          <div className="relative w-full h-8 rounded-full bg-slate-900 border border-slate-700 overflow-hidden flex items-center justify-center">
            <div className="absolute w-12 h-full border-x-2 border-slate-700/80 bg-slate-800/40" />
            <div
              style={{
                transform: `translateX(${Math.max(-120, Math.min(120, (primaryAngle / 20) * 120))}px)`,
              }}
              className={`w-10 h-6 rounded-full transition-transform duration-75 shadow-md ${
                Math.abs(primaryAngle) <= 0.5 ? 'bg-emerald-400 shadow-emerald-500/50' : 'bg-cyan-400'
              }`}
            />
          </div>
        </div>

        {/* Butang Tetapan Semula jika ada penentukuran aktif */}
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
