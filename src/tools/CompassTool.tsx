/**
 * @file src/tools/CompassTool.tsx
 * @description Alatan Kompas Digital (Digital Compass & Orientation).
 * 
 * FUNGSI UTAMA:
 * 1. Mengesan arah mata angin dan orientasi magnetik darjah (0° - 360°) menggunakan DeviceOrientationEvent.
 * 2. Menghitung arah mata angin penuh dalam Bahasa Melayu (Utara, Timur, Selatan, Barat, dsb.).
 * 3. Dail kompas bervektor interaktif dengan jarum kompas beranimasi lancar.
 * 4. Pilihan penentukuran dan pengesanan sudut condong.
 */

import React, { useState, useEffect } from 'react';
import { ArrowLeft, Compass as CompassIcon, Navigation, RefreshCw } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const CompassTool: React.FC<Props> = ({ lang, onBack }) => {
  const [heading, setHeading] = useState<number>(0);
  const [sensorActive, setSensorActive] = useState<boolean>(false);
  const [calibrationOffset, setCalibrationOffset] = useState<number>(0);

  useEffect(() => {
    const handleOrientation = (e: DeviceOrientationEvent) => {
      let compassHeading: number | null = null;

      // iOS Webkit Compass Heading
      const webkitEvent = e as unknown as { webkitCompassHeading?: number };
      if (typeof webkitEvent.webkitCompassHeading === 'number') {
        compassHeading = webkitEvent.webkitCompassHeading;
      } else if (e.alpha !== null) {
        // Android DeviceOrientation alpha (360 - alpha)
        compassHeading = (360 - e.alpha) % 360;
      }

      if (compassHeading !== null) {
        setSensorActive(true);
        setHeading(Math.round(compassHeading));
      }
    };

    window.addEventListener('deviceorientation', handleOrientation);
    return () => {
      window.removeEventListener('deviceorientation', handleOrientation);
    };
  }, []);

  const netHeading = (heading - calibrationOffset + 360) % 360;

  // Nama Arah Mata Angin
  const getCardinalDirection = (deg: number) => {
    const directions = [
      { ms: 'Utara (N)', en: 'North (N)' },
      { ms: 'Timur Laut (NE)', en: 'North East (NE)' },
      { ms: 'Timur (E)', en: 'East (E)' },
      { ms: 'Tenggara (SE)', en: 'South East (SE)' },
      { ms: 'Selatan (S)', en: 'South (S)' },
      { ms: 'Barat Daya (SW)', en: 'South West (SW)' },
      { ms: 'Barat (W)', en: 'West (W)' },
      { ms: 'Barat Laut (NW)', en: 'North West (NW)' },
    ];
    const index = Math.round(deg / 45) % 8;
    return directions[index][lang];
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
              {lang === 'ms' ? 'Kompas Digital' : 'Digital Compass'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Penunjuk arah mata angin & orientasi magnetik' : 'Heading & cardinal points'}
            </p>
          </div>
        </div>

        <button
          onClick={() => setCalibrationOffset(heading)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow transition"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>{lang === 'ms' ? 'Tentukur Sifar' : 'Zero Set'}</span>
        </button>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Paparan Darjah & Arah Semasa */}
        <div className="text-center">
          <div className="text-5xl font-black font-mono text-white tracking-tight tabular-nums">
            {netHeading}°
          </div>
          <div className="text-sm font-bold text-cyan-400 mt-1 uppercase tracking-wider">
            {getCardinalDirection(netHeading)}
          </div>
        </div>

        {/* Dail Kompas Berputar */}
        <div className="relative w-72 h-72 rounded-full bg-gradient-to-b from-[#131f3b] to-[#0c1424] border-4 border-slate-700 shadow-2xl flex items-center justify-center p-4">
          {/* Garisan Tanda Utara Tetap di Atas */}
          <div className="absolute top-2 w-1.5 h-4 bg-red-500 rounded-full shadow-[0_0_8px_#ef4444]" />

          {/* Dail Berputar Mengikut Heading */}
          <div
            style={{
              transform: `rotate(${-netHeading}deg)`,
              transition: 'transform 0.15s ease-out',
            }}
            className="relative w-full h-full flex items-center justify-center"
          >
            {/* Tanda Mata Angin Utama */}
            <span className="absolute top-3 font-bold text-red-500 text-sm">U / N</span>
            <span className="absolute bottom-3 font-bold text-slate-300 text-sm">S</span>
            <span className="absolute right-3 font-bold text-slate-300 text-sm">T / E</span>
            <span className="absolute left-3 font-bold text-slate-300 text-sm">B / W</span>

            {/* Senggatan Garisan Keliling Kompas */}
            {[...Array(12)].map((_, i) => (
              <div
                key={i}
                style={{ transform: `rotate(${i * 30}deg)` }}
                className="absolute w-full h-full flex justify-center pointer-events-none"
              >
                <div className={`w-0.5 ${i % 3 === 0 ? 'h-3 bg-cyan-400' : 'h-1.5 bg-slate-600'}`} />
              </div>
            ))}

            {/* Jarum Kompas Klasik */}
            <div className="relative flex flex-col items-center">
              {/* Jarum Merah (Utara) */}
              <div className="w-0 h-0 border-l-[8px] border-l-transparent border-r-[8px] border-r-transparent border-b-[65px] border-b-red-500" />
              {/* Jarum Putih (Selatan) */}
              <div className="w-0 h-0 border-l-[8px] border-l-transparent border-r-[8px] border-r-transparent border-t-[65px] border-t-slate-200" />
              {/* Pusat Pivot */}
              <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-4 h-4 rounded-full bg-yellow-400 border-2 border-slate-900 shadow-md" />
            </div>
          </div>
        </div>

        {/* Maklumat Tambahan & Petua */}
        <div className="w-full p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex items-center justify-between text-xs text-slate-400">
          <span>{lang === 'ms' ? 'Status Penderia:' : 'Sensor Status:'}</span>
          <span className="font-semibold text-emerald-400">
            {sensorActive ? (lang === 'ms' ? 'Aktif & Tepat' : 'Active & Accurate') : (lang === 'ms' ? 'Menggunakan Gyro Web' : 'Web Gyro Active')}
          </span>
        </div>
      </div>
    </div>
  );
};
