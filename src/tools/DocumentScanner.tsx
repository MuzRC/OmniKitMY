/**
 * @file src/tools/DocumentScanner.tsx
 * @description Alatan Pengimbas Dokumen Kamera (Document Scanner & B&W Filter).
 * 
 * FUNGSI UTAMA:
 * 1. Menangkap foto dokumen menggunakan kamera telefon pintar.
 * 2. Penapis Pemprosesan Imej: Dokumen Hitam Putih (B&W Threshold), Grayscale, dan Warna Asli.
 * 3. Garisan panduan penjajaran sudut dokumen (Edge Alignment Guides).
 * 4. Putaran 90 darjah dan muat turun dokumen hasil imbasan dalam format imej bersih.
 */

import React, { useState, useRef, useEffect } from 'react';
import { ArrowLeft, Camera, RotateCw, Download, FileText, Check, Sliders } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const DocumentScanner: React.FC<Props> = ({ lang, onBack }) => {
  const [isCameraActive, setIsCameraActive] = useState<boolean>(false);
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [filterMode, setFilterMode] = useState<'bw' | 'gray' | 'color'>('bw');
  const [contrastThreshold, setContrastThreshold] = useState<number>(128);
  const [processedUrl, setProcessedUrl] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const startCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: { ideal: 1920 }, height: { ideal: 1080 } },
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setIsCameraActive(true);
      setCapturedImage(null);
      setProcessedUrl(null);
    } catch (e) {
      console.error('Kamera gagal:', e);
    }
  };

  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    setIsCameraActive(false);
  };

  const snapDocument = () => {
    const video = videoRef.current;
    if (!video) return;

    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const rawData = canvas.toDataURL('image/jpeg', 0.95);
    setCapturedImage(rawData);
    stopCamera();
    applyFilter(rawData, filterMode, contrastThreshold);
  };

  // Gunakan penapis pemprosesan imej dokumen
  const applyFilter = (imgSrc: string, mode: 'bw' | 'gray' | 'color', threshold: number) => {
    const img = new Image();
    img.src = imgSrc;
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = img.width;
      canvas.height = img.height;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      ctx.drawImage(img, 0, 0);

      if (mode !== 'color') {
        const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const d = imgData.data;

        for (let i = 0; i < d.length; i += 4) {
          const r = d[i], g = d[i + 1], b = d[i + 2];
          // Grayscale luminance
          const gray = 0.299 * r + 0.587 * g + 0.114 * b;

          if (mode === 'bw') {
            // Threshold binarization untuk teks dokumen yang jelas
            const val = gray > threshold ? 255 : 0;
            d[i] = val;
            d[i + 1] = val;
            d[i + 2] = val;
          } else if (mode === 'gray') {
            d[i] = gray;
            d[i + 1] = gray;
            d[i + 2] = gray;
          }
        }
        ctx.putImageData(imgData, 0, 0);
      }

      setProcessedUrl(canvas.toDataURL('image/jpeg', 0.92));
    };
  };

  useEffect(() => {
    return () => {
      stopCamera();
    };
  }, []);

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              stopCamera();
              onBack();
            }}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Pengimbas Dokumen' : 'Document Scanner'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Imbas dokumen & penapis kontras tinggi B&W' : 'Scan docs with high contrast B&W filter'}
            </p>
          </div>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-5">
        {/* Paparan Kamera / Imej Imbasan */}
        <div className="relative w-full aspect-[3/4] max-w-xs rounded-2xl overflow-hidden bg-slate-900 border-2 border-slate-700 shadow-2xl flex items-center justify-center">
          {isCameraActive && (
            <>
              <video
                ref={videoRef}
                playsInline
                muted
                className="w-full h-full object-cover"
              />
              {/* Panduan Bingkai Dokumen A4 */}
              <div className="absolute inset-4 border-2 border-cyan-400/70 border-dashed rounded-xl pointer-events-none flex items-center justify-center">
                <span className="text-[11px] font-mono text-cyan-300/80 bg-slate-950/60 px-2 py-0.5 rounded">
                  {lang === 'ms' ? 'Jajarkan Dokumen di Sini' : 'Align Document Here'}
                </span>
              </div>
            </>
          )}

          {!isCameraActive && !processedUrl && (
            <div className="p-6 text-center flex flex-col items-center gap-3">
              <div className="w-16 h-16 rounded-2xl bg-blue-600/20 text-blue-400 flex items-center justify-center border border-blue-500/30">
                <FileText className="w-8 h-8" />
              </div>
              <p className="text-sm font-semibold text-white">
                {lang === 'ms' ? 'Sedia untuk Mengimbas' : 'Ready to Scan Document'}
              </p>
              <p className="text-xs text-slate-400">
                {lang === 'ms' ? 'Tukar kertas dokumen ke imej digital berkontras tinggi' : 'Digitize paper receipts & contracts'}
              </p>
            </div>
          )}

          {processedUrl && (
            <img
              src={processedUrl}
              alt="Scanned Document"
              className="w-full h-full object-contain bg-white"
            />
          )}
        </div>

        {/* Pilihan Penapis Imej jika ada foto diimbas */}
        {capturedImage && (
          <div className="w-full flex flex-col gap-3">
            <div className="grid grid-cols-3 gap-2">
              {[
                { id: 'bw', label: lang === 'ms' ? 'B&W Kontras' : 'B&W Scan' },
                { id: 'gray', label: lang === 'ms' ? 'Kelabu' : 'Grayscale' },
                { id: 'color', label: lang === 'ms' ? 'Warna Asli' : 'Color' },
              ].map((f) => (
                <button
                  key={f.id}
                  onClick={() => {
                    const nextMode = f.id as 'bw' | 'gray' | 'color';
                    setFilterMode(nextMode);
                    applyFilter(capturedImage, nextMode, contrastThreshold);
                  }}
                  className={`py-2 rounded-xl text-xs font-semibold border transition ${
                    filterMode === f.id
                      ? 'bg-blue-600 border-blue-500 text-white'
                      : 'bg-slate-900 border-slate-800 text-slate-400'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Butang Tindakan Utama */}
        <div className="w-full flex gap-3">
          {!isCameraActive && !processedUrl && (
            <button
              onClick={startCamera}
              className="w-full py-4 rounded-2xl bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-bold text-base shadow-xl flex items-center justify-center gap-2"
            >
              <Camera className="w-5 h-5" />
              {lang === 'ms' ? 'Buka Kamera Pengimbas' : 'Open Scanner Camera'}
            </button>
          )}

          {isCameraActive && (
            <button
              onClick={snapDocument}
              className="w-full py-4 rounded-2xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-black text-base shadow-xl flex items-center justify-center gap-2"
            >
              <Camera className="w-5 h-5 fill-slate-950" />
              {lang === 'ms' ? 'Tangkap Dokumen' : 'Capture Document'}
            </button>
          )}

          {processedUrl && (
            <>
              <button
                onClick={startCamera}
                className="flex-1 py-3.5 rounded-xl bg-slate-800 text-slate-200 text-xs font-bold"
              >
                {lang === 'ms' ? 'Imbas Lagi' : 'Retake'}
              </button>
              <a
                href={processedUrl}
                download={`omnikitmy_doc_${Date.now()}.jpg`}
                className="flex-1 py-3.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold flex items-center justify-center gap-2"
              >
                <Download className="w-4 h-4" />
                {lang === 'ms' ? 'Muat Turun' : 'Download'}
              </a>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
