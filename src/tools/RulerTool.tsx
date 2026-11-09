/**
 * @file src/tools/RulerTool.tsx
 * @description Alatan Pembaris Skrin Skrin Penuh (Full Screen Adaptive Ruler).
 * 
 * FUNGSI UTAMA:
 * 1. Mengisi 100% skrin telefon secara mutlak (fixed inset-0) tanpa isu kontena runtuh.
 * 2. Senggat penuh sehingga 25 cm (250 mm) dengan senggat halus 1mm, 5mm dan 10mm (1cm).
 * 3. Tukaran mod dwi-unit (CM & INCH) dengan penukaran senggat fizikal sebenar.
 * 4. Kawalan angkup merah (caliper) lancar dengan sekatan gerak isyarat skrin (touch-none).
 * 5. Penentukuran menggunakan Kad Pengenalan / Kad Bank (85.6 mm) yang disimpan dalam memori.
 */

import React, { useState, useRef, useEffect } from 'react';
import { ArrowLeft, Sliders, RotateCcw, CreditCard } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

const STORAGE_CALIB_KEY = 'omnikit_ruler_px_per_mm';
const DEFAULT_PX_PER_MM = 3.78; // Anggaran piawai skrin telefon pintar moden (~96-100 CSS DPI)
const CARD_STANDARD_MM = 85.6; // Lebar standard Kad Bank / MyKad piawai

