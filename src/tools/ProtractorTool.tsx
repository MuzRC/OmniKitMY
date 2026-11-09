/**
 * @file src/tools/ProtractorTool.tsx
 * @description Alatan Jangka Sudut Skrin & Kamera (Interactive Protractor & Angle Finder).
 * 
 * FUNGSI UTAMA:
 * 1. Dail jangka sudut interaktif 180° / 360° dengan lengan sentuh berputar bebas.
 * 2. Mod Kamera Pandangan Langsung (Camera Viewfinder Mode) untuk menyukat sudut objek fizikal di dunia nyata.
 * 3. Kiraan sudut pelengkap (Supplementary & Complementary angles) secara automatik.
 * 4. Kunci sudut (Angle Lock) untuk menyimpan bacaan semasa meletakkan telefon pada permukaan.
 */

import React, { useState, useRef, useEffect } from 'react';
import { ArrowLeft, Camera, RotateCcw, Lock, Unlock } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const ProtractorTool: React.FC<Props> = ({ lang, onBack }) => {
  const [angle, setAngle] = useState<number>(45);
  const [isLocked, setIsLocked] = useState<boolean>(false);
  const [cameraActive, setCameraActive] = useState<boolean>(false);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  // Togol Mod Kamera
  const toggleCamera = async () => {
    if (cameraActive) {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
        streamRef.current = null;
      }
      setCameraActive(false);
    } else {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' },
        });
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          await videoRef.current.play();
        }
        setCameraActive(true);
      } catch (e) {
        console.error('Kamera gagal:', e);
      }
    }
  };

  // Kiraan Sudut daripada Sentuhan
  const handlePointer = (e: React.MouseEvent | React.TouchEvent) => {
    if (isLocked || !containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;

    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX;
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY;

    const dx = clientX - centerX;
    const dy = clientY - centerY;

    let rad = Math.atan2(dy, dx);
    let deg = Math.round((rad * 180) / Math.PI);

    // Tukar kepada sistem sudut standard (0° ke 180°)
    deg = (deg + 360) % 360;
    if (deg > 180) deg = 360 - deg;
    setAngle(deg);
  };

  useEffect(() => {
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
      }
    };
  }, []);

  return (
    <div className="flex flex-col min-h-full pb-8 select-none">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              if (streamRef.current) streamRef.current.getTracks().forEach((t) => t.stop());
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Jangka Sudut (Protractor)' : 'Protractor'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Sukat darjah sudut pada skrin atau kamera' : 'Measure angles on screen or camera'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <button
            onClick={toggleCamera}
            className={`p-2 rounded-xl border transition ${
              cameraActive ? 'bg-cyan-500 text-slate-950 border-cyan-400 shadow-md' : 'bg-slate-800 text-slate-300 border-slate-700'
            }`}
            title="Togol Kamera Latar"
          >
            <Camera className="w-4 h-4" />
          </button>
          <button
            onClick={() => setIsLocked(!isLocked)}
            className={`p-2 rounded-xl border transition ${
              isLocked ? 'bg-amber-400 text-slate-950 border-amber-300' : 'bg-slate-800 text-slate-300 border-slate-700'
            }`}
            title="Kunci Sudut"
          >
            {isLocked ? <Lock className="w-4 h-4" /> : <Unlock className="w-4 h-4" />}
          </button>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Paparan Nilai Sudut */}
        <div className="text-center">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest block mb-1">
            {lang === 'ms' ? 'Sudut Diukur' : 'Measured Angle'}
          </span>
          <div className="text-6xl font-black font-mono text-cyan-400 tracking-tight tabular-nums">
            {angle}°
          </div>
          <span className="text-xs text-slate-400 font-mono mt-1 block">
            {lang === 'ms' ? `Pelengkap: ${180 - angle}°` : `Supplementary: ${180 - angle}°`}
          </span>
        </div>

        {/* Dail Jangka Sudut Interaktif dengan Lengan Sentuh */}
        <div
          ref={containerRef}
          onMouseMove={(e) => {
            if (e.buttons === 1) handlePointer(e);
          }}
          onTouchMove={handlePointer}
          className="relative w-72 h-72 rounded-full overflow-hidden bg-slate-900 border-4 border-slate-700 shadow-2xl flex items-center justify-center cursor-pointer"
        >
          {/* Suapan Kamera jika aktif */}
          {cameraActive && (
            <video
              ref={videoRef}
              playsInline
              muted
              className="absolute inset-0 w-full h-full object-cover opacity-60"
            />
          )}

          {/* Dail Semi-Circle Busur Derajat SVG */}
          <svg className="absolute inset-0 w-full h-full pointer-events-none" viewBox="0 0 200 200">
            <circle cx="100" cy="100" r="90" stroke="rgba(148, 163, 184, 0.2)" strokeWidth="2" fill="none" />
            <line x1="10" y1="100" x2="190" y2="100" stroke="#3b82f6" strokeWidth="2" />
            <line x1="100" y1="10" x2="100" y2="190" stroke="#3b82f6" strokeWidth="2" />

            {/* Senggatan Setiap 10 Darjah */}
            {[...Array(19)].map((_, i) => {
              const deg = i * 10;
              const rad = (deg * Math.PI) / 180;
              const x1 = 100 + Math.cos(rad) * 90;
              const y1 = 100 - Math.sin(rad) * 90;
              const x2 = 100 + Math.cos(rad) * 78;
              const y2 = 100 - Math.sin(rad) * 78;
              return (
                <g key={i}>
                  <line x1={x1} y1={y1} x2={x2} y2={y2} stroke="#94a3b8" strokeWidth={i % 3 === 0 ? '2' : '1'} />
                  {i % 3 === 0 && (
                    <text
                      x={100 + Math.cos(rad) * 65}
                      y={100 - Math.sin(rad) * 65 + 4}
                      fill="#94a3b8"
                      fontSize="9"
                      fontFamily="monospace"
                      textAnchor="middle"
                    >
                      {deg}°
                    </text>
                  )}
                </g>
              );
            })}
          </svg>

          {/* Lengan Angkup Boleh Diputar */}
          <div
            style={{
              transform: `rotate(${-angle}deg)`,
              transformOrigin: 'bottom left',
            }}
            className="absolute left-1/2 bottom-1/2 w-36 h-1 bg-gradient-to-r from-red-500 to-amber-400 shadow-[0_0_10px_#ef4444] pointer-events-none"
          >
            <div className="absolute right-0 -top-2 w-5 h-5 rounded-full bg-yellow-400 border-2 border-slate-900 shadow" />
          </div>

          {/* Pusat Titik Pangkal */}
          <div className="w-5 h-5 rounded-full bg-cyan-400 border-2 border-slate-900 shadow-md z-10" />
        </div>

        <div className="text-xs text-slate-400 text-center max-w-xs">
          {lang === 'ms'
            ? 'Sentuh dan pusingkan lengan merah untuk menyukat mana-mana sudut.'
            : 'Touch and rotate the red arm to match and measure any angle.'}
        </div>
      </div>
    </div>
  );
};
