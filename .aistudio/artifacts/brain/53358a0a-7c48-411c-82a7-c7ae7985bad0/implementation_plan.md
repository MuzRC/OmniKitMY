# OmniKitMY - Kit Alatan Pintar Serba Boleh Android (Release v1.0)

OmniKitMY ialah sebuah aplikasi kit alatan utiliti pintar (Swiss Army Knife / All Tools) komprehensif, stabil dan berfungsi sepenuhnya khusus untuk peranti Android dan pelayar web moden di Malaysia. Aplikasi ini membawakan penjenamaan baharu beridentitikan ikon kotak alatan (*toolbox*) dengan gabungan warna kebangsaan (Merah, Biru, Kuning, Putih), bahasa utama Bahasa Melayu, seni bina modular berasingan fail bagi setiap alatan, keupayaan luar talian berterusan (offline-first PWA), mod gelap bergaya seperti tangkap layar rujukan, dan set ujian unit (unit tests) yang menjamin kestabilan pelepasan perisian.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Keputusan dan penyesuaian terkini yang disahkan oleh pengguna:

- **Nama Aplikasi Rasmi**: **OmniKitMY** (dikemas kini daripada Omikit atas permintaan pengguna).
- **Reka Bentuk Ikon & Identiti Jenama**: Ikon kotak alatan (toolbox) moden yang elegan dengan gandingan harmoni warna Jalur Gemilang: **Merah, Biru Diraja, Kuning Diraja, dan Putih Bersih**. Ikon ini akan dipaparkan pada header aplikasi, logo favicon, dan ikon aplikasi PWA Android.
- **Sokongan Alatan Tambahan**: Pengimbas dan Penjana Kod QR (**QR & Barcode Scanner & Generator**) berfungsi sepenuhnya dengan pengesanan kamera masa nyata dan perkongsian data.
- **Seni Bina Fail Berasingan**: Setiap satu alatan ditempatkan dalam fail modul tersendiri di bawah `src/tools/<ToolName>.tsx` lengkap dengan dokumentasi kod (JSDoc dan panduan penyenggaraan).
- **Kualiti Pelepasan Stabil (Bukan Demo)**: Menggunakan Web APIs peringkat pengeluaran: Web Audio API (gelombang frekuensi pembersih speaker sebenar & meter desibel), Canvas 2D (papan lukis & pemprosesan imej), DeviceOrientation/Motion (kompas & penimbang air), Web Crypto API (penyulitan fail/nota peti kebal AES-GCM), dan IndexedDB untuk storan tempatan luar talian.
- **Sokongan Bahasa**: Bahasa Melayu (lalai) dengan pilihan penukaran pantas ke Bahasa Inggeris.
- **Pengalaman Android & Mod Gelap**: Latar belakang navi gelap `#090e1c` dengan kad `#121e36`, penanda kegemaran, dan responsif sepenuhnya untuk semua saiz skrin telefon Android.

---

## 1. Overview & Core Concept

OmniKitMY membawakan himpunan alatan penting harian dalam satu aplikasi ringan, pantas, selamat dan boleh digunakan di mana-mana sahaja walaupun tanpa sambungan internet.

### Kumpulan Alatan Utama Berfungsi Sepenuhnya:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            OMNIKITMY TOOLKIT SUITE                          │
├──────────────────────┬──────────────────────┬───────────────────────────────┤
│ 🔊 Audio & Akustik   │ 📐 Sensor & Ukuran   │ 📷 Visual, QR & Dokumen       │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ • Pembersih Speaker  │ • Peringkat Air      │ • Pengimbas & Penjana QR      │
│   (Frekuensi 165Hz)  │   (Spirit Level)     │ • Pengimbas Metadata (EXIF)   │
│ • Penjana Nada Gel.  │ • Jangka Sudut       │ • Pengimbas Dokumen Kamera    │
│ • Meter Bunyi (dB)   │ • Pembaris Skrin     │ • Papan Lukisan (Paint Pad)   │
│ • Perakam Suara & Mic│ • Kompas Digital     │ • Pemotong & Pemeriksa Media  │
│ • Ujian Frekuensi    │ • Meter Cahaya (Lux) │ • Pembersih Privasi Foto      │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ 🔒 Keselamatan       │ 🔄 Penukar & Utiliti │ 🛠️ Alatan Pembangun & Sistem  │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ • Peti Kebal Rahsia  │ • Penukar Unit Serba │ • Penjana UUID & Hash Crypto  │
│   (AES-GCM Berenkrip)│ • Penukar Warna HEX  │ • Penguji Regex & Format JSON │
│ • Pengimbas Data     │ • Pengira Tarikh &   │ • Diagnostik Rangkaian & Ping │
│   Sensitif & Nota PIN│   Zon Waktu Malaysia │ • Pengira Bit & Kod Aksara    │
└──────────────────────┴──────────────────────┴───────────────────────────────┘
```

---

## 2. User Experience & Visual Design

### 2.1 Identiti Visual & Ikon Kotak Alatan (Toolbox)
- **Ikon Jenama Toolbox**: Kotak alatan bervektor tajam (SVG kualiti tinggi) yang menggabungkan:
  - Badan kotak alatan berwarna **Biru Laut Dalam / Biru Diraja** (`#1d4ed8` / `#1e40af`).
  - Pemegang dan perincian kemasan berwarna **Kuning Keemasan** (`#eab308` / `#facc15`).
  - Klip pengunci dan tanda aksen dinamik berwarna **Merah Semarak** (`#dc2626` / `#ef4444`).
  - Latar belakang dalaman dan sorotan kilauan berwarna **Putih Bersih** (`#ffffff`).
