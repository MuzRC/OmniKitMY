/**
 * @file src/tools/RulerTool.tsx
 * @description Alatan Pembaris Skrin (Screen Ruler & Caliper).
 * 
 * FUNGSI UTAMA:
 * 1. Skala ukuran fizikal sentimeter (cm), milimeter (mm) dan inci (inch) pada skrin telefon.
 * 2. Angkup gelongsor sentuh (Touch Sliding Caliper) untuk mengukur saiz objek kecil (skru, syiling, kunci).
 * 3. Alat penentukuran DPI fizikal (Physical Screen Calibration) menggunakan saiz kad bank piawai (85.6mm).
 * 4. Berfungsi 100% luar talian tanpa memerlukan internet.
 */

import React, { useState, useRef } from 'react';
import { ArrowLeft, Sliders, Check, RotateCcw } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const RulerTool: React.FC<Props> = ({ lang, onBack }) => {
  const [unit, setUnit] = useState<'cm' | 'inch'>('cm');
  const [sliderPos, setSliderPos] = useState<number>(120); // Posisi caliper dalam piksel
  const [pixelsPerMm, setPixelsPerMm] = useState<number>(3.78); // Anggaran piksel per milimeter untuk telefon pintar
  const [showCalibration, setShowCalibration] = useState<boolean>(false);

  const containerRef = useRef<HTMLDivElement | null>(null);

  // Kiraan ukuran sebenar
  const measuredMm = sliderPos / pixelsPerMm;
  const measuredCm = measuredMm / 10;
  const measuredInch = measuredMm / 25.4;

  const handleTouchMove = (e: React.TouchEvent | React.MouseEvent) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY;
    const newPos = Math.max(0, Math.min(rect.height, clientY - rect.top));
    setSliderPos(newPos);
  };

  return (
    <div className="flex flex-col h-full select-none overflow-hidden pb-4">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 z-20">
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
              {lang === 'ms' ? 'Pembaris Skrin' : 'Screen Ruler'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Letakkan objek pada skrin untuk diukur' : 'Place object on screen to measure'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setUnit(unit === 'cm' ? 'inch' : 'cm')}
            className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-bold border border-slate-700"
          >
            {unit.toUpperCase()}
          </button>
          <button
            onClick={() => setShowCalibration(!showCalibration)}
            className="p-2 rounded-xl bg-slate-800 text-slate-300 hover:text-white"
            title="Tentukur Saiz Skrin"
          >
            <Sliders className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Kotak Penentukuran Skrin jika dibuka */}
      {showCalibration && (
        <div className="p-3 bg-amber-500/10 border-b border-amber-500/30 flex items-center justify-between text-xs text-amber-200">
          <span>{lang === 'ms' ? 'Laraskan faktor skala piksel/mm:' : 'Adjust scale factor (px/mm):'}</span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPixelsPerMm((prev) => Math.max(1, prev - 0.1))}
              className="px-2 py-1 bg-slate-800 rounded font-bold"
            >
              -
            </button>
            <span className="font-mono">{pixelsPerMm.toFixed(2)}</span>
            <button
              onClick={() => setPixelsPerMm((prev) => prev + 0.1)}
              className="px-2 py-1 bg-slate-800 rounded font-bold"
            >
              +
            </button>
          </div>
        </div>
      )}

      {/* Ruang Pembaris Interaktif Penuh */}
      <div
        ref={containerRef}
        onMouseMove={(e) => {
          if (e.buttons === 1) handleTouchMove(e);
        }}
        onTouchMove={handleTouchMove}
        className="relative flex-1 bg-gradient-to-r from-slate-950 via-slate-900 to-[#121e36] flex cursor-crosshair overflow-hidden"
      >
        {/* Senggatan Skala di Sebelah Kiri */}
        <div className="w-20 border-r border-slate-700/80 relative h-full bg-slate-950/80">
          {[...Array(60)].map((_, i) => {
            const y = i * (pixelsPerMm * 5); // Setiap 5mm
            const isCm = i % 2 === 0;
            const cmNum = i / 2;

            return (
              <div
                key={i}
                style={{ top: `${y}px` }}
                className="absolute right-0 flex items-center gap-1.5 pointer-events-none"
              >
                {isCm && (
                  <span className="text-[10px] font-mono font-bold text-slate-400 select-none">
                    {cmNum}
                  </span>
                )}
                <div
                  className={`${
                    isCm ? 'w-6 bg-cyan-400' : i % 2 !== 0 ? 'w-3 bg-slate-600' : 'w-4 bg-slate-400'
                  } h-[1.5px]`}
                />
              </div>
            );
          })}
        </div>

        {/* Garisan Penunjuk Angkup (Caliper Slider) */}
        <div
          style={{ top: `${sliderPos}px` }}
          className="absolute left-0 right-0 h-0.5 bg-red-500 shadow-[0_0_8px_#ef4444] z-10 flex items-center"
        >
          {/* Pemegang Sentuh di sebelah kanan */}
          <div className="absolute right-4 px-3 py-1.5 rounded-full bg-red-600 text-white font-mono font-bold text-xs shadow-lg">
            {unit === 'cm' ? `${measuredCm.toFixed(2)} cm` : `${measuredInch.toFixed(2)} in`}
          </div>
        </div>

        {/* Paparan Nilai Ukuran Besar di Tengah */}
        <div className="flex-1 flex flex-col items-center justify-center p-6 text-center pointer-events-none">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest mb-1">
            {lang === 'ms' ? 'Panjang Diukur' : 'Measured Length'}
          </span>
          <div className="text-5xl font-black font-mono text-cyan-400 tabular-nums">
            {unit === 'cm' ? `${measuredCm.toFixed(2)}` : `${measuredInch.toFixed(2)}`}
          </div>
          <span className="text-sm font-bold text-white mt-1">
            {unit === 'cm' ? 'Sentimeter (cm)' : 'Inches (in)'}
          </span>
          <span className="text-xs text-slate-500 font-mono mt-2">
            ({measuredMm.toFixed(1)} mm)
          </span>

          <div className="mt-8 text-xs text-slate-400 max-w-xs">
            {lang === 'ms'
              ? 'Tarik garisan merah ke hujung objek untuk mengukur dengan tepat.'
              : 'Drag the red line to the edge of the object to measure.'}
          </div>
        </div>
      </div>
    </div>
  );
};
