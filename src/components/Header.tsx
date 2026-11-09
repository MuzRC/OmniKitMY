/**
 * @file src/components/Header.tsx
 * @description Bar Navigasi Atas (Top App Bar) untuk OmniKitMY.
 * Menggabungkan Ikon Kotak Alatan Berwarna, Tajuk Jenama, Pengira Alatan, Carian, Bahasa, dan Tema.
 */

import React from 'react';
import { ToolboxLogo } from './ToolboxLogo';
import { Search, Heart, Moon, Sun, Globe, X } from 'lucide-react';
import { Language } from '../types/tool';

interface HeaderProps {
  lang: Language;
  onLanguageChange: (lang: Language) => void;
  isDark: boolean;
  onThemeToggle: () => void;
  showOnlyFavorites: boolean;
  onToggleFavorites: () => void;
  searchQuery: string;
  onSearchChange: (q: string) => void;
  totalTools: number;
}

export const Header: React.FC<HeaderProps> = ({
  lang,
  onLanguageChange,
  isDark,
  onThemeToggle,
  showOnlyFavorites,
  onToggleFavorites,
  searchQuery,
  onSearchChange,
  totalTools,
}) => {
  const [isSearchOpen, setIsSearchOpen] = React.useState(false);

  return (
    <header className="sticky top-0 z-30 bg-[#090e1c]/95 dark:bg-[#090e1c]/95 backdrop-blur-md border-b border-slate-800 px-4 py-3">
      <div className="max-w-5xl mx-auto flex items-center justify-between gap-3">
        {/* Bahagian Jenama & Logo Kotak Alatan */}
        <div className="flex items-center gap-2.5 shrink-0">
          <ToolboxLogo size={36} />
          <div className="flex flex-col">
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-black tracking-tight text-white flex items-center">
                <span>OmniKit</span>
                <span className="text-yellow-400">MY</span>
              </h1>
              <span className="px-2 py-0.5 rounded-full bg-blue-600/20 text-cyan-400 border border-blue-500/30 text-[10px] font-mono font-bold">
                {totalTools}
              </span>
            </div>
            <span className="text-[10px] text-slate-400 font-medium -mt-0.5">
              {lang === 'ms' ? 'Kit Alatan Pintar Luar Talian' : 'Smart Offline Toolkit'}
            </span>
          </div>
        </div>

        {/* Butang Tindakan Carian, Kegemaran, Bahasa, dan Tema */}
        <div className="flex items-center gap-1.5 sm:gap-2">
          {/* Carian Pantas (Desktop / Lapang) */}
          <div className="relative hidden md:block w-48 lg:w-64">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              placeholder={lang === 'ms' ? 'Cari alatan...' : 'Search tools...'}
              className="w-full pl-8 pr-3 py-1.5 rounded-xl bg-slate-900 border border-slate-700/80 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-400"
            />
            <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
            {searchQuery && (
              <button
                onClick={() => onSearchChange('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white"
              >
                <X className="w-3 h-3" />
              </button>
            )}
          </div>

          {/* Togol Carian Mobile */}
          <button
            onClick={() => setIsSearchOpen(!isSearchOpen)}
            className="md:hidden p-2 rounded-xl bg-slate-900/80 border border-slate-800 text-slate-300 hover:text-white active:scale-95 transition"
            aria-label="Cari"
          >
            <Search className="w-4 h-4" />
          </button>

          {/* Penapis Kegemaran */}
          <button
            onClick={onToggleFavorites}
            className={`p-2 rounded-xl border transition active:scale-95 ${
              showOnlyFavorites
                ? 'bg-rose-500/20 border-rose-500/60 text-rose-400 shadow-[0_0_10px_rgba(244,63,94,0.3)]'
                : 'bg-slate-900/80 border-slate-800 text-slate-400 hover:text-rose-400'
            }`}
            title={lang === 'ms' ? 'Alatan Kegemaran' : 'Favorite Tools'}
          >
            <Heart className={`w-4 h-4 ${showOnlyFavorites ? 'fill-rose-400' : ''}`} />
          </button>

          {/* Penukar Bahasa (BM / EN) */}
          <button
            onClick={() => onLanguageChange(lang === 'ms' ? 'en' : 'ms')}
            className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-slate-900/80 border border-slate-800 text-slate-300 hover:text-white text-xs font-bold active:scale-95 transition font-mono"
            title="Tukar Bahasa / Change Language"
          >
            <Globe className="w-3.5 h-3.5 text-cyan-400" />
            <span>{lang.toUpperCase()}</span>
          </button>

          {/* Togol Mod Gelap / Cerah */}
          <button
            onClick={onThemeToggle}
            className="p-2 rounded-xl bg-slate-900/80 border border-slate-800 text-slate-300 hover:text-yellow-400 active:scale-95 transition"
            title="Tukar Tema (Gelap / Cerah)"
          >
            {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {/* Bar Input Carian Mudah Alih (Muncul jika dibuka) */}
      {isSearchOpen && (
        <div className="md:hidden pt-3 px-1">
          <div className="relative w-full">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              placeholder={lang === 'ms' ? 'Cari nama alatan atau fungsi...' : 'Search tools...'}
              className="w-full pl-9 pr-8 py-2 rounded-xl bg-slate-900 border border-cyan-500/60 text-sm text-white placeholder-slate-500 focus:outline-none"
              autoFocus
            />
            <Search className="w-4 h-4 text-cyan-400 absolute left-3 top-1/2 -translate-y-1/2" />
            {searchQuery && (
              <button
                onClick={() => onSearchChange('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>
        </div>
      )}
    </header>
  );
};
