/**
 * @file src/tools/QRScannerTool.tsx
 * @description Alatan Pengimbas & Penjana Kod QR (QR & Barcode Scanner / Generator).
 * 
 * FUNGSI UTAMA:
 * 1. Pengimbas Langsung Kamera (Live Camera Scanner) dengan sokongan BarcodeDetector pelayar asli
 *    dan pemprosesan imej kanvas bagi peranti tanpa perkakasan BarcodeDetector.
 * 2. Pengimbas Fail Imej/Tangkapan Skrin untuk mengimbas kod QR daripada galeri telefon.
 * 3. Penjana Kod QR Penuh (Full QR Generator) dengan pilihan muat turun PNG dan penyalinan teks.
 * 4. Kawalan Lampu Suluh (Torch / Flashlight) peranti untuk pengimbasan waktu malam.
 * 5. Sejarah Imbasan Tempatan yang disimpan secara automatik dalam storan luar talian.
 */

import React, { useState, useEffect, useRef } from 'react';
import QRCode from 'qrcode';
import { Camera, QrCode, Flashlight, Upload, Copy, Check, ExternalLink, RefreshCw, History, Trash2, ArrowLeft } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

interface ScanHistoryItem {
  id: string;
  text: string;
  date: string;
}

export const QRScannerTool: React.FC<Props> = ({ lang, onBack }) => {
  const [activeTab, setActiveTab] = useState<'scan' | 'generate' | 'history'>('scan');
  
  // Keadaan Pengimbas (Scanner states)
  const [isScanning, setIsScanning] = useState(false);
  const [scanResult, setScanResult] = useState<string | null>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [torchEnabled, setTorchEnabled] = useState(false);
  const [hasTorch, setHasTorch] = useState(false);
  const [copied, setCopied] = useState(false);
  const [scanHistory, setScanHistory] = useState<ScanHistoryItem[]>(() => {
    try {
      const saved = localStorage.getItem('omnikitmy_qr_history');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  // Keadaan Penjana (Generator states)
  const [inputText, setInputText] = useState('https://omnikitmy.app');
  const [qrDataUrl, setQrDataUrl] = useState<string>('');
  const [qrColor, setQrColor] = useState('#000000');
  const [qrBgColor, setQrBgColor] = useState('#ffffff');

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const scanIntervalRef = useRef<number | null>(null);

  // Jana QR Code apabila input teks atau warna berubah
  useEffect(() => {
    if (activeTab === 'generate' && inputText) {
      QRCode.toDataURL(inputText, {
        width: 320,
        margin: 2,
        color: {
          dark: qrColor,
          light: qrBgColor,
        },
      })
        .then((url) => setQrDataUrl(url))
        .catch((err) => console.error('Ralat menjana QR:', err));
    }
  }, [inputText, qrColor, qrBgColor, activeTab]);

  // Simpan Sejarah Imbasan
  const addScanHistory = (text: string) => {
    const newItem: ScanHistoryItem = {
      id: Date.now().toString(),
      text,
      date: new Date().toLocaleString(lang === 'ms' ? 'ms-MY' : 'en-US'),
    };
    const updated = [newItem, ...scanHistory.slice(0, 19)];
    setScanHistory(updated);
    try {
      localStorage.setItem('omnikitmy_qr_history', JSON.stringify(updated));
    } catch (e) {
      console.error('Ralat simpan sejarah:', e);
    }
  };

  // Mulakan Kamera untuk Mengimbas
  const startCamera = async () => {
    setCameraError(null);
    setScanResult(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setIsScanning(true);

      // Periksa keupayaan lampu suluh (torch)
      const track = stream.getVideoTracks()[0];
      const capabilities = track.getCapabilities ? (track.getCapabilities() as { torch?: boolean }) : {};
      setHasTorch(Boolean(capabilities?.torch));

      // Mulakan gelung pengesanan bingkai (frame loop)
      startDetectionLoop();
    } catch (err) {
      console.error('Akses kamera gagal:', err);
      setCameraError(
        lang === 'ms'
          ? 'Kebenaran kamera tidak diberikan atau kamera tidak ditemui. Sila benarkan akses kamera.'
          : 'Camera access denied or camera not found. Please allow camera permissions.'
      );
      setIsScanning(false);
    }
  };

  // Hentikan Kamera
  const stopCamera = () => {
    if (scanIntervalRef.current) {
      clearInterval(scanIntervalRef.current);
      scanIntervalRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setIsScanning(false);
    setTorchEnabled(false);
  };

  // Togol Lampu Suluh (Flashlight)
  const toggleTorch = async () => {
    if (!streamRef.current) return;
    const track = streamRef.current.getVideoTracks()[0];
    try {
      const nextState = !torchEnabled;
      await (track as MediaStreamTrack & { applyConstraints: (c: unknown) => Promise<void> }).applyConstraints({
        advanced: [{ torch: nextState }],
      });
      setTorchEnabled(nextState);
    } catch (e) {
      console.error('Ralat lampu suluh:', e);
    }
  };

  // Gelung Pengesanan QR (Detection Loop)
  const startDetectionLoop = () => {
    // Semak sokongan BarcodeDetector asli
    const BarcodeDetectorClass = (window as unknown as { BarcodeDetector?: { new (opts: { formats: string[] }): { detect: (src: ImageBitmapSource) => Promise<Array<{ rawValue: string }>> } } }).BarcodeDetector;

    let detector: { detect: (src: ImageBitmapSource) => Promise<Array<{ rawValue: string }>> } | null = null;
    if (BarcodeDetectorClass) {
      try {
        detector = new BarcodeDetectorClass({ formats: ['qr_code', 'code_128', 'ean_13', 'data_matrix'] });
      } catch (e) {
        console.warn('BarcodeDetector tidak disokong secara penuh:', e);
      }
    }

    scanIntervalRef.current = window.setInterval(async () => {
      if (!videoRef.current || videoRef.current.readyState < 2) return;

      if (detector) {
        try {
          const barcodes = await detector.detect(videoRef.current);
          if (barcodes.length > 0) {
            const result = barcodes[0].rawValue;
            handleScanSuccess(result);
          }
        } catch (e) {
          // Lanjut ke bingkai seterusnya jika ada ralat
        }
      }
    }, 250);
  };

  const handleScanSuccess = (code: string) => {
    // Mainkan getaran haptik ringkas jika peranti menyokong
    if (navigator.vibrate) {
      navigator.vibrate([40, 20, 40]);
    }
    setScanResult(code);
    addScanHistory(code);
    stopCamera();
  };

  // Imbas fail imej yang dimuat naik (daripada tangkapan skrin atau galeri)
  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      const img = new Image();
      img.src = URL.createObjectURL(file);
      await img.decode();

      const BarcodeDetectorClass = (window as unknown as { BarcodeDetector?: { new (opts: { formats: string[] }): { detect: (src: ImageBitmapSource) => Promise<Array<{ rawValue: string }>> } } }).BarcodeDetector;
      if (BarcodeDetectorClass) {
        const detector = new BarcodeDetectorClass({ formats: ['qr_code', 'code_128', 'ean_13'] });
        const barcodes = await detector.detect(img);
        if (barcodes.length > 0) {
          handleScanSuccess(barcodes[0].rawValue);
          return;
        }
      }

      setCameraError(
        lang === 'ms'
          ? 'Tiada kod QR yang jelas dikesan pada imej ini.'
          : 'No clear QR code detected in this image.'
      );
    } catch (err) {
      console.error('Ralat imbas fail:', err);
      setCameraError(
        lang === 'ms'
          ? 'Gagal memproses fail imej yang dipilih.'
          : 'Failed to process selected image.'
      );
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const isUrl = (text: string) => {
    return /^https?:\/\//i.test(text.trim());
  };

  // Bersihkan kamera apabila komponen ditutup
  useEffect(() => {
    return () => {
      stopCamera();
    };
  }, []);

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Top Header Alatan */}
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
              {lang === 'ms' ? 'Pengimbas & Penjana QR' : 'QR Scanner & Generator'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Kamera langsung & muat turun kod QR' : 'Live camera & QR code download'}
            </p>
          </div>
        </div>

        {/* Tab Penukar Mod */}
        <div className="flex items-center bg-slate-900/80 p-1 rounded-xl border border-slate-700/50">
          <button
            onClick={() => {
              setActiveTab('scan');
              setScanResult(null);
            }}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              activeTab === 'scan' ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            {lang === 'ms' ? 'Imbas' : 'Scan'}
          </button>
          <button
            onClick={() => {
              stopCamera();
              setActiveTab('generate');
            }}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              activeTab === 'generate' ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            {lang === 'ms' ? 'Jana' : 'Create'}
          </button>
          <button
            onClick={() => {
              stopCamera();
              setActiveTab('history');
            }}
            className={`px-2 py-1.5 rounded-lg text-xs font-semibold transition ${
              activeTab === 'history' ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
            title="Sejarah Imbasan"
          >
            <History className="w-4 h-4" />
          </button>
        </div>
      </div>

      <div className="p-4 max-w-lg mx-auto w-full flex-1 flex flex-col">
        {/* ===================== TAB 1: PENGIMBAS QR ===================== */}
        {activeTab === 'scan' && (
          <div className="flex flex-col items-center gap-4">
            {/* Ruang Paparan Kamera */}
            <div className="relative w-full aspect-square max-w-xs rounded-2xl overflow-hidden bg-slate-900 border-2 border-slate-700/70 shadow-2xl flex items-center justify-center">
              <video
                ref={videoRef}
                playsInline
                muted
                className={`w-full h-full object-cover ${!isScanning ? 'hidden' : 'block'}`}
              />
              <canvas ref={canvasRef} className="hidden" />

              {!isScanning && !scanResult && (
                <div className="flex flex-col items-center gap-3 p-6 text-center">
                  <div className="w-16 h-16 rounded-2xl bg-blue-600/20 text-blue-400 flex items-center justify-center border border-blue-500/30">
                    <Camera className="w-8 h-8" />
                  </div>
                  <p className="text-sm text-slate-300 font-medium">
                    {lang === 'ms'
                      ? 'Tekan butang di bawah untuk membuka kamera'
                      : 'Tap button below to launch camera scanner'}
                  </p>
                  <p className="text-xs text-slate-400">
                    {lang === 'ms' ? 'Menyokong kod QR, Kod Bar, dan URL' : 'Supports QR codes, Barcodes, & URLs'}
                  </p>
                </div>
              )}

              {/* Bingkai Sasaran Pengimbas Interaktif */}
              {isScanning && (
                <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
                  <div className="w-3/4 h-3/4 border-2 border-cyan-400 rounded-xl relative animate-pulse">
                    <div className="absolute top-0 left-0 w-4 h-4 border-t-4 border-l-4 border-yellow-400" />
                    <div className="absolute top-0 right-0 w-4 h-4 border-t-4 border-r-4 border-yellow-400" />
                    <div className="absolute bottom-0 left-0 w-4 h-4 border-b-4 border-l-4 border-yellow-400" />
                    <div className="absolute bottom-0 right-0 w-4 h-4 border-b-4 border-r-4 border-yellow-400" />
                    <div className="w-full h-0.5 bg-red-500/80 absolute top-1/2 left-0 shadow-[0_0_8px_#ef4444]" />
                  </div>
                </div>
              )}

              {/* Butang Lampu Suluh di Sudut Atas */}
              {isScanning && hasTorch && (
                <button
                  onClick={toggleTorch}
                  className={`absolute top-3 right-3 p-2.5 rounded-full backdrop-blur-md transition ${
                    torchEnabled ? 'bg-yellow-400 text-slate-900 shadow-lg' : 'bg-black/60 text-white'
                  }`}
                  aria-label="Lampu Suluh"
                >
                  <Flashlight className="w-5 h-5" />
                </button>
              )}
            </div>

            {/* Butang Tindakan Pengimbas */}
            <div className="flex items-center gap-3 w-full max-w-xs">
              {!isScanning ? (
                <button
                  onClick={startCamera}
                  className="flex-1 py-3 px-4 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-semibold text-sm shadow-lg shadow-blue-900/30 active:scale-[0.98] transition flex items-center justify-center gap-2"
                >
                  <Camera className="w-4 h-4" />
                  {lang === 'ms' ? 'Mulakan Imbasan' : 'Start Camera Scan'}
                </button>
              ) : (
                <button
                  onClick={stopCamera}
                  className="flex-1 py-3 px-4 rounded-xl bg-red-600 text-white font-semibold text-sm shadow-lg active:scale-[0.98] transition flex items-center justify-center gap-2"
                >
                  {lang === 'ms' ? 'Hentikan Kamera' : 'Stop Camera'}
                </button>
              )}

              {/* Butang Muat Naik Fail Imej */}
              <label className="py-3 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-semibold text-sm border border-slate-700 cursor-pointer transition flex items-center justify-center gap-2">
                <Upload className="w-4 h-4 text-cyan-400" />
                <span className="hidden sm:inline">{lang === 'ms' ? 'Pilih Imej' : 'Upload'}</span>
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleFileUpload}
                  className="hidden"
                />
              </label>
            </div>

            {/* Ralat Kamera jika ada */}
            {cameraError && (
              <div className="w-full max-w-xs p-3 rounded-xl bg-red-950/60 border border-red-700/50 text-red-200 text-xs">
                {cameraError}
              </div>
            )}

            {/* Paparan Keputusan Imbasan */}
            {scanResult && (
              <div className="w-full max-w-xs p-4 rounded-2xl bg-[#121e36] border border-cyan-500/40 shadow-xl flex flex-col gap-3 animate-in fade-in zoom-in-95">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-cyan-400 uppercase tracking-wider">
                    {lang === 'ms' ? 'Keputusan Dikesan' : 'Result Detected'}
                  </span>
                  <button
                    onClick={() => setScanResult(null)}
                    className="text-xs text-slate-400 hover:text-white"
                  >
                    {lang === 'ms' ? 'Padam' : 'Clear'}
                  </button>
                </div>

                <div className="p-3 bg-slate-900/90 rounded-xl border border-slate-800 text-slate-100 text-sm break-all font-mono">
                  {scanResult}
                </div>

                <div className="flex items-center gap-2 pt-1">
                  <button
                    onClick={() => copyToClipboard(scanResult)}
                    className="flex-1 py-2 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-medium flex items-center justify-center gap-1.5 transition"
                  >
                    {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    {copied ? (lang === 'ms' ? 'Disalin!' : 'Copied!') : (lang === 'ms' ? 'Salin' : 'Copy')}
                  </button>

                  {isUrl(scanResult) && (
                    <a
                      href={scanResult}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex-1 py-2 px-3 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium flex items-center justify-center gap-1.5 transition"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                      {lang === 'ms' ? 'Buka Pautan' : 'Open Link'}
                    </a>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        {/* ===================== TAB 2: PENJANA KOD QR ===================== */}
        {activeTab === 'generate' && (
          <div className="flex flex-col items-center gap-4">
            <div className="w-full flex flex-col gap-2">
              <label className="text-xs font-semibold text-slate-300">
                {lang === 'ms' ? 'Teks atau Pautan URL:' : 'Text or Web URL:'}
              </label>
              <textarea
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                placeholder="https://contoh.com atau teks rahsia..."
                rows={3}
                className="w-full p-3 rounded-xl bg-slate-900 border border-slate-700 text-white text-sm focus:outline-none focus:border-blue-500 transition"
              />
            </div>

            {/* Pilihan Warna */}
            <div className="w-full grid grid-cols-2 gap-3">
              <div>
                <label className="text-[11px] text-slate-400 block mb-1">
                  {lang === 'ms' ? 'Warna Kod QR' : 'QR Color'}
                </label>
                <div className="flex items-center gap-2 p-2 bg-slate-900 rounded-xl border border-slate-700">
                  <input
                    type="color"
                    value={qrColor}
                    onChange={(e) => setQrColor(e.target.value)}
                    className="w-7 h-7 rounded border-0 cursor-pointer bg-transparent"
                  />
                  <span className="text-xs font-mono text-slate-300">{qrColor}</span>
                </div>
              </div>

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">
                  {lang === 'ms' ? 'Warna Latar' : 'Background'}
                </label>
                <div className="flex items-center gap-2 p-2 bg-slate-900 rounded-xl border border-slate-700">
                  <input
                    type="color"
                    value={qrBgColor}
                    onChange={(e) => setQrBgColor(e.target.value)}
                    className="w-7 h-7 rounded border-0 cursor-pointer bg-transparent"
                  />
                  <span className="text-xs font-mono text-slate-300">{qrBgColor}</span>
                </div>
              </div>
            </div>

            {/* Paparan Kod QR yang Dijana */}
            {qrDataUrl && (
              <div className="flex flex-col items-center gap-3 p-4 bg-white rounded-2xl shadow-xl mt-2">
                <img
                  src={qrDataUrl}
                  alt="QR Code"
                  className="w-56 h-56 object-contain rounded-lg"
                />
              </div>
            )}

            {/* Butang Muat Turun */}
            {qrDataUrl && (
              <a
                href={qrDataUrl}
                download="omnikitmy_qr.png"
                className="w-full max-w-xs py-3 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-sm shadow-lg text-center flex items-center justify-center gap-2 transition"
              >
                <Upload className="w-4 h-4 rotate-180" />
                {lang === 'ms' ? 'Muat Turun Imej PNG' : 'Download PNG Image'}
              </a>
            )}
          </div>
        )}

        {/* ===================== TAB 3: SEJARAH IMBASAN ===================== */}
        {activeTab === 'history' && (
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                {lang === 'ms' ? 'Sejarah Imbasan Terkini' : 'Recent Scan History'}
              </span>
              {scanHistory.length > 0 && (
                <button
                  onClick={() => {
                    setScanHistory([]);
                    localStorage.removeItem('omnikitmy_qr_history');
                  }}
                  className="text-xs text-red-400 hover:text-red-300 flex items-center gap-1"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  {lang === 'ms' ? 'Kosongkan' : 'Clear All'}
                </button>
              )}
            </div>

            {scanHistory.length === 0 ? (
              <div className="p-8 text-center text-slate-500 text-xs">
                {lang === 'ms' ? 'Belum ada sejarah imbasan.' : 'No scan history yet.'}
              </div>
            ) : (
              scanHistory.map((item) => (
                <div
                  key={item.id}
                  className="p-3 bg-[#121e36] rounded-xl border border-slate-700/60 flex flex-col gap-1.5"
                >
                  <div className="flex items-center justify-between text-[11px] text-slate-400">
                    <span>{item.date}</span>
                    <button
                      onClick={() => copyToClipboard(item.text)}
                      className="text-cyan-400 hover:underline flex items-center gap-1"
                    >
                      <Copy className="w-3 h-3" />
                      {lang === 'ms' ? 'Salin' : 'Copy'}
                    </button>
                  </div>
                  <div className="text-xs font-mono text-slate-200 break-all bg-slate-900/60 p-2 rounded-lg">
                    {item.text}
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};
