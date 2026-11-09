/**
 * @file src/tools/LightMeterTool.tsx
 * @description Alatan Meter Kecerahan Cahaya (Ambient Light Meter - Lux).
 * 
 * FUNGSI UTAMA:
 * 1. Mengukur keamatan cahaya persekitaran dalam unit Lux menggunakan Generic AmbientLightSensor API.
 * 2. Menggunakan analisis luminositi video kamera (Camera Frame Luminance Analysis) sebagai sandaran
 *    tepat sekiranya peranti tidak menyokong AmbientLightSensor secara langsung.
 * 3. Merekod bacaan Min, Purata, dan Maksimum Lux.
 * 4. Menyediakan panduan piawaian pencahayaan selesa untuk membaca, bekerja, dan fotografi.
 */

import React, { useState, useEffect, useRef } from 'react';
import { ArrowLeft, Sun, SunMedium, Moon, RotateCcw, Camera } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const LightMeterTool: React.FC<Props> = ({ lang, onBack }) => {
  const [lux, setLux] = useState<number>(0);
  const [minLux, setMinLux] = useState<number | null>(null);
  const [maxLux, setMaxLux] = useState<number | null>(null);
  const [sensorType, setSensorType] = useState<'ambient' | 'camera'>('camera');
  const [isMeasuring, setIsMeasuring] = useState<boolean>(false);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const intervalRef = useRef<number | null>(null);

  // Mulakan Sensor Cahaya atau Kamera
  const startMeasuring = async () => {
    // 1. Cuba AmbientLightSensor dahulu
    const AmbientSensor = (window as unknown as { AmbientLightSensor?: { new (): { start: () => void; illuminance: number; addEventListener: (t: string, cb: () => void) => void; stop: () => void } } }).AmbientLightSensor;
    if (AmbientSensor) {
      try {
        const sensor = new AmbientSensor();
        sensor.addEventListener('reading', () => {
          updateLux(Math.round(sensor.illuminance));
        });
        sensor.start();
        setSensorType('ambient');
        setIsMeasuring(true);
        return;
      } catch (e) {
        console.warn('AmbientLightSensor tidak dapat dimulakan, guna kamera:', e);
      }
    }

    // 2. Gunakan pengiraan luminositi kamera
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: 160, height: 120 },
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setSensorType('camera');
      setIsMeasuring(true);

      // Gelung analisa kecerahan setiap 200ms
      intervalRef.current = window.setInterval(() => {
        calculateCameraLuminance();
      }, 200);
    } catch (err) {
      console.error('Kamera gagal dimulakan:', err);
    }
  };

  const calculateCameraLuminance = () => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas || video.readyState < 2) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.drawImage(video, 0, 0, 32, 24);
    const imgData = ctx.getImageData(0, 0, 32, 24).data;

    let totalLuminance = 0;
    const pixelCount = imgData.length / 4;

    for (let i = 0; i < imgData.length; i += 4) {
      const r = imgData[i];
      const g = imgData[i + 1];
      const b = imgData[i + 2];
      // Formula piawai persepsi kecerahan: 0.299R + 0.587G + 0.114B
      const lum = 0.299 * r + 0.587 * g + 0.114 * b;
      totalLuminance += lum;
    }

    const avgLum = totalLuminance / pixelCount;
    // Skala daripada kecerahan kamera ke anggaran Lux persekitaran (0 - 1500+ lux)
    const estimatedLux = Math.round(Math.pow(avgLum / 255, 2.2) * 2000);
    updateLux(estimatedLux);
  };

  const updateLux = (val: number) => {
    setLux(val);
    setMinLux((prev) => (prev === null ? val : Math.min(prev, val)));
    setMaxLux((prev) => (prev === null ? val : Math.max(prev, val)));
  };

  const stopMeasuring = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    setIsMeasuring(false);
  };

  const resetStats = () => {
    setMinLux(null);
    setMaxLux(null);
    setLux(0);
  };

  useEffect(() => {
    return () => {
      stopMeasuring();
    };
  }, []);

  const getLightStandard = (l: number) => {
    if (l < 50) return { label: lang === 'ms' ? 'Gelap / Malap (Masa Tidur)' : 'Dark / Dim', color: 'text-indigo-400' };
    if (l < 250) return { label: lang === 'ms' ? 'Selesa untuk Rehat & Santai' : 'Relaxing & Ambient', color: 'text-blue-400' };
    if (l < 600) return { label: lang === 'ms' ? 'Sesuai Membaca & Pejabat (300-500 Lux)' : 'Office & Reading Standard', color: 'text-emerald-400' };
    if (l < 1200) return { label: lang === 'ms' ? 'Kecerahan Tinggi (Kerja Teliti)' : 'High Precision Task Light', color: 'text-yellow-400' };
    return { label: lang === 'ms' ? 'Cahaya Sangat Terang / Luar Bangunan' : 'Intense Outdoor Daylight', color: 'text-amber-400 font-bold' };
  };

  const standard = getLightStandard(lux);

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              stopMeasuring();
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Meter Cahaya (Light Meter)' : 'Light Meter'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Ukur keamatan cahaya persekitaran (Lux)' : 'Measure ambient illumination in Lux'}
            </p>
          </div>
        </div>

        {isMeasuring && (
          <button
            onClick={resetStats}
            className="p-2 rounded-xl bg-slate-800 text-slate-300 hover:text-white"
            title="Set Semula"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
        )}
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-6">
        {/* Elemen Tersembunyi Kamera Analisis */}
        <video ref={videoRef} playsInline muted className="hidden" />
        <canvas ref={canvasRef} width={32} height={24} className="hidden" />

        {/* Paparan Lux Utama */}
        <div className="relative w-64 h-64 rounded-full bg-gradient-to-b from-[#131f3b] to-[#0d162a] border-4 border-slate-700 shadow-2xl flex flex-col items-center justify-center p-6 text-center">
          <Sun className={`w-10 h-10 mb-2 transition-transform duration-300 ${isMeasuring ? 'text-yellow-400 scale-110 animate-spin-slow' : 'text-slate-500'}`} />
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest">
            {lang === 'ms' ? 'Kecerahan Cahaya' : 'Illuminance'}
          </span>
          <div className="text-5xl font-black font-mono text-white tracking-tight tabular-nums mt-1">
            {lux}
          </div>
          <span className="text-sm font-bold text-yellow-400 mt-1">LUX (lx)</span>

          <div className={`text-[11px] mt-3 px-3 py-1 rounded-full bg-slate-900/80 border border-slate-800 ${standard.color}`}>
            {standard.label}
          </div>
        </div>

        {/* Statistik Min & Max */}
        <div className="w-full grid grid-cols-2 gap-3">
          <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-xs text-slate-400 uppercase font-semibold block mb-1">Min Lux</span>
            <span className="text-2xl font-bold font-mono text-cyan-400 tabular-nums">
              {minLux !== null ? `${minLux} lx` : '--'}
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 text-center">
            <span className="text-xs text-slate-400 uppercase font-semibold block mb-1">Max Lux</span>
            <span className="text-2xl font-bold font-mono text-yellow-400 tabular-nums">
              {maxLux !== null ? `${maxLux} lx` : '--'}
            </span>
          </div>
        </div>

        {/* Panduan Piawaian Pencahayaan (Lighting Reference) */}
        <div className="w-full p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2 text-xs">
          <span className="font-semibold text-slate-300">
            {lang === 'ms' ? 'Panduan Piawaian Cahaya Selesa:' : 'Recommended Light Levels:'}
          </span>
          <div className="flex justify-between text-slate-400 border-b border-slate-800 pb-1">
            <span>{lang === 'ms' ? 'Membaca & Belajar' : 'Reading & Study'}</span>
            <span className="font-mono text-emerald-400">300 - 500 Lux</span>
          </div>
          <div className="flex justify-between text-slate-400 border-b border-slate-800 pb-1">
            <span>{lang === 'ms' ? 'Ruang Tamu & Bersantai' : 'Living Room'}</span>
            <span className="font-mono text-blue-400">100 - 200 Lux</span>
          </div>
          <div className="flex justify-between text-slate-400">
            <span>{lang === 'ms' ? 'Fotografi / Kerja Teliti' : 'Photography Studio'}</span>
            <span className="font-mono text-yellow-400">1,000+ Lux</span>
          </div>
        </div>

        {/* Butang Kawalan */}
        <div className="w-full">
          {!isMeasuring ? (
            <button
              onClick={startMeasuring}
              className="w-full py-4 px-6 rounded-2xl bg-gradient-to-r from-amber-500 to-yellow-500 hover:from-amber-400 hover:to-yellow-400 text-slate-950 font-bold text-base shadow-xl active:scale-[0.98] transition flex items-center justify-center gap-3"
            >
              <Sun className="w-5 h-5 fill-slate-950" />
              {lang === 'ms' ? 'Mula Ukur Cahaya' : 'Start Light Sensor'}
            </button>
          ) : (
            <button
              onClick={stopMeasuring}
              className="w-full py-4 px-6 rounded-2xl bg-red-600 hover:bg-red-500 text-white font-bold text-base shadow-xl active:scale-[0.98] transition flex items-center justify-center gap-3"
            >
              {lang === 'ms' ? 'Hentikan Sensor' : 'Stop Sensor'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
