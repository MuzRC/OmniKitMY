/**
 * @file src/components/ToolCard.tsx
 * @description Komponen Kad Alatan Individu (Android Tool Card).
 * Direka bentuk menyerupai gaya tangkap layar aplikasi utiliti Android sebenar:
 * Kad berkontur bulat, lencana ikon berpusat, butang kegemaran hati, dan tajuk dwibahasa.
 */

import React from 'react';
import {
  QrCode,
  Droplets,
  Mic,
  Compass,
  ArrowRightLeft,
  Lock,
  Ruler,
  Navigation,
  Image,
  Activity,
  Paintbrush,
  Sun,
  Code,
  FileText,
  Wifi,
  Radio,
  Headphones,
  Scissors,
  Printer,
  Heart,
} from 'lucide-react';
import { ToolEntry } from '../tools/allToolsRegistry';
import { Language } from '../types/tool';

interface ToolCardProps {
  tool: ToolEntry;
  lang: Language;
  isFavorite: boolean;
  onToggleFavorite: (e: React.MouseEvent) => void;
  onClick: () => void;
}

export const ToolCard: React.FC<ToolCardProps> = ({
  tool,
  lang,
  isFavorite,
  onToggleFavorite,
  onClick,
}) => {
  // Peta Ikon Dinamik
  const renderIcon = () => {
    const iconProps = { className: `w-7 h-7 ${tool.iconColor}` };
    switch (tool.iconName) {
      case 'QrCode': return <QrCode {...iconProps} />;
      case 'Droplets': return <Droplets {...iconProps} />;
      case 'Mic': return <Mic {...iconProps} />;
      case 'Compass': return <Compass {...iconProps} />;
      case 'ArrowRightLeft': return <ArrowRightLeft {...iconProps} />;
      case 'Lock': return <Lock {...iconProps} />;
      case 'Ruler': return <Ruler {...iconProps} />;
      case 'Navigation': return <Navigation {...iconProps} />;
      case 'Image': return <Image {...iconProps} />;
      case 'Activity': return <Activity {...iconProps} />;
      case 'Paintbrush': return <Paintbrush {...iconProps} />;
      case 'Sun': return <Sun {...iconProps} />;
      case 'Code': return <Code {...iconProps} />;
      case 'FileText': return <FileText {...iconProps} />;
      case 'Wifi': return <Wifi {...iconProps} />;
      case 'Radio': return <Radio {...iconProps} />;
      case 'Headphones': return <Headphones {...iconProps} />;
      case 'Scissors': return <Scissors {...iconProps} />;
      case 'Printer': return <Printer {...iconProps} />;
      default: return <QrCode {...iconProps} />;
    }
  };

  return (
    <div
      onClick={onClick}
      className="group relative flex flex-col items-center p-4 rounded-3xl bg-[#121e36] dark:bg-[#121e36] border border-slate-700/60 hover:border-blue-500/50 shadow-lg shadow-black/20 hover:shadow-cyan-500/10 active:scale-[0.97] transition-all duration-200 cursor-pointer overflow-hidden text-center"
    >
      {/* Butang Kegemaran (Heart Bookmark) di Bucu Atas Kanan */}
      <button
        onClick={(e) => {
          e.stopPropagation();
          onToggleFavorite(e);
        }}
        className="absolute top-3 right-3 p-1.5 rounded-full text-slate-400 hover:text-rose-400 active:scale-90 transition z-10"
        title="Simpan ke Kegemaran"
      >
        <Heart
          className={`w-4 h-4 transition-colors ${
            isFavorite ? 'fill-rose-500 text-rose-500' : 'text-slate-500 hover:text-slate-300'
          }`}
        />
      </button>

      {/* Lencana Khas jika ada (cth: HOT / AES-256) */}
      {tool.badge && (
        <span className="absolute top-3 left-3 px-2 py-0.5 rounded-md bg-blue-600/30 border border-blue-400/40 text-[9px] font-mono font-bold text-cyan-300 uppercase tracking-wide">
          {tool.badge}
        </span>
      )}

      {/* Bulatan Lencana Ikon */}
      <div
        className={`w-14 h-14 rounded-2xl ${tool.iconBgColor} flex items-center justify-center my-2 shadow-inner border border-white/5 group-hover:scale-105 transition-transform`}
      >
        {renderIcon()}
      </div>

      {/* Tajuk Alatan */}
      <h3 className="text-sm font-bold text-white group-hover:text-cyan-300 transition-colors leading-snug line-clamp-1 mt-1">
        {tool.name[lang]}
      </h3>

      {/* Penerangan Ringkas Alatan */}
      <p className="text-[11px] text-slate-400 line-clamp-2 mt-1 leading-relaxed">
        {tool.description[lang]}
      </p>
    </div>
  );
};
