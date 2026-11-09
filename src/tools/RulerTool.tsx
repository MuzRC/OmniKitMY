/**
 * @file src/tools/RulerTool.tsx
 * @description Alatan Pembaris Skrin Responsif Penuh (Adaptive Screen Ruler & Caliper).
 * 
 * FUNGSI UTAMA:
 * 1. Skala ukuran automatik penuh 100% ketinggian skrin tanpa ruang kosong.
 * 2. Senggat skala presisi tinggi: 1mm halus, 5mm sederhana, dan 10mm (1cm) berangka.
 * 3. Sokongan dwi-unit (CM & INCH) dengan perubahan senggat fizikal sebenar.
 * 4. Angkup gelongsor sentuh (Touch Sliding Caliper) dengan gerak balas sentuh segera.
 * 5. Kotak penentukuran bersaiz Kad Bank/MyKad (85.6 mm) yang disimpan secara kekal.
 */

import React, { useState, useRef, useEffect, useMemo } from 'react';
import { ArrowLeft, Sliders, RotateCcw, CreditCard } from 'lucide-react';
import { getStoredSetting, saveStoredSetting } from '../services/storage';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

const STORAGE_CALIB_KEY = 'omnikit_ruler_px_per_mm';
const DEFAULT_PX_PER_MM = 3.78; // Anggaran piawai skrin telefon pintar moden (~96-100 CSS DPI)
const CARD_STANDARD_MM = 85.6; // Saiz piawai lebar Kad Pengenalan / Kad ATM (ISO/IEC 7810 ID-1)

