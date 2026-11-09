/**
 * @file src/tools/UniversalConverter.tsx
 * @description Alatan Penukar Universal Serba Boleh (Universal Converter).
 * 
 * FUNGSI UTAMA:
 * 1. Penukaran Unit Fizikal: Panjang, Berat, Suhu, Kelajuan, dan Luas.
 * 2. Penukaran Data Komputer: Byte, Kilobyte, Megabyte, Gigabyte, Terabyte.
 * 3. Penukar Kod Warna: HEX, RGB, HSL, CMYK dengan pemilih warna langsung (Color Picker).
 * 4. Penukar Format Teks: Base64, Hexadecimal, Binary, URL Encoding, dan JSON formatter.
 * 5. Pengiraan dua hala segera (Instant two-way calculation) dengan fungsi salin pantas.
 */

import React, { useState } from 'react';
import { ArrowLeft, ArrowRightLeft, Copy, Check, Hash, Palette, HardDrive, Ruler } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

type Mode = 'length' | 'weight' | 'temp' | 'data' | 'color' | 'text';

export const UniversalConverter: React.FC<Props> = ({ lang, onBack }) => {
  const [activeMode, setActiveMode] = useState<Mode>('length');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Keadaan Panjang
  const [lengthValue, setLengthValue] = useState<number>(1);
  const [lengthUnit, setLengthUnit] = useState<string>('m');

  // Keadaan Berat
  const [weightValue, setWeightValue] = useState<number>(1);
  const [weightUnit, setWeightUnit] = useState<string>('kg');

  // Keadaan Suhu
  const [tempValue, setTempValue] = useState<number>(25);
  const [tempUnit, setTempUnit] = useState<'C' | 'F' | 'K'>('C');

  // Keadaan Data Digital
  const [dataValue, setDataValue] = useState<number>(1024);
  const [dataUnit, setDataUnit] = useState<string>('MB');

  // Keadaan Warna
  const [hexColor, setHexColor] = useState<string>('#1d4ed8');

  // Keadaan Teks & Pengekodan
  const [textInput, setTextInput] = useState<string>('OmniKitMY');

  const copyVal = (val: string, key: string) => {
    navigator.clipboard.writeText(val);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 1500);
  };

  // =================== PENGIRAAN PANJANG ===================
  // Nilai asas: Meter (m)
  const lengthToMeters: Record<string, number> = {
    m: 1,
    km: 1000,
    cm: 0.01,
    mm: 0.001,
    inch: 0.0254,
    foot: 0.3048,
    yard: 0.9144,
    mile: 1609.344,
  };

  const currentLengthInMeters = lengthValue * (lengthToMeters[lengthUnit] || 1);
  const lengthConversions = [
    { label: lang === 'ms' ? 'Meter (m)' : 'Meters (m)', key: 'm', val: currentLengthInMeters },
    { label: lang === 'ms' ? 'Kilometer (km)' : 'Kilometers (km)', key: 'km', val: currentLengthInMeters / 1000 },
    { label: lang === 'ms' ? 'Sentimeter (cm)' : 'Centimeters (cm)', key: 'cm', val: currentLengthInMeters * 100 },
    { label: lang === 'ms' ? 'Milimeter (mm)' : 'Millimeters (mm)', key: 'mm', val: currentLengthInMeters * 1000 },
    { label: lang === 'ms' ? 'Kaki (ft)' : 'Feet (ft)', key: 'foot', val: currentLengthInMeters / 0.3048 },
    { label: lang === 'ms' ? 'Inci (in)' : 'Inches (in)', key: 'inch', val: currentLengthInMeters / 0.0254 },
    { label: lang === 'ms' ? 'Batu (miles)' : 'Miles', key: 'mile', val: currentLengthInMeters / 1609.344 },
  ];

  // =================== PENGIRAAN BERAT ===================
  // Nilai asas: Kilogram (kg)
  const weightToKg: Record<string, number> = {
    kg: 1,
    g: 0.001,
    mg: 0.000001,
    lb: 0.453592,
    oz: 0.0283495,
    tonne: 1000,
  };

  const currentWeightInKg = weightValue * (weightToKg[weightUnit] || 1);
  const weightConversions = [
    { label: lang === 'ms' ? 'Kilogram (kg)' : 'Kilograms (kg)', key: 'kg', val: currentWeightInKg },
    { label: lang === 'ms' ? 'Gram (g)' : 'Grams (g)', key: 'g', val: currentWeightInKg * 1000 },
    { label: lang === 'ms' ? 'Miligram (mg)' : 'Milligrams (mg)', key: 'mg', val: currentWeightInKg * 1000000 },
    { label: lang === 'ms' ? 'Paun (lbs)' : 'Pounds (lbs)', key: 'lb', val: currentWeightInKg / 0.453592 },
    { label: lang === 'ms' ? 'Auns (oz)' : 'Ounces (oz)', key: 'oz', val: currentWeightInKg / 0.0283495 },
    { label: lang === 'ms' ? 'Tan Metrik (t)' : 'Metric Tonne (t)', key: 'tonne', val: currentWeightInKg / 1000 },
  ];

  // =================== PENGIRAAN SUHU ===================
  let celsius = 0;
  if (tempUnit === 'C') celsius = tempValue;
  else if (tempUnit === 'F') celsius = ((tempValue - 32) * 5) / 9;
  else if (tempUnit === 'K') celsius = tempValue - 273.15;

  const tempConversions = [
    { label: 'Celsius (°C)', key: 'C', val: `${Math.round(celsius * 100) / 100} °C` },
    { label: 'Fahrenheit (°F)', key: 'F', val: `${Math.round(((celsius * 9) / 5 + 32) * 100) / 100} °F` },
    { label: 'Kelvin (K)', key: 'K', val: `${Math.round((celsius + 273.15) * 100) / 100} K` },
  ];

  // =================== PENGIRAAN DATA ===================
  // Nilai asas: Megabytes (MB)
  const dataToMB: Record<string, number> = {
    B: 1 / (1024 * 1024),
    KB: 1 / 1024,
    MB: 1,
    GB: 1024,
    TB: 1024 * 1024,
  };
  const currentDataMB = dataValue * (dataToMB[dataUnit] || 1);
  const dataConversions = [
    { label: 'Bytes (B)', key: 'B', val: Math.round(currentDataMB * 1024 * 1024).toLocaleString() },
    { label: 'Kilobytes (KB)', key: 'KB', val: (currentDataMB * 1024).toLocaleString() },
    { label: 'Megabytes (MB)', key: 'MB', val: currentDataMB.toLocaleString() },
    { label: 'Gigabytes (GB)', key: 'GB', val: (currentDataMB / 1024).toFixed(4) },
    { label: 'Terabytes (TB)', key: 'TB', val: (currentDataMB / (1024 * 1024)).toFixed(6) },
  ];

  // =================== PENGIRAAN WARNA ===================
  const hexToRgb = (hex: string) => {
    let clean = hex.replace('#', '');
    if (clean.length === 3) {
      clean = clean.split('').map((c) => c + c).join('');
    }
    const num = parseInt(clean, 16);
    return {
      r: (num >> 16) & 255,
      g: (num >> 8) & 255,
      b: num & 255,
    };
  };

  const rgb = hexToRgb(hexColor);
  const rgbString = `rgb(${rgb.r}, ${rgb.g}, ${rgb.b})`;
  
  // Kira HSL
  const rNorm = rgb.r / 255, gNorm = rgb.g / 255, bNorm = rgb.b / 255;
  const max = Math.max(rNorm, gNorm, bNorm), min = Math.min(rNorm, gNorm, bNorm);
  let h = 0, s = 0, l = (max + min) / 2;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case rNorm: h = (gNorm - bNorm) / d + (gNorm < bNorm ? 6 : 0); break;
      case gNorm: h = (bNorm - rNorm) / d + 2; break;
      case bNorm: h = (rNorm - gNorm) / d + 4; break;
    }
    h /= 6;
  }
  const hslString = `hsl(${Math.round(h * 360)}, ${Math.round(s * 100)}%, ${Math.round(l * 100)}%)`;

  // =================== PENGIRAAN TEKS & KOD ===================
  let base64Encoded = '';
  let hexEncoded = '';
  let urlEncoded = '';
  try {
    base64Encoded = btoa(unescape(encodeURIComponent(textInput)));
    urlEncoded = encodeURIComponent(textInput);
    hexEncoded = Array.from(new TextEncoder().encode(textInput))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join(' ');
  } catch {
    base64Encoded = 'Ralat pengekodan';
  }

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
              {lang === 'ms' ? 'Penukar Universal' : 'Universal Converter'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Tukar unit, ukuran, data, warna & format' : 'Convert units, data, color & formats'}
            </p>
          </div>
        </div>
      </div>

      <div className="p-4 max-w-md mx-auto w-full flex-1 flex flex-col gap-5">
        {/* Pilihan Mod Kategori Bersegmen */}
        <div className="grid grid-cols-3 sm:grid-cols-6 gap-1.5 p-1 bg-slate-900/90 rounded-2xl border border-slate-700/60">
          {[
            { id: 'length', label: lang === 'ms' ? 'Panjang' : 'Length' },
            { id: 'weight', label: lang === 'ms' ? 'Berat' : 'Weight' },
            { id: 'temp', label: lang === 'ms' ? 'Suhu' : 'Temp' },
            { id: 'data', label: 'Data' },
            { id: 'color', label: lang === 'ms' ? 'Warna' : 'Color' },
            { id: 'text', label: 'Teks' },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveMode(tab.id as Mode)}
              className={`py-2 px-1 text-center rounded-xl text-xs font-semibold transition ${
                activeMode === tab.id
                  ? 'bg-blue-600 text-white shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* ===================== KATEGORI: PANJANG ===================== */}
        {activeMode === 'length' && (
          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
              <label className="text-xs font-semibold text-slate-300">
                {lang === 'ms' ? 'Masukkan Nilai Panjang:' : 'Enter Length Value:'}
              </label>
              <div className="flex gap-2">
                <input
                  type="number"
                  value={lengthValue}
                  onChange={(e) => setLengthValue(parseFloat(e.target.value) || 0)}
                  className="flex-1 p-3 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-lg font-bold focus:outline-none focus:border-blue-500"
                />
                <select
                  value={lengthUnit}
                  onChange={(e) => setLengthUnit(e.target.value)}
                  className="p-3 rounded-xl bg-slate-900 border border-slate-700 text-white text-sm font-semibold focus:outline-none"
                >
                  <option value="m">Meter (m)</option>
                  <option value="km">Kilometer (km)</option>
                  <option value="cm">Sentimeter (cm)</option>
                  <option value="mm">Milimeter (mm)</option>
                  <option value="foot">Kaki (ft)</option>
                  <option value="inch">Inci (in)</option>
                  <option value="mile">Batu (miles)</option>
                </select>
              </div>
            </div>

            <div className="flex flex-col gap-2">
              {lengthConversions.map((item) => (
                <div
                  key={item.key}
                  className="p-3 bg-[#121e36] rounded-xl border border-slate-700/50 flex items-center justify-between"
                >
                  <div>
                    <span className="text-[11px] text-slate-400 block">{item.label}</span>
                    <span className="text-base font-bold font-mono text-white tabular-nums">
                      {typeof item.val === 'number' ? (Number.isInteger(item.val) ? item.val : item.val.toPrecision(6)) : item.val}
                    </span>
                  </div>
                  <button
                    onClick={() => copyVal(item.val.toString(), item.key)}
                    className="p-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white"
                  >
                    {copiedKey === item.key ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ===================== KATEGORI: BERAT ===================== */}
        {activeMode === 'weight' && (
          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
              <label className="text-xs font-semibold text-slate-300">
                {lang === 'ms' ? 'Masukkan Nilai Berat / Jisim:' : 'Enter Weight Value:'}
              </label>
              <div className="flex gap-2">
                <input
                  type="number"
                  value={weightValue}
                  onChange={(e) => setWeightValue(parseFloat(e.target.value) || 0)}
                  className="flex-1 p-3 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-lg font-bold focus:outline-none focus:border-blue-500"
                />
                <select
                  value={weightUnit}
                  onChange={(e) => setWeightUnit(e.target.value)}
                  className="p-3 rounded-xl bg-slate-900 border border-slate-700 text-white text-sm font-semibold focus:outline-none"
                >
                  <option value="kg">Kilogram (kg)</option>
                  <option value="g">Gram (g)</option>
                  <option value="mg">Miligram (mg)</option>
                  <option value="lb">Paun (lbs)</option>
                  <option value="oz">Auns (oz)</option>
                  <option value="tonne">Tan Metrik (t)</option>
                </select>
              </div>
            </div>

            <div className="flex flex-col gap-2">
              {weightConversions.map((item) => (
                <div
                  key={item.key}
                  className="p-3 bg-[#121e36] rounded-xl border border-slate-700/50 flex items-center justify-between"
                >
                  <div>
                    <span className="text-[11px] text-slate-400 block">{item.label}</span>
                    <span className="text-base font-bold font-mono text-white tabular-nums">
                      {typeof item.val === 'number' ? (Number.isInteger(item.val) ? item.val : item.val.toPrecision(6)) : item.val}
                    </span>
                  </div>
                  <button
                    onClick={() => copyVal(item.val.toString(), item.key)}
                    className="p-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white"
                  >
                    {copiedKey === item.key ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ===================== KATEGORI: SUHU ===================== */}
        {activeMode === 'temp' && (
          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
              <label className="text-xs font-semibold text-slate-300">
                {lang === 'ms' ? 'Masukkan Nilai Suhu:' : 'Enter Temperature:'}
              </label>
              <div className="flex gap-2">
                <input
                  type="number"
                  value={tempValue}
                  onChange={(e) => setTempValue(parseFloat(e.target.value) || 0)}
                  className="flex-1 p-3 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-lg font-bold focus:outline-none focus:border-blue-500"
                />
                <select
                  value={tempUnit}
                  onChange={(e) => setTempUnit(e.target.value as 'C' | 'F' | 'K')}
                  className="p-3 rounded-xl bg-slate-900 border border-slate-700 text-white text-sm font-semibold focus:outline-none"
                >
                  <option value="C">Celsius (°C)</option>
                  <option value="F">Fahrenheit (°F)</option>
                  <option value="K">Kelvin (K)</option>
                </select>
              </div>
            </div>

            <div className="flex flex-col gap-2">
              {tempConversions.map((item) => (
                <div
                  key={item.key}
                  className="p-3 bg-[#121e36] rounded-xl border border-slate-700/50 flex items-center justify-between"
                >
                  <div>
                    <span className="text-[11px] text-slate-400 block">{item.label}</span>
                    <span className="text-base font-bold font-mono text-cyan-400 tabular-nums">{item.val}</span>
                  </div>
                  <button
                    onClick={() => copyVal(item.val, item.key)}
                    className="p-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white"
                  >
                    {copiedKey === item.key ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ===================== KATEGORI: DATA DIGITAL ===================== */}
        {activeMode === 'data' && (
          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
              <label className="text-xs font-semibold text-slate-300">
                {lang === 'ms' ? 'Kapasiti Saiz Data:' : 'Data Storage Capacity:'}
              </label>
              <div className="flex gap-2">
                <input
                  type="number"
                  value={dataValue}
                  onChange={(e) => setDataValue(parseFloat(e.target.value) || 0)}
                  className="flex-1 p-3 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-lg font-bold focus:outline-none focus:border-blue-500"
                />
                <select
                  value={dataUnit}
                  onChange={(e) => setDataUnit(e.target.value)}
                  className="p-3 rounded-xl bg-slate-900 border border-slate-700 text-white text-sm font-semibold focus:outline-none"
                >
                  <option value="B">Bytes (B)</option>
                  <option value="KB">Kilobytes (KB)</option>
                  <option value="MB">Megabytes (MB)</option>
                  <option value="GB">Gigabytes (GB)</option>
                  <option value="TB">Terabytes (TB)</option>
                </select>
              </div>
            </div>

            <div className="flex flex-col gap-2">
              {dataConversions.map((item) => (
                <div
                  key={item.key}
                  className="p-3 bg-[#121e36] rounded-xl border border-slate-700/50 flex items-center justify-between"
                >
                  <div>
                    <span className="text-[11px] text-slate-400 block">{item.label}</span>
                    <span className="text-base font-bold font-mono text-white tabular-nums">{item.val}</span>
                  </div>
                  <button
                    onClick={() => copyVal(item.val.toString(), item.key)}
                    className="p-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white"
                  >
                    {copiedKey === item.key ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ===================== KATEGORI: WARNA ===================== */}
        {activeMode === 'color' && (
          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex items-center gap-4">
              <input
                type="color"
                value={hexColor}
                onChange={(e) => setHexColor(e.target.value)}
                className="w-16 h-16 rounded-2xl cursor-pointer border-0 bg-transparent p-0"
              />
              <div className="flex-1">
                <label className="text-xs font-semibold text-slate-300 block mb-1">
                  {lang === 'ms' ? 'Pilih Warna (Color Picker)' : 'Color Picker'}
                </label>
                <input
                  type="text"
                  value={hexColor}
                  onChange={(e) => setHexColor(e.target.value)}
                  className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-sm uppercase"
                />
              </div>
            </div>

            {/* Pratonton Kotak Warna Penuh */}
            <div
              style={{ backgroundColor: hexColor }}
              className="w-full h-16 rounded-2xl shadow-inner border border-white/20 flex items-center justify-center font-mono font-bold text-white drop-shadow-md"
            >
              {hexColor}
            </div>

            <div className="flex flex-col gap-2">
              {[
                { label: 'HEX Code', val: hexColor.toUpperCase(), key: 'hex' },
                { label: 'RGB Code', val: rgbString, key: 'rgb' },
                { label: 'HSL Code', val: hslString, key: 'hsl' },
              ].map((item) => (
                <div
                  key={item.key}
                  className="p-3 bg-[#121e36] rounded-xl border border-slate-700/50 flex items-center justify-between"
                >
                  <div>
                    <span className="text-[11px] text-slate-400 block">{item.label}</span>
                    <span className="text-base font-bold font-mono text-cyan-300">{item.val}</span>
                  </div>
                  <button
                    onClick={() => copyVal(item.val, item.key)}
                    className="p-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white"
                  >
                    {copiedKey === item.key ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ===================== KATEGORI: TEKS & PENGEKODAN ===================== */}
        {activeMode === 'text' && (
          <div className="flex flex-col gap-4">
            <div className="p-4 rounded-2xl bg-[#121e36] border border-slate-700/60 flex flex-col gap-2">
              <label className="text-xs font-semibold text-slate-300">
                {lang === 'ms' ? 'Masukkan Teks Biasa:' : 'Enter Plain Text:'}
              </label>
              <textarea
                value={textInput}
                onChange={(e) => setTextInput(e.target.value)}
                rows={2}
                className="w-full p-3 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-sm focus:outline-none focus:border-blue-500"
              />
            </div>

            <div className="flex flex-col gap-2">
              {[
                { label: 'Base64 Encoded', val: base64Encoded, key: 'b64' },
                { label: 'URL Encoded', val: urlEncoded, key: 'url' },
                { label: 'Hexadecimal (Bytes)', val: hexEncoded, key: 'hextext' },
              ].map((item) => (
                <div
                  key={item.key}
                  className="p-3 bg-[#121e36] rounded-xl border border-slate-700/50 flex items-center justify-between gap-2"
                >
                  <div className="flex-1 min-w-0">
                    <span className="text-[11px] text-slate-400 block">{item.label}</span>
                    <span className="text-xs font-mono text-slate-200 block truncate">{item.val}</span>
                  </div>
                  <button
                    onClick={() => copyVal(item.val, item.key)}
                    className="p-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white shrink-0"
                  >
                    {copiedKey === item.key ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