export const RulerTool: React.FC<Props> = ({ lang, onBack }) => {
  const [unit, setUnit] = useState<'cm' | 'inch'>('cm');
  const [sliderPos, setSliderPos] = useState<number>(150);
  const [showCalibration, setShowCalibration] = useState<boolean>(false);

  // Muat nilai penentukuran dari storan peranti
  const [pixelsPerMm, setPixelsPerMm] = useState<number>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_CALIB_KEY);
      return saved ? parseFloat(saved) : DEFAULT_PX_PER_MM;
    } catch {
      return DEFAULT_PX_PER_MM;
    }
  });

  const containerRef = useRef<HTMLDivElement | null>(null);

  // Simpan nilai penentukuran
  const handleUpdatePixelsPerMm = (val: number) => {
    const clamped = Math.max(1.5, Math.min(8.0, Math.round(val * 100) / 100));
    setPixelsPerMm(clamped);
    try {
      localStorage.setItem(STORAGE_CALIB_KEY, clamped.toString());
    } catch (e) {
      console.error(e);
    }
  };

  const handleResetCalibration = () => {
    setPixelsPerMm(DEFAULT_PX_PER_MM);
    try {
      localStorage.setItem(STORAGE_CALIB_KEY, DEFAULT_PX_PER_MM.toString());
    } catch (e) {
      console.error(e);
    }
  };

  // Kiraan ukuran sebenar
  const measuredMm = sliderPos / pixelsPerMm;
  const measuredCm = measuredMm / 10;
  const measuredInch = measuredMm / 25.4;

  // Tangkap pergerakan jari / tetikus
  const handleDrag = (clientY: number) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const newPos = Math.max(0, Math.min(rect.height, clientY - rect.top));
    setSliderPos(newPos);
  };

  return (
    <div className="fixed inset-0 z-30 flex flex-col bg-[#090e1c] select-none overflow-hidden touch-none">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 z-20 shrink-0">
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
            className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-cyan-400 text-xs font-mono font-bold border border-slate-700 transition"
          >
            {unit.toUpperCase()}
          </button>
          <button
            onClick={() => setShowCalibration(!showCalibration)}
            className={`p-2 rounded-xl border transition ${
              showCalibration
                ? 'bg-blue-600 text-white border-blue-500 shadow-md'
                : 'bg-slate-800 text-slate-300 hover:text-white border-slate-700'
            }`}
            title="Tentukur Skala Skrin"
          >
            <Sliders className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Panel Penentukuran (Kad Bank / MyKad 85.6 mm) */}
      {showCalibration && (
        <div className="p-3.5 bg-slate-900 border-b border-slate-700/80 flex flex-col gap-2.5 shrink-0 text-xs animate-in fade-in z-20">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-amber-300 font-semibold">
              <CreditCard className="w-4 h-4" />
              <span>{lang === 'ms' ? 'Penentukuran Kad Bank (85.6 mm)' : 'Card Calibration (85.6 mm)'}</span>
            </div>
            <button
              onClick={handleResetCalibration}
              className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-white"
            >
              <RotateCcw className="w-3 h-3" />
              <span>{lang === 'ms' ? 'Set Semula' : 'Reset'}</span>
            </button>
          </div>

          <p className="text-[11px] text-slate-400">
            {lang === 'ms'
              ? 'Terapkan sisi kad bank pada skrin. Tekan "Set 85.6mm", kemudian laraskan butang + / - sehingga garisan merah tepat pada hujung kad.'
              : 'Place a bank card on screen. Tap "Set 85.6mm", then tune + / - until the red line matches the card edge.'}
          </p>

          <div className="flex items-center justify-between bg-slate-950 p-2 rounded-xl border border-slate-800">
            <span className="text-slate-400 font-mono text-[11px]">
              Skala: <strong className="text-white">{pixelsPerMm.toFixed(2)}</strong> px/mm
            </span>
            <div className="flex items-center gap-1.5">
              <button
                onClick={() => handleUpdatePixelsPerMm(pixelsPerMm - 0.05)}
                className="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 text-white font-mono font-bold"
              >
                -
              </button>
              <button
                onClick={() => setSliderPos(CARD_STANDARD_MM * pixelsPerMm)}
                className="px-2.5 py-1.5 rounded-lg bg-blue-600/30 text-blue-300 border border-blue-500/40 text-[10px] font-bold"
              >
                Set 85.6mm
              </button>
              <button
                onClick={() => handleUpdatePixelsPerMm(pixelsPerMm + 0.05)}
                className="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 text-white font-mono font-bold"
              >
                +
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Kawasan Pembaris Skrin Penuh */}
      <div
        ref={containerRef}
        onMouseDown={(e) => handleDrag(e.clientY)}
        onMouseMove={(e) => {
          if (e.buttons === 1) handleDrag(e.clientY);
        }}
        onTouchStart={(e) => handleDrag(e.touches[0].clientY)}
        onTouchMove={(e) => handleDrag(e.touches[0].clientY)}
        className="relative flex-1 min-h-0 bg-gradient-to-r from-slate-950 via-slate-900 to-[#121e36] flex cursor-crosshair overflow-hidden touch-none"
      >
        {/* Lajur Senggat Pembaris di Sebelah Kiri */}
        <div className="w-24 border-r border-slate-700/80 relative h-full bg-slate-950/90 shrink-0">
          {unit === 'cm'
            ? /* Skala Sentimeter & Milimeter (0 hingga 25 cm) */
              Array.from({ length: 251 }, (_, mm) => {
                const y = mm * pixelsPerMm;
                const isCm = mm % 10 === 0;
                const isHalf = mm % 5 === 0 && !isCm;

                return (
                  <div
                    key={mm}
                    style={{ top: `${y}px` }}
                    className="absolute right-0 flex items-center justify-end pointer-events-none"
                  >
                    {isCm && (
                      <span className="text-[10px] font-mono font-bold text-cyan-400 select-none mr-2">
                        {mm / 10}
                      </span>
                    )}
                    <div
                      className={`${
                        isCm
                          ? 'w-7 bg-cyan-400 h-[1.5px]'
                          : isHalf
                          ? 'w-4 bg-slate-300 h-[1.2px]'
                          : 'w-2 bg-slate-600/80 h-[0.8px]'
                      }`}
                    />
                  </div>
                );
              })
            : /* Skala Inci (0 hingga 10 inci, pecahan 1/16) */
              Array.from({ length: 161 }, (_, s) => {
                const y = (s / 16) * (pixelsPerMm * 25.4);
                const isInch = s % 16 === 0;
                const isHalf = s % 8 === 0 && !isInch;
                const isQuarter = s % 4 === 0 && !isInch && !isHalf;

                return (
                  <div
                    key={s}
                    style={{ top: `${y}px` }}
                    className="absolute right-0 flex items-center justify-end pointer-events-none"
                  >
                    {isInch && (
                      <span className="text-[10px] font-mono font-bold text-cyan-400 select-none mr-2">
                        {s / 16}
                      </span>
                    )}
                    <div
                      className={`${
                        isInch
                          ? 'w-7 bg-cyan-400 h-[1.5px]'
                          : isHalf
                          ? 'w-5 bg-slate-300 h-[1.2px]'
                          : isQuarter
                          ? 'w-3.5 bg-slate-400 h-[1px]'
                          : 'w-2 bg-slate-600/80 h-[0.8px]'
                      }`}
                    />
                  </div>
                );
              })}
        </div>

        {/* Garisan Penunjuk Angkup Merah (Caliper Line) */}
        <div
          style={{ top: `${sliderPos}px` }}
          className="absolute left-0 right-0 h-[2px] bg-red-500 shadow-[0_0_10px_#ef4444] z-10 flex items-center pointer-events-none"
        >
          <div className="absolute right-3 px-3 py-1 rounded-full bg-red-600 text-white font-mono font-bold text-xs shadow-xl border border-red-400/50">
            {unit === 'cm' ? `${measuredCm.toFixed(2)} cm` : `${measuredInch.toFixed(2)} in`}
          </div>
        </div>

        {/* Paparan Bacaan Digital di Tengah Skrin */}
        <div className="flex-1 flex flex-col items-center justify-center p-6 text-center pointer-events-none">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest mb-1">
            {lang === 'ms' ? 'Panjang Diukur' : 'Measured Length'}
          </span>

          <div className="text-5xl sm:text-6xl font-black font-mono text-cyan-400 tabular-nums">
            {unit === 'cm' ? `${measuredCm.toFixed(2)}` : `${measuredInch.toFixed(2)}`}
          </div>

          <span className="text-sm font-bold text-white mt-1">
            {unit === 'cm' ? 'Sentimeter (cm)' : 'Inches (in)'}
          </span>

          <span className="text-xs text-slate-400 font-mono mt-1">
            ({measuredMm.toFixed(1)} mm)
          </span>

          <div className="mt-8 text-xs text-slate-400 max-w-xs leading-relaxed">
            {lang === 'ms'
              ? 'Sentuh atau leret jari pada skrin untuk melaraskan garisan merah ke hujung objek.'
              : 'Touch or drag anywhere on screen to slide the red line.'}
          </div>
        </div>
      </div>
    </div>
  );
};
