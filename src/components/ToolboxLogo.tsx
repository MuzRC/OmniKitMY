/**
 * @file src/components/ToolboxLogo.tsx
 * @description Komponen Ikon Rasmi OmniKitMY - Kotak Alatan (Toolbox)
 * Menggabungkan warna Merah, Biru, Kuning, dan Putih mengikut kehendak pengguna.
 */

import React from 'react';

interface ToolboxLogoProps {
  className?: string;
  size?: number;
}

export const ToolboxLogo: React.FC<ToolboxLogoProps> = ({ className = '', size = 36 }) => {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`shrink-0 drop-shadow-md ${className}`}
      aria-label="OmniKitMY Toolbox Logo"
    >
      {/* Pemegang Kotak Alatan (Kuning Diraja) */}
      <path
        d="M32 30 V18 C32 11 40 8 50 8 C60 8 68 11 68 18 V30"
        stroke="#facc15"
        strokeWidth="6.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      
      {/* Sambungan Logam Pemegang (Putih) */}
      <rect x="29" y="27" width="6" height="5" rx="1.5" fill="#ffffff" />
      <rect x="65" y="27" width="6" height="5" rx="1.5" fill="#ffffff" />

      {/* Badan Utama Kotak Alatan (Biru Diraja) */}
      <rect
        x="12"
        y="30"
        width="76"
        height="60"
        rx="12"
        fill="#1d4ed8"
        stroke="#1e3a8a"
        strokeWidth="2.5"
      />

      {/* Jalur Kemasan Bahagian Atas (Merah Semarak) */}
      <path
        d="M12 40 C12 34.4772 16.4772 30 22 30 H78 C83.5228 30 88 34.4772 88 40 V48 H12 V40 Z"
        fill="#dc2626"
      />

      {/* Jalur Pembahagi Horizontal Berkilau (Kuning Keemasan) */}
      <rect x="12" y="47" width="76" height="3" fill="#facc15" />

      {/* Kancing / Kunci Tengah Kotak Alatan (Putih & Kuning) */}
      <rect
        x="42"
        y="42"
        width="16"
        height="14"
        rx="3.5"
        fill="#ffffff"
        stroke="#facc15"
        strokeWidth="2"
      />
      <circle cx="50" cy="49" r="2.5" fill="#dc2626" />

      {/* Lencana Alatan Bersilang di Dalam Kotak (Putih) */}
      <g opacity="0.95">
        {/* Sepana Putih */}
        <path
          d="M32 68 L42 78"
          stroke="#ffffff"
          strokeWidth="3.5"
          strokeLinecap="round"
        />
        <circle cx="31" cy="67" r="4" stroke="#ffffff" strokeWidth="2.5" fill="none" />
        
        {/* Pemutar Skru Putih bersilang */}
        <path
          d="M68 68 L58 78"
          stroke="#ffffff"
          strokeWidth="3.5"
          strokeLinecap="round"
        />
        <path
          d="M69 67 L73 63"
          stroke="#facc15"
          strokeWidth="3.5"
          strokeLinecap="round"
        />
      </g>

      {/* Bucu Pelindung Bawah (Kuning) */}
      <path d="M12 80 C12 85.5 16.5 90 22 90 H28 V80 H12 Z" fill="#facc15" />
      <path d="M88 80 C88 85.5 83.5 90 78 90 H72 V80 H88 Z" fill="#facc15" />
    </svg>
  );
};
