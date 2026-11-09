/**
 * @file src/services/i18n.ts
 * @description Modul pengantarabangsaan (i18n) untuk OmniKitMY.
 * Menyediakan kamus dwibahasa untuk Bahasa Melayu (lalai) dan Bahasa Inggeris.
 */

import { Language } from '../types/tool';

export const translations = {
  ms: {
    appName: 'OmniKitMY',
    appTagline: 'Kit Alatan Pintar Android Serba Boleh',
    searchPlaceholder: 'Cari nama alatan atau fungsi...',
    allTools: 'Semua Alatan',
    favorites: 'Kegemaran',
    noFavorites: 'Tiada alatan kegemaran disimpan lagi. Tekan ikon hati pada mana-mana alatan untuk simpan.',
    noResults: 'Tiada alatan dijumpai sepadan dengan carian anda.',
    backToTools: 'Kembali ke Senarai Alatan',
    categories: {
      all: 'Semua',
      audio: 'Audio & Bunyi',
      sensor: 'Sensor Peranti',
      measure: 'Ukuran & Sudut',
      media: 'Kamera & Visual',
      security: 'Keselamatan',
      utility: 'Utiliti Harian',
      developer: 'Alatan Pembangun',
    },
    offlineStatus: {
      online: 'Dalam Talian',
      offline: 'Mod Luar Talian (Data disimpan dalam peranti)',
      pwaReady: 'Sedia Digunakan Luar Talian',
    },
    actions: {
      start: 'Mula',
      stop: 'Berhenti',
      reset: 'Set Semula',
      copy: 'Salin',
      copied: 'Disalin!',
      download: 'Muat Turun',
      upload: 'Muat Naik',
      save: 'Simpan',
      delete: 'Padam',
      cancel: 'Batal',
      calibrate: 'Tentukur (Calibrate)',
      close: 'Tutup',
    },
    footerNote: 'OmniKitMY · Kit Alatan Pintar Luar Talian untuk Pengguna Android Malaysia',
  },
  en: {
    appName: 'OmniKitMY',
    appTagline: 'All-in-One Smart Android Utility Toolkit',
    searchPlaceholder: 'Search tools or features...',
    allTools: 'All Tools',
    favorites: 'Favorites',
    noFavorites: 'No favorites saved yet. Tap the heart icon on any tool card.',
    noResults: 'No tools found matching your search.',
    backToTools: 'Back to Tools List',
    categories: {
      all: 'All',
      audio: 'Audio & Sound',
      sensor: 'Device Sensors',
      measure: 'Measurement & Angle',
      media: 'Camera & Visual',
      security: 'Security & Vault',
      utility: 'Daily Utilities',
      developer: 'Developer Tools',
    },
    offlineStatus: {
      online: 'Online',
      offline: 'Offline Mode (Data stored locally)',
      pwaReady: 'Ready for Offline Use',
    },
    actions: {
      start: 'Start',
      stop: 'Stop',
      reset: 'Reset',
      copy: 'Copy',
      copied: 'Copied!',
      download: 'Download',
      upload: 'Upload',
      save: 'Save',
      delete: 'Delete',
      cancel: 'Cancel',
      calibrate: 'Calibrate',
      close: 'Close',
    },
    footerNote: 'OmniKitMY · Offline-Ready Smart Toolkit for Android Users',
  },
};

export function getTranslation(lang: Language) {
  return translations[lang] || translations.ms;
}
