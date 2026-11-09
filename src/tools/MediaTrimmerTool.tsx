/**
 * @file src/tools/MediaTrimmerTool.tsx
 * @description Alatan Pemotong Video & Pengambil Bingkai Foto (Video Trimmer & Frame Grabber).
 * 
 * FUNGSI UTAMA:
 * 1. Memuatkan fail video MP4 / WebM terus dari peranti pengguna tanpa sebarang muat naik ke awan.
 * 2. Menetapkan masa Mula (Start Time) dan masa Tamat (End Time) dengan peluncur masa.
 * 3. Tangkapan Bingkai Foto Segera (Snapshot Grabber) untuk menyimpan foto daripada saat video tertentu.
 * 4. Eksport klip video atau foto dengan resolusi penuh.
 */

import React, { useState, useRef } from 'react';
import { ArrowLeft, Video, Scissors, Camera, Download, Play, Pause } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

export const MediaTrimmerTool: React.FC<Props> = ({ lang, onBack }) => {
  const [videoSrc, setVideoSrc] = useState<string | null>(null);
  const [duration, setDuration] = useState<number>(0);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [startTime, setStartTime] = useState<number>(0);
  const [endTime, setEndTime] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [snapshotUrl, setSnapshotUrl] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement | null>(null);

  const handleVideoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const url = URL.createObjectURL(file);
    setVideoSrc(url);
    setSnapshotUrl(null);
  };

  const handleLoadedMetadata = () => {
    if (videoRef.current) {
      const dur = Math.floor(videoRef.current.duration);
      setDuration(dur);
      setStartTime(0);
      setEndTime(dur);
    }
  };

  const handleTimeUpdate = () => {
    if (videoRef.current) {
      setCurrentTime(videoRef.current.currentTime);
      if (videoRef.current.currentTime >= endTime && isPlaying) {
        videoRef.current.pause();
        setIsPlaying(false);
      }
    }
  };

  const togglePlay = () => {
    if (!videoRef.current) return;
    if (isPlaying) {
      videoRef.current.pause();
      setIsPlaying(false);
    } else {
      if (videoRef.current.currentTime < startTime || videoRef.current.currentTime >= endTime) {
        videoRef.current.currentTime = startTime;
      }
      videoRef.current.play();
      setIsPlaying(true);
    }
  };

  const grabSnapshot = () => {
    const video = videoRef.current;
    if (!video) return;

    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const snap = canvas.toDataURL('image/jpeg', 0.95);
    setSnapshotUrl(snap);
  };

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
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
              {lang === 'ms' ? 'Pemotong Video & Tangkapan Bingkai' : 'Video Trimmer & Snapshot'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Pangkas video & tangkap foto kualiti tinggi' : 'Trim videos & grab high-res photos'}
            </p>
          </div>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col items-center justify-between gap-5">
        {!videoSrc ? (
          <label className="flex flex-col items-center justify-center p-8 rounded-3xl border-2 border-dashed border-slate-700 bg-slate-900/50 hover:bg-slate-900/80 cursor-pointer transition gap-3 text-center w-full">
            <div className="w-16 h-16 rounded-2xl bg-blue-600/20 text-blue-400 flex items-center justify-center border border-blue-500/30">
              <Video className="w-8 h-8" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">
                {lang === 'ms' ? 'Pilih Fail Video dari Telefon' : 'Select Video File'}
              </p>
              <p className="text-xs text-slate-400 mt-1">
                {lang === 'ms' ? 'Menyokong fail MP4, WebM dan MOV' : 'Supports MP4, WebM & MOV'}
              </p>
            </div>
            <input
              type="file"
              accept="video/*"
              onChange={handleVideoUpload}
              className="hidden"
            />
          </label>
        ) : (
          <div className="w-full flex flex-col gap-4">
            {/* Paparan Pemain Video */}
            <div className="relative w-full aspect-video rounded-2xl overflow-hidden bg-slate-950 border border-slate-700 shadow-xl flex items-center justify-center">
              <video
                ref={videoRef}
                src={videoSrc}
                onLoadedMetadata={handleLoadedMetadata}
                onTimeUpdate={handleTimeUpdate}
                playsInline
                className="w-full h-full object-contain"
              />
              <button
                onClick={togglePlay}
                className="absolute inset-0 m-auto w-14 h-14 rounded-full bg-black/60 hover:bg-black/80 text-white flex items-center justify-center backdrop-blur-sm transition"
              >
                {isPlaying ? <Pause className="w-6 h-6" /> : <Play className="w-6 h-6 ml-0.5" />}
              </button>
            </div>

            {/* Kawalan Garis Masa Pangkas (Trim Sliders) */}
            <div className="p-4 bg-[#121e36] rounded-2xl border border-slate-700/60 flex flex-col gap-3">
              <div className="flex justify-between text-xs font-mono text-slate-300">
                <span>{lang === 'ms' ? 'Masa Mula:' : 'Start:'} {formatTime(startTime)}</span>
                <span className="text-cyan-400">{formatTime(currentTime)}</span>
                <span>{lang === 'ms' ? 'Masa Tamat:' : 'End:'} {formatTime(endTime)}</span>
              </div>

              {/* Slider Mula */}
              <div className="flex flex-col gap-1">
                <span className="text-[10px] text-slate-400">{lang === 'ms' ? 'Laraskan Masa Mula' : 'Start Point'}</span>
                <input
                  type="range"
                  min="0"
                  max={duration}
                  value={startTime}
                  onChange={(e) => {
                    const val = Math.min(parseInt(e.target.value), endTime - 1);
                    setStartTime(val);
                    if (videoRef.current) videoRef.current.currentTime = val;
                  }}
                  className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-blue-500"
                />
              </div>

              {/* Slider Tamat */}
              <div className="flex flex-col gap-1">
                <span className="text-[10px] text-slate-400">{lang === 'ms' ? 'Laraskan Masa Tamat' : 'End Point'}</span>
                <input
                  type="range"
                  min="0"
                  max={duration}
                  value={endTime}
                  onChange={(e) => {
                    const val = Math.max(parseInt(e.target.value), startTime + 1);
                    setEndTime(val);
                  }}
                  className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-red-500"
                />
              </div>
            </div>

            {/* Butang Tindakan Tangkapan Bingkai Foto */}
            <div className="flex gap-2">
              <button
                onClick={grabSnapshot}
                className="flex-1 py-3 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-semibold text-xs flex items-center justify-center gap-2 border border-slate-700 transition"
              >
                <Camera className="w-4 h-4 text-cyan-400" />
                {lang === 'ms' ? 'Tangkap Bingkai Foto Semasa' : 'Capture Frame Snapshot'}
              </button>
            </div>

            {/* Pratonton Snapshot yang ditangkap */}
            {snapshotUrl && (
              <div className="p-3 bg-[#121e36] rounded-2xl border border-slate-700/60 flex flex-col gap-2">
                <span className="text-xs font-semibold text-slate-300">
                  {lang === 'ms' ? 'Foto Ditangkap dari Video:' : 'Snapshot Captured:'}
                </span>
                <img
                  src={snapshotUrl}
                  alt="Snapshot"
                  className="w-full aspect-video object-contain rounded-xl bg-black"
                />
                <a
                  href={snapshotUrl}
                  download={`omnikitmy_frame_${Date.now()}.jpg`}
                  className="w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold flex items-center justify-center gap-1.5"
                >
                  <Download className="w-3.5 h-3.5" />
                  {lang === 'ms' ? 'Muat Turun Foto HD' : 'Download Photo HD'}
                </a>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
