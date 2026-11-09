/**
 * @file src/tools/PaintTool.tsx
 * @description Alatan Papan Lukisan & Lakaran Pantas (Paint & Doodle Canvas).
 * 
 * FUNGSI UTAMA:
 * 1. Kanvas sentuhan responsif berskala Retina/HiDPI untuk telefon pintar Android.
 * 2. Pilihan palet warna lengkap (Merah, Biru, Kuning, Hijau, Hitam, Putih Pemadam).
 * 3. Pelarasan ketebalan berus (Brush Stroke Width) daripada 2px hingga 32px.
 * 4. Fungsi Buat Semula (Redo), Buat Asal (Undo), dan Kosongkan Kanvas (Clear).
 * 5. Muat turun terus lakaran sebagai imej PNG berkualiti tinggi ke galeri telefon pengguna.
 */

import React, { useState, useRef, useEffect } from 'react';
import { ArrowLeft, Undo, Redo, Trash2, Download, Paintbrush, Eraser } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const PaintTool: React.FC<Props> = ({ lang, onBack }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [color, setColor] = useState<string>('#ffffff');
  const [lineWidth, setLineWidth] = useState<number>(4);
  const [isEraser, setIsEraser] = useState<boolean>(false);
  const [isDrawing, setIsDrawing] = useState<boolean>(false);
  const [history, setHistory] = useState<ImageData[]>([]);
  const [historyStep, setHistoryStep] = useState<number>(-1);

  // Inisialisasi kanvas mengikut saiz kontena
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const parent = canvas.parentElement;
    if (!parent) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = parent.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;

    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.scale(dpr, dpr);
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      // Latar belakang gelap bersih
      ctx.fillStyle = '#0f172a';
      ctx.fillRect(0, 0, rect.width, rect.height);
      saveState();
    }
  }, []);

  const saveState = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const updated = history.slice(0, historyStep + 1);
    updated.push(imgData);
    setHistory(updated);
    setHistoryStep(updated.length - 1);
  };

  const undo = () => {
    if (historyStep > 0) {
      const nextStep = historyStep - 1;
      setHistoryStep(nextStep);
      restoreState(history[nextStep]);
    }
  };

  const redo = () => {
    if (historyStep < history.length - 1) {
      const nextStep = historyStep + 1;
      setHistoryStep(nextStep);
      restoreState(history[nextStep]);
    }
  };

  const restoreState = (imgData: ImageData) => {
    const canvas = canvasRef.current;
    if (!canvas || !imgData) return;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.putImageData(imgData, 0, 0);
    }
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = '#0f172a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    saveState();
  };

  const getCoordinates = (e: React.MouseEvent | React.TouchEvent) => {
    const canvas = canvasRef.current;
    if (!canvas) return { x: 0, y: 0 };
    const rect = canvas.getBoundingClientRect();

    if ('touches' in e && e.touches.length > 0) {
      return {
        x: e.touches[0].clientX - rect.left,
        y: e.touches[0].clientY - rect.top,
      };
    }
    const mouseEvent = e as React.MouseEvent;
    return {
      x: mouseEvent.clientX - rect.left,
      y: mouseEvent.clientY - rect.top,
    };
  };

  const startDraw = (e: React.MouseEvent | React.TouchEvent) => {
    setIsDrawing(true);
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const { x, y } = getCoordinates(e);
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.strokeStyle = isEraser ? '#0f172a' : color;
    ctx.lineWidth = lineWidth;
  };

  const draw = (e: React.MouseEvent | React.TouchEvent) => {
    if (!isDrawing) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const { x, y } = getCoordinates(e);
    ctx.lineTo(x, y);
    ctx.stroke();
  };

  const endDraw = () => {
    if (isDrawing) {
      setIsDrawing(false);
      saveState();
    }
  };

  const downloadDrawing = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dataUrl = canvas.toDataURL('image/png');
    const link = document.createElement('a');
    link.download = `omnikitmy_sketch_${Date.now()}.png`;
    link.href = dataUrl;
    link.click();
  };

  const colors = ['#ffffff', '#ef4444', '#3b82f6', '#facc15', '#10b981', '#06b6d4', '#a855f7'];

  return (
    <div className="flex flex-col h-full overflow-hidden">
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
              {lang === 'ms' ? 'Papan Lukis (Paint Pad)' : 'Paint & Sketch Pad'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Lakar, conteng nota & muat turun imej' : 'Sketch, doodle & download PNG image'}
            </p>
          </div>
        </div>

        {/* Butang Tindakan Atas */}
        <div className="flex items-center gap-1.5">
          <button
            onClick={undo}
            disabled={historyStep <= 0}
            className="p-2 rounded-xl bg-slate-800 text-slate-300 hover:text-white disabled:opacity-40"
            title="Undur (Undo)"
          >
            <Undo className="w-4 h-4" />
          </button>
          <button
            onClick={redo}
            disabled={historyStep >= history.length - 1}
            className="p-2 rounded-xl bg-slate-800 text-slate-300 hover:text-white disabled:opacity-40"
            title="Maju (Redo)"
          >
            <Redo className="w-4 h-4" />
          </button>
          <button
            onClick={clearCanvas}
            className="p-2 rounded-xl bg-slate-800 text-slate-300 hover:text-red-400"
            title="Padam Semua"
          >
            <Trash2 className="w-4 h-4" />
          </button>
          <button
            onClick={downloadDrawing}
            className="p-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white shadow"
            title="Muat Turun PNG"
          >
            <Download className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Kawasan Kanvas Lukisan Sentuh */}
      <div className="relative flex-1 bg-slate-950 overflow-hidden touch-none">
        <canvas
          ref={canvasRef}
          onMouseDown={startDraw}
          onMouseMove={draw}
          onMouseUp={endDraw}
          onMouseLeave={endDraw}
          onTouchStart={startDraw}
          onTouchMove={draw}
          onTouchEnd={endDraw}
          className="w-full h-full cursor-crosshair"
        />
      </div>

      {/* Bar Alatan Bawah (Warna & Saiz Berus) */}
      <div className="p-3 bg-[#121e36] border-t border-slate-700/60 flex items-center justify-between gap-3">
        {/* Palet Warna */}
        <div className="flex items-center gap-1.5 overflow-x-auto py-1">
          {colors.map((c) => (
            <button
              key={c}
              onClick={() => {
                setColor(c);
                setIsEraser(false);
              }}
              style={{ backgroundColor: c }}
              className={`w-7 h-7 rounded-full shrink-0 transition-transform ${
                color === c && !isEraser ? 'scale-125 ring-2 ring-white shadow-md' : 'opacity-80 hover:opacity-100'
              }`}
            />
          ))}

          {/* Pemadam */}
          <button
            onClick={() => setIsEraser(true)}
            className={`p-1.5 rounded-full border transition ${
              isEraser ? 'bg-amber-400 text-slate-900 border-amber-300' : 'bg-slate-800 text-slate-300 border-slate-700'
            }`}
            title="Pemadam"
          >
            <Eraser className="w-4 h-4" />
          </button>
        </div>

        {/* Pelaras Saiz Berus */}
        <div className="flex items-center gap-2 shrink-0">
          <Paintbrush className="w-4 h-4 text-slate-400" />
          <input
            type="range"
            min="2"
            max="32"
            value={lineWidth}
            onChange={(e) => setLineWidth(parseInt(e.target.value))}
            className="w-20 h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400"
          />
          <span className="text-xs font-mono text-slate-400 w-5">{lineWidth}</span>
        </div>
      </div>
    </div>
  );
};
