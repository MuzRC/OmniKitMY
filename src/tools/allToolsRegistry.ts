/**
 * @file src/tools/allToolsRegistry.ts
 * @description Pendaftaran Induk bagi Semua Alatan (Central Registry of All Tools) dalam OmniKitMY.
 * Mengaitkan metadata dwibahasa, ikon, warna tema kad dan komponen alatan berasingan.
 */

import React from 'react';
import { ToolMetadata } from '../types/tool';

// Import setiap komponen alatan berasingan
import { QRScannerTool } from './QRScannerTool';
import { SpeakerCleaner } from './SpeakerCleaner';
import { SoundMeter } from './SoundMeter';
import { LevelerTool } from './LevelerTool';
import { ProtractorTool } from './ProtractorTool';
import { RulerTool } from './RulerTool';
import { CompassTool } from './CompassTool';
import { ExifScannerTool } from './ExifScannerTool';
import { SecureVaultTool } from './SecureVaultTool';
import { UniversalConverter } from './UniversalConverter';
import { ToneGeneratorTool } from './ToneGeneratorTool';
import { PaintTool } from './PaintTool';
import { LightMeterTool } from './LightMeterTool';
import { DeveloperTools } from './DeveloperTools';
import { DocumentScanner } from './DocumentScanner';
import { NetworkInspector } from './NetworkInspector';
import { MetalDetectorTool } from './MetalDetectorTool';
import { EarSpyTool } from './EarSpyTool';
import { MediaTrimmerTool } from './MediaTrimmerTool';
import { PdfTools } from './PdfTools';

export interface ToolEntry extends ToolMetadata {
  component: React.ComponentType<{ lang: 'ms' | 'en'; onBack: () => void }>;
}