export const RulerTool: React.FC<Props> = ({ lang, onBack }) => {
  const [unit, setUnit] = useState<'cm' | 'inch'>('cm');
  const [sliderPos, setSliderPos] = useState<number>(140);
  const [showCalibration, setShowCalibration] = useState<boolean>(false);
  const [containerHeight, setContainerHeight] = useState<number>(0);

  // Ambil tetapan tentukur yang disimpan (jika ada)
  const [pixelsPerMm, setPixelsPerMm] = useState<number>(() => {
    return getStoredSetting<number>(STORAGE_CALIB_KEY, DEFAULT_PX_PER_MM);
  });

  const containerRef = useRef<HTMLDivElement | null>(null);

  // Kemas kini ketinggian skrin dinamik bila dipasang atau saiz tingkap berubah
  useEffect(() => {
    const updateSize = () => {
      if (containerRef.current) {
        setContainerHeight(containerRef.current.clientHeight);
      }
    };

    updateSize();
    window.addEventListener('resize', updateSize);
    return () => window.removeEventListener('resize', updateSize);
  }, [showCalibration]);

  // Simpan nilai penentukuran secara automatik
  const handleUpdatePixelsPerMm = (newValue: number) => {
    const clamped = Math.max(1.5, Math.min(8.0, Math.round(newValue * 100) / 100));
    setPixelsPerMm(clamped);
    saveStoredSetting(STORAGE_CALIB_KEY, clamped);
  };

  const handleResetCalibration = () => {
    setPixelsPerMm(DEFAULT_PX_PER_MM);
    saveStoredSetting(STORAGE_CALIB_KEY, DEFAULT_PX_PER_MM);
  };

  // Kiraan ukuran fizikal
  const measuredMm = sliderPos / pixelsPerMm;
  const measuredCm = measuredMm / 10;
  const measuredInch = measuredMm / 25.4;

  // Tangkap pergerakan jari / tetikus serta-merta
  const handleTouchMove = (e: React.TouchEvent | React.MouseEvent) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY;
    const newPos = Math.max(0, Math.min(rect.height, clientY - rect.top));
    setSliderPos(newPos);
  };

  // Jana senggat pembaris penuh secara dinamik mengikut ketinggian skrin sebenar
  const ticks = useMemo(() => {
    if (!containerHeight || containerHeight <= 0) return [];
    const list: Array<{ id: number; y: number; type: 'major' | 'medium' | 'minor' | 'quarter'; label: number | null }> = [];

    if (unit === 'cm') {
      // Skala Metrik (Lengkap setiap 1 mm)
      const totalMm = Math.ceil(containerHeight / pixelsPerMm);
      for (let mm = 0; mm <= totalMm; mm++) {
        const y = mm * pixelsPerMm;
        if (y > containerHeight + 2) break;

        const isCm = mm % 10 === 0;
        const isHalfCm = mm % 5 === 0 && !isCm;

        list.push({
          id: mm,
          y,
          type: isCm ? 'major' : isHalfCm ? 'medium' : 'minor',
          label: isCm ? mm / 10 : null,
        });
      }
    } else {
      // Skala Inci (Lengkap setiap 1/16 inci)
      const pxPerInch = pixelsPerMm * 25.4;
      const totalSixteenths = Math.ceil(containerHeight / (pxPerInch / 16));
      for (let s = 0; s <= totalSixteenths; s++) {
        const y = (s / 16) * pxPerInch;
        if (y > containerHeight + 2) break;

        const isInch = s % 16 === 0;
        const isHalf = s % 8 === 0 && !isInch;
        const isQuarter = s % 4 === 0 && !isInch && !isHalf;

        list.push({
          id: s,
          y,
          type: isInch ? 'major' : isHalf ? 'medium' : isQuarter ? 'quarter' : 'minor',
          label: isInch ? s / 16 : null,
        });
      }
    }

    return list;
  }, [containerHeight, pixelsPerMm, unit]);

  return (
    <div className="flex flex-col h-full select-none overflow-hidden pb-2">
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
          {/* Suis Unit CM / INCH */}
          <button
            onClick={() => setUnit(unit === 'cm' ? 'inch' : 'cm')}
            className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-cyan-400 text-xs font-mono font-bold border border-slate-700 transition"
          >
            {unit.toUpperCase()}
          </button>

          {/* Butang Panel Penentukuran */}
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

      {/* Kotak Panduan Penentukuran Menggunakan Kad ATM / MyKad */}
      {showCalibration && (
        <div className="p-3.5 bg-slate-900 border-b border-slate-700/80 flex flex-col gap-3 shrink-0 text-xs animate-in fade-in">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-amber-300 font-semibold">
              <CreditCard className="w-4 h-4" />
              <span>{lang === 'ms' ? 'Panduan Tentukur Kad Bank (85.6 mm)' : 'Card Calibration (85.6 mm)'}</span>
            </div>

            <button
              onClick={handleResetCalibration}
              className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-white"
            >
              <RotateCcw className="w-3 h-3" />
              <span>{lang === 'ms' ? 'Set Semula' : 'Reset'}</span>
            </button>
          </div>

          <p className="text-[11px] text-slate-400 leading-relaxed">
            {lang === 'ms'
              ? 'Terapkan sisi panjang Kad Pengenalan atau Kad Bank pada skrin. Laraskan butang + / - di bawah sehingga garisan merah tepat menyentuh bucu kad.'
              : 'Align a standard bank card along the screen. Adjust + / - until the red line matches the card edge.'}
          </p>

          <div className="flex items-center justify-between bg-slate-950 p-2 rounded-xl border border-slate-800">
            <span className="text-slate-400 font-mono text-[11px]">
              Skala: <strong className="text-white">{pixelsPerMm.toFixed(2)}</strong> px/mm
            </span>

            <div className="flex items-center gap-1.5">
              <button
                onClick={() => handleUpdatePixelsPerMm(pixelsPerMm - 0.05)}
                className="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 text-white font-mono font-bold text-sm"
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
                className="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 text-white font-mono font-bold text-sm"
              >
                +
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Ruang Pembaris Interaktif Penuh (Mengisi Baki Skrin) */}
      <div
        ref={containerRef}
        onMouseDown={handleTouchMove}
        onMouseMove={(e) => {
          if (e.buttons === 1) handleTouchMove(e);
        }}
        onTouchStart={handleTouchMove}
        onTouchMove={handleTouchMove}
        className="relative flex-1 min-h-0 bg-gradient-to-r from-slate-950 via-slate-900 to-[#121e36] flex cursor-crosshair overflow-hidden touch-none"
      >
        {/* Lajur Senggatan Pembaris di Sebelah Kiri */}
        <div className="w-24 border-r border-slate-700/80 relative h-full bg-slate-950/90 shrink-0">
          {ticks.map((tick) => (
            <div
              key={tick.id}
              style={{ top: `${tick.y}px` }}
              className="absolute right-0 flex items-center justify-end pointer-events-none pr-0"
            >
              {/* Nombor Senggat */}
              {tick.label !== null && (
                <span className="text-[11px] font-mono font-bold text-cyan-400 select-none mr-2">
                  {tick.label}
                </span>
              )}

              {/* Garisan Tanda Senggat Mengikut Jenis */}
              <div
                className={`transition-colors ${
                  tick.type === 'major'
                    ? 'w-7 bg-cyan-400 h-[1.5px]'
                    : tick.type === 'medium'
                    ? 'w-4 bg-slate-300 h-[1.2px]'
                    : tick.type === 'quarter'
                    ? 'w-3 bg-slate-400 h-[1px]'
                    : 'w-2 bg-slate-600/80 h-[0.8px]'
                }`}
              />
            </div>
          ))}
        </div>

        {/* Garisan Penunjuk Angkup Merah (Caliper Slider) */}
        <div
          style={{ top: `${sliderPos}px` }}
          className="absolute left-0 right-0 h-[2px] bg-red-500 shadow-[0_0_10px_#ef4444] z-10 flex items-center pointer-events-none"
        >
          {/* Lencana Bacaan Ukuran Terapung di Sebelah Kanan */}
          <div className="absolute right-3 px-3 py-1 rounded-full bg-red-600 text-white font-mono font-bold text-xs shadow-xl border border-red-400/50">
            {unit === 'cm' ? `${measuredCm.toFixed(2)} cm` : `${measuredInch.toFixed(2)} in`}
          </div>
        </div>

        {/* Paparan Nilai Ukuran Besar di Tengah Skrin */}
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

          <div className="mt-6 text-xs text-slate-400 max-w-xs leading-relaxed">
            {lang === 'ms'
              ? 'Tarik atau sentuh garisan merah ke hujung objek untuk mengukur dengan tepat.'
              : 'Drag or tap the red line to the edge of your object.'}
          </div>
        </div>
      </div>
    </div>
  );
};