- **Skema Warna Antaramuka (Theme Palette)**:
  - **Latar Belakang Gelap (Lalai)**: `#090e1c` (Padat, jimat bateri skrin AMOLED Android).
  - **Permukaan Kad Alatan**: `#121e36` dengan sempadan halus `rgba(255, 255, 255, 0.08)`.
  - **Mod Cerah**: `#f8fafc` dengan kad putih berkontras tinggi dan bayang kemas.
  - **Aksen Interaktif**: Cyan `#06b6d4`, Kuning `#facc15`, dan Biru `#3b82f6`.

### 2.2 Susun Atur Skrin & Ergonomik
- **Top Bar OmniKitMY**: Memaparkan logo toolbox berwarna (merah/biru/kuning/putih), nama jenama **OmniKitMY**, bilangan alatan aktif, togol tema gelap/cerah, dan butang bahasa (BM / EN).
- **Bar Carian & Penapis Kategori**: Bar carian pantas masa nyata dengan butang kategori bertaburan (*Semua, Audio, Sensor, Ukuran, Visual, Keselamatan, Utiliti*).
- **Grid Alatan (2 Lajur Responsif)**: Kad interaktif dengan butang kegemaran (ikon hati), ikon alatan bulat berkod warna, tajuk dwibahasa dan deskripsi fungsi.
- **Paparan Alatan (Modal Skrin Penuh)**: Navigasi mesra ibu jari (thumb-zone) dengan butang kembali, butang tetapan/reset alatan, dan butang bantuan dokumentasi.

---

## 3. Key Product Decisions & Trade-Offs

### 3.1 Fungsi Pelepasan Sebenar (Zero Dummy Data)
- **Pembersih Speaker (Speaker Cleaner)**: Menghasilkan frekuensi resonans sebenar 165Hz termodulasi menggunakan Web Audio API `OscillatorNode` berselang-seli dengan frekuensi denyutan 400Hz–800Hz untuk menghasilkan gelombang fizikal yang menolak titisan air keluar dari gril pembesar suara telefon.
- **Pengimbas & Penjana Kod QR**: Menggunakan `BarcodeDetector` API pelayar asli disokong oleh pemprosesan kanvas pantas untuk membaca kod QR dari strim kamera langsung, serta penjanaan kod QR berdefinisi tinggi yang boleh dimuat turun.
- **Pengukur Bunyi (Sound Meter)**: Menerima input mikrofon peranti melalui `navigator.mediaDevices.getUserMedia`, mengira purata kuasa isyarat (RMS) melalui `AnalyserNode`, dan memaparkan tahap desibel (dB) dengan graf masa nyata.
- **Peti Kebal Peribadi (Private Vault)**: Perlindungan kata laluan/PIN dengan penyulitan AES-GCM (256-bit) menggunakan Web Crypto API. Fail dan nota disimpan dalam format tersulit di dalam IndexedDB peranti.

### 3.2 Struktur Kod Modular & Mudah Diselenggara
- Setiap alatan berada dalam fail kendiri di bawah `src/tools/` (contoh: `src/tools/SpeakerCleaner.tsx`, `src/tools/QRScannerTool.tsx`, dsb.).
- Setiap fail mengandungi dokumentasi terperinci di bahagian atas (tujuan alatan, input/output, kebenaran peranti yang diperlukan, dan cara penyenggaraan).

---

## 4. Technical Architecture & Data Strategy