export const ALL_TOOLS: ToolEntry[] = [
  {
    id: 'qr-scanner',
    name: {
      ms: 'Pengimbas & Penjana QR',
      en: 'QR Scanner & Generator',
    },
    description: {
      ms: 'Imbas kod QR kamera langsung, tangkapan skrin & jana kod QR',
      en: 'Scan live camera QR, barcodes & generate custom QR codes',
    },
    category: 'media',
    iconName: 'QrCode',
    iconBgColor: 'bg-indigo-500/20',
    iconColor: 'text-indigo-400',
    keywords: ['qr', 'barcode', 'imbas', 'scan', 'camera', 'kamera'],
    requiresCamera: true,
    badge: 'HOT',
    component: QRScannerTool,
  },
  {
    id: 'speaker-cleaner',
    name: {
      ms: 'Pembersih Pembesar Suara',
      en: 'Speaker Cleaner',
    },
    description: {
      ms: 'Keluarkan air & habuk dari speaker dengan gelombang akustik 165Hz',
      en: 'Eject water & dust from your speaker using 165Hz sound waves',
    },
    category: 'audio',
    iconName: 'Droplets',
    iconBgColor: 'bg-cyan-500/20',
    iconColor: 'text-cyan-400',
    keywords: ['speaker', 'air', 'water', 'eject', 'audio', 'bunyi', 'habuk'],
    badge: 'POPULAR',
    component: SpeakerCleaner,
  },
  {
    id: 'sound-meter',
    name: {
      ms: 'Pengukur Bunyi (dB)',
      en: 'Sound Meter',
    },
    description: {
      ms: 'Ukur tahap hingar persekitaran dalam desibel dengan mikrofon',
      en: 'Measure noise level in decibels using your device microphone',
    },
    category: 'audio',
    iconName: 'Mic',
    iconBgColor: 'bg-blue-500/20',
    iconColor: 'text-blue-400',
    keywords: ['sound', 'bunyi', 'decibel', 'db', 'hingar', 'noise', 'mic'],
    requiresMic: true,
    component: SoundMeter,
  },
  {
    id: 'leveler',
    name: {
      ms: 'Peringkat Air (Spirit Level)',
      en: 'Leveler',
    },
    description: {
      ms: 'Ukur kerataan permukaan & kecondongan sudut dengan penderia graviti',
      en: 'Spirit level & slope angle finder using device orientation',
    },
    category: 'measure',
    iconName: 'Compass',
    iconBgColor: 'bg-emerald-500/20',
    iconColor: 'text-emerald-400',
    keywords: ['level', 'waterpas', 'kerataan', 'spirit', 'angle', 'sudut'],
    isSensorBased: true,
    component: LevelerTool,
  },
  {
    id: 'universal-converter',
    name: {
      ms: 'Penukar Universal',
      en: 'Universal Converter',
    },
    description: {
      ms: 'Tukar panjang, berat, suhu, data, warna & format kod',
      en: 'Convert length, weight, temperature, data storage, color & codes',
    },
    category: 'utility',
    iconName: 'ArrowRightLeft',
    iconBgColor: 'bg-cyan-500/20',
    iconColor: 'text-cyan-400',
    keywords: ['converter', 'penukar', 'unit', 'suhu', 'panjang', 'data', 'color'],
    component: UniversalConverter,
  },
  {
    id: 'secure-vault',
    name: {
      ms: 'Peti Kebal Peribadi',
      en: 'Private Vault',
    },
    description: {
      ms: 'Simpan nota sulit & kata laluan dengan enkripsi AES-GCM 256',
      en: 'PIN-protected encrypted storage for private notes & passwords',
    },
    category: 'security',
    iconName: 'Lock',
    iconBgColor: 'bg-purple-500/20',
    iconColor: 'text-purple-400',
    keywords: ['vault', 'peti kebal', 'rahsia', 'password', 'encrypt', 'security'],
    badge: 'AES-256',
    component: SecureVaultTool,
  },
  {
    id: 'protractor',
    name: {
      ms: 'Jangka Sudut (Protractor)',
      en: 'Protractor',
    },
    description: {
      ms: 'Sukat darjah sudut pada skrin atau melalui pandangan kamera',
      en: 'Measure angles on screen or through live camera viewfinder',
    },
    category: 'measure',
    iconName: 'Ruler',
    iconBgColor: 'bg-purple-500/20',
    iconColor: 'text-purple-400',
    keywords: ['protractor', 'sudut', 'jangka sudut', 'angle', 'degree'],
    component: ProtractorTool,
  },
  {
    id: 'ruler',
    name: {
      ms: 'Pembaris Skrin',
      en: 'Screen Ruler',
    },
    description: {
      ms: 'Ukur objek kecil pada skrin dalam sentimeter (cm) dan inci (in)',
      en: 'Measure small physical objects on screen in cm and inches',
    },
    category: 'measure',
    iconName: 'Ruler',
    iconBgColor: 'bg-teal-500/20',
    iconColor: 'text-teal-400',
    keywords: ['ruler', 'pembaris', 'ukur', 'cm', 'inch', 'caliper'],
    component: RulerTool,
  },
  {
    id: 'compass',
    name: {
      ms: 'Kompas Digital',
      en: 'Digital Compass',
    },
    description: {
      ms: 'Semak arah mata angin & orientasi kompas magnetik peranti',
      en: 'Check direction and magnetic orientation in real-time',
    },
    category: 'sensor',
    iconName: 'Navigation',
    iconBgColor: 'bg-blue-500/20',
    iconColor: 'text-blue-400',
    keywords: ['compass', 'kompas', 'kiblat', 'utara', 'direction'],
    isSensorBased: true,
    component: CompassTool,
  },
  {
    id: 'exif-scanner',
    name: {
      ms: 'Pengimbas Metadata Foto',
      en: 'Photo Metadata Scanner',
    },
    description: {
      ms: 'Kesan koordinat GPS, tarikh & bersihkan metadata untuk privasi',
      en: 'Find hidden GPS, date, device info & strip metadata for privacy',
    },
    category: 'media',
    iconName: 'Image',
    iconBgColor: 'bg-indigo-500/20',
    iconColor: 'text-indigo-400',
    keywords: ['exif', 'metadata', 'photo', 'gps', 'privasi', 'privacy'],
    component: ExifScannerTool,
  },
  {
    id: 'tone-generator',
    name: {
      ms: 'Penjana Nada (Tone Generator)',
      en: 'Tone Generator',
    },
    description: {
      ms: 'Jana frekuensi nada audio daripada 20 Hz hingga 20,000 Hz',
      en: 'Generate clean audio frequencies and test waveforms',
    },
    category: 'audio',
    iconName: 'Activity',
    iconBgColor: 'bg-pink-500/20',
    iconColor: 'text-pink-400',
    keywords: ['tone', 'nada', 'frekuensi', 'frequency', 'audio', 'synth'],
    component: ToneGeneratorTool,
  },
  {
    id: 'paint-pad',
    name: {
      ms: 'Papan Lukis & Lakaran',
      en: 'Paint & Sketch',
    },
    description: {
      ms: 'Conteng nota, lakar lukisan & simpan fail imej PNG ke telefon',
      en: 'Sketch and doodle on a blank canvas, export as PNG',
    },
    category: 'media',
    iconName: 'Paintbrush',
    iconBgColor: 'bg-amber-500/20',
    iconColor: 'text-amber-400',
    keywords: ['paint', 'lukis', 'lakar', 'sketch', 'doodle', 'kanvas'],
    component: PaintTool,
  },
  {
    id: 'light-meter',
    name: {
      ms: 'Meter Cahaya (Lux)',
      en: 'Light Meter',
    },
    description: {
      ms: 'Ukur keamatan cahaya persekitaran dalam unit Lux dengan sensor/kamera',
      en: 'Measure ambient illumination in Lux with sensor or camera',
    },
    category: 'sensor',
    iconName: 'Sun',
    iconBgColor: 'bg-yellow-500/20',
    iconColor: 'text-yellow-400',
    keywords: ['light', 'cahaya', 'lux', 'kecerahan', 'illuminance', 'sun'],
    isSensorBased: true,
    component: LightMeterTool,
  },
  {
    id: 'developer-tools',
    name: {
      ms: 'Alatan Pembangun (DevTools)',
      en: 'Developer Tools',
    },
    description: {
      ms: 'UUID, hash SHA-256/512, format JSON, penguji Regex & Base64',
      en: 'UUID, SHA-256/512 hashes, JSON formatter, Regex & Base64 tool',
    },
    category: 'developer',
    iconName: 'Code',
    iconBgColor: 'bg-indigo-500/20',
    iconColor: 'text-indigo-400',
    keywords: ['dev', 'developer', 'uuid', 'hash', 'json', 'regex', 'timestamp', 'base64', 'encode', 'decode', 'b64'],
    component: DeveloperTools,
  },
  {
    id: 'document-scanner',
    name: {
      ms: 'Pengimbas Dokumen',
      en: 'Document Scanner',
    },
    description: {
      ms: 'Imbas dokumen fizikal dengan penapis kontras tinggi B&W',
      en: 'Scan paper documents with edge guide & contrast filter',
    },
    category: 'media',
    iconName: 'FileText',
    iconBgColor: 'bg-rose-500/20',
    iconColor: 'text-rose-400',
    keywords: ['document', 'dokumen', 'scan', 'imbas', 'pdf', 'resit'],
    requiresCamera: true,
    component: DocumentScanner,
  },
  {
    id: 'network-inspector',
    name: {
      ms: 'Pemeriksa Rangkaian',
      en: 'Network Inspector',
    },
    description: {
      ms: 'Uji latensi ping rangkaian, kelajuan downlink & status sambungan',
      en: 'Test ping latency, downlink speed & network connection status',
    },
    category: 'utility',
    iconName: 'Wifi',
    iconBgColor: 'bg-sky-500/20',
    iconColor: 'text-sky-400',
    keywords: ['network', 'rangkaian', 'ping', 'wifi', 'internet', 'kelajuan'],
    component: NetworkInspector,
  },
  {
    id: 'metal-detector',
    name: {
      ms: 'Pengesan Logam (Metal Detector)',
      en: 'Metal Detector',
    },
    description: {
      ms: 'Kesan logam dan medan magnet (μT) dengan bunyi amaran berirama',
      en: 'Detect metals & magnets nearby using device magnetometer',
    },
    category: 'sensor',
    iconName: 'Radio',
    iconBgColor: 'bg-orange-500/20',
    iconColor: 'text-orange-400',
    keywords: ['metal', 'logam', 'besi', 'magnet', 'magnetometer', 'fluks'],
    isSensorBased: true,
    component: MetalDetectorTool,
  },
  {
    id: 'ear-spy',
    name: {
      ms: 'Perisik Telinga (Ear Spy)',
      en: 'Ear Spy Amplifier',
    },
    description: {
      ms: 'Kuatkan pendengaran suara dan perbualan melalui fon kepala',
      en: 'Amplify ambient sounds through your headphones with bandpass',
    },
    category: 'audio',
    iconName: 'Headphones',
    iconBgColor: 'bg-purple-500/20',
    iconColor: 'text-purple-400',
    keywords: ['ear', 'spy', 'telinga', 'dengar', 'amplifier', 'suara'],
    requiresMic: true,
    component: EarSpyTool,
  },
  {
    id: 'media-trimmer',
    name: {
      ms: 'Pemotong Video & Tangkapan Bingkai',
      en: 'Video Trimmer',
    },
    description: {
      ms: 'Pangkas video & tangkap foto kualiti tinggi tanpa muat naik awan',
      en: 'Trim videos and grab high-res snapshot frames locally',
    },
    category: 'media',
    iconName: 'Scissors',
    iconBgColor: 'bg-red-500/20',
    iconColor: 'text-red-400',
    keywords: ['video', 'trim', 'potong', 'pangkas', 'frame', 'snapshot'],
    component: MediaTrimmerTool,
  },
  {
    id: 'pdf-tools',
    name: {
      ms: 'Alatan Resit & Dokumen PDF',
      en: 'PDF & Receipt Tools',
    },
    description: {
      ms: 'Jana invois, resit rasmi Malaysia & cetak ke PDF secara luar talian',
      en: 'Create formal receipts, invoices & print to PDF offline',
    },
    category: 'utility',
    iconName: 'Printer',
    iconBgColor: 'bg-emerald-500/20',
    iconColor: 'text-emerald-400',
    keywords: ['pdf', 'resit', 'receipt', 'invoice', 'invois', 'cetak', 'print'],
    component: PdfTools,
  },
];