### 4.1 Gambar Rajah Komponen Sistem

```
┌────────────────────────────────────────────────────────────────────────┐
│                        OMNIKITMY APPLICATION CORE                      │
├────────────────────────────────────────────────────────────────────────┤
│ App.tsx (Penyelaras Utama, Mod Gelap, Bahasa & Pemilih Alatan)        │
│   ├── components/Header.tsx (Logo Toolbox MY, Carian, Tema, Bahasa)    │
│   ├── components/CategoryBar.tsx (Penapis Kategori Alatan)             │
│   ├── components/ToolGrid.tsx (Grid Kad Alatan & Butang Kegemaran)     │
│   ├── components/ToolboxLogo.tsx (Ikon Kotak Alatan Merah/Biru/Kuning) │
│   └── components/OfflineStatus.tsx (Penunjuk Rangkaian & PWA)          │
├────────────────────────────────────────────────────────────────────────┤
│                     MODUL ALATAN INDIVIDU (src/tools/)                 │
├───────────────────────┬───────────────────────┬────────────────────────┤
│ QRScannerTool.tsx     │ SpeakerCleaner.tsx    │ SoundMeter.tsx         │
│ LevelerTool.tsx       │ ProtractorTool.tsx    │ RulerTool.tsx          │
│ CompassTool.tsx       │ ExifScannerTool.tsx   │ SecureVaultTool.tsx    │
│ UniversalConverter.tsx│ ToneGeneratorTool.tsx │ PaintTool.tsx          │
│ LightMeterTool.tsx    │ DeveloperTools.tsx    │ DocumentScanner.tsx    │
│ MediaTrimmerTool.tsx  │ NetworkInspector.tsx  │ PdfTools.tsx           │
├───────────────────────┴───────────────────────┴────────────────────────┤
│                 PERKHIDMATAN & STORAN TEMPATAN (src/services/)         │
│ • storage.ts (Pengurusan storan luar talian IndexedDB & LocalStorage)  │
│ • i18n.ts (Terjemahan lengkap Bahasa Melayu dan Bahasa Inggeris)       │
│ • cryptoUtils.ts (Enkripsi & Dekripsi AES-GCM dengan Web Crypto)       │
│ • audioEngine.ts (Sintesis frekuensi audio & penganalisis desibel)     │
└────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Pelan Ujian Unit (Unit Testing Suite)
- `tests/converter.test.ts`: Menguji ketepatan semua algoritma penukaran unit (panjang, berat, suhu, data, warna).
- `tests/crypto.test.ts`: Menguji integriti penyulitan dan penyahsulitan Peti Kebal berasaskan kata laluan.
- `tests/exif.test.ts`: Menguji keupayaan membaca dan membersihkan metadata imej untuk privasi.
- `tests/audio.test.ts`: Menguji pengiraan matematik frekuensi dan paras desibel.

---

## 5. Pelan Langkah Pelaksanaan (Execution Steps)

1. **Konfigurasi Awal & Penjenamaan**:
   - Kemas kini `metadata.json` dan `index.html` dengan nama **OmniKitMY** dan ikon toolbox khas (Merah, Biru, Kuning, Putih).
2. **Pakej & Dependensi**:
   - Pasang pakej yang diperlukan (cth: `qrcode`, `canvas-confetti`, dan jenis TypeScript berkaitan) sekali gus.
3. **Penyediaan Lapisan Teras (Core Layer)**:
   - `src/components/ToolboxLogo.tsx`: Reka bentuk SVG kotak alatan berwarna merah, biru, kuning, putih.
   - `src/services/i18n.ts`: Glosari dan teks dwibahasa Bahasa Melayu & Inggeris.
   - `src/services/storage.ts`: Modul IndexedDB & kegemaran alatan.
   - `src/services/cryptoUtils.ts`: Enjin keselamatan AES-GCM.
4. **Pembangunan Modul Alatan Berasingan (`src/tools/`)**:
   - Membina setiap alatan secara terperinci dengan kawalan penuh dan dokumentasi lengkap di setiap fail.
5. **Penggabungan Antaramuka Utama (`src/App.tsx` & Komponen)**:
   - Antaramuka kemas mengikut gaya tangkap layar Android, mod gelap/cerah, carian, dan kegemaran.
6. **Pelaksanaan Ujian Unit**:
   - Menyediakan suite ujian unit dalam folder `tests/` dan mengesahkan kestabilan aplikasi.
7. **Pengesahan & Kompilasi**:
   - Menjalankan `compile_applet` dan `lint_applet` sehingga sifar ralat.
