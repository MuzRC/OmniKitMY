/**
 * @file src/App.tsx
 * @description Komponen Induk Aplikasi OmniKitMY (Natif Capacitor).
 * Kawalan butang Back Android menggunakan @capacitor/app:
 * 1. Tutup alatan jika berada di dalam sub-screen.
 * 2. Double-tap back berserta toast jika di skrin utama untuk exit.
 */

import React, { useState, useEffect, useMemo, useRef } from 'react';
import { App as CapApp } from '@capacitor/app';
import { Header } from './components/Header';
import { CategoryFilter } from './components/CategoryFilter';
import { ToolCard } from './components/ToolCard';
import { OfflineIndicator } from './components/OfflineIndicator';
import { ALL_TOOLS } from './tools/allToolsRegistry';
import { ToolCategory, Language } from './types/tool';
import { getFavorites, toggleFavorite, getStoredSetting, saveStoredSetting } from './services/storage';
import { getTranslation } from './services/i18n';
import { Heart, SearchX } from 'lucide-react';

export default function App() {
  // Tetapan Bahasa (Lalai: Bahasa Melayu 'ms')
  const [lang, setLang] = useState<Language>(() => getStoredSetting<Language>('language', 'ms'));

  // Tetapan Tema Mod Gelap (Lalai: Gelap 'dark')
  const [isDark, setIsDark] = useState<boolean>(() => getStoredSetting<boolean>('is_dark', true));

  // Keadaan Penapis & Carian
  const [selectedCategory, setSelectedCategory] = useState<ToolCategory>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [showOnlyFavorites, setShowOnlyFavorites] = useState<boolean>(false);

  // Alatan Kegemaran
  const [favorites, setFavorites] = useState<string[]>(() => getFavorites());

  // Alatan Aktif
  const [activeToolId, setActiveToolId] = useState<string | null>(null);

  // Rujukan untuk listener natif
  const activeToolIdRef = useRef<string | null>(activeToolId);
  const lastBackPressRef = useRef<number>(0);
  const [showExitToast, setShowExitToast] = useState<boolean>(false);

  useEffect(() => {
    activeToolIdRef.current = activeToolId;
  }, [activeToolId]);

  const t = getTranslation(lang);

  // Simpan tetapan tema dan kemas kini kelas HTML
  useEffect(() => {
    saveStoredSetting('is_dark', isDark);
    if (isDark) {
      document.documentElement.classList.add('dark');
      document.body.className = 'bg-[#090e1c] text-slate-100 antialiased selection:bg-amber-400 selection:text-slate-900 min-h-screen';
    } else {
      document.documentElement.classList.remove('dark');
      document.body.className = 'bg-slate-50 text-slate-900 antialiased selection:bg-blue-600 selection:text-white min-h-screen';
    }
  }, [isDark]);

  // ===================== KAWALAN BACK BUTTON CAPACITOR =====================
  useEffect(() => {
    let toastTimer: any = null;

    const backListenerPromise = CapApp.addListener('backButton', () => {
      // 1. Kalau sedang berada dalam alatan, kembali ke menu utama
      if (activeToolIdRef.current !== null) {
        setActiveToolId(null);
        return;
      }

      // 2. Kalau di menu utama, semak jeda masa 2 saat (Double-tap to exit)
      const now = Date.now();
      if (now - lastBackPressRef.current < 2000) {
        setShowExitToast(false);
        CapApp.exitApp(); // Panggil fungsi natif untuk tutup app
      } else {
        lastBackPressRef.current = now;
        setShowExitToast(true);

        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
          setShowExitToast(false);
        }, 2000);
      }
    });

    return () => {
      backListenerPromise.then((handler) => handler.remove());
      clearTimeout(toastTimer);
    };
  }, []);

  const handleLanguageChange = (newLang: Language) => {
    setLang(newLang);
    saveStoredSetting('language', newLang);
  };

  const handleToggleFavorite = (toolId: string) => {
    const updated = toggleFavorite(toolId);
    setFavorites(updated);
  };

  // Tapis alatan
  const filteredTools = useMemo(() => {
    return ALL_TOOLS.filter((tool) => {
      if (showOnlyFavorites && !favorites.includes(tool.id)) {
        return false;
      }

      if (selectedCategory !== 'all' && tool.category !== selectedCategory) {
        return false;
      }

      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase().trim();
        const nameMs = tool.name.ms.toLowerCase();
        const nameEn = tool.name.en.toLowerCase();
        const descMs = tool.description.ms.toLowerCase();
        const descEn = tool.description.en.toLowerCase();
        const matchesKeyword = tool.keywords.some((k) => k.toLowerCase().includes(query));

        if (
          !nameMs.includes(query) &&
          !nameEn.includes(query) &&
          !descMs.includes(query) &&
          !descEn.includes(query) &&
          !matchesKeyword
        ) {
          return false;
        }
      }

      return true;
    });
  }, [selectedCategory, searchQuery, showOnlyFavorites, favorites]);

  const activeTool = useMemo(() => {
    if (!activeToolId) return null;
    return ALL_TOOLS.find((t) => t.id === activeToolId) || null;
  }, [activeToolId]);

  return (
    <div className={`min-h-screen flex flex-col ${isDark ? 'dark bg-[#090e1c] text-slate-100' : 'bg-slate-100 text-slate-900'}`}>
      {/* ===================== PAPARAN ALATAN SKRIN PENUH ===================== */}
      {activeTool ? (
        <main className="flex-1 flex flex-col">
          <activeTool.component
            lang={lang}
            onBack={() => setActiveToolId(null)}
          />
        </main>
      ) : (
        /* ===================== PAPARAN UTAMA (KATALOG ALATAN) ===================== */
        <>
          <Header
            lang={lang}
            onLanguageChange={handleLanguageChange}
            isDark={isDark}
            onThemeToggle={() => setIsDark(!isDark)}
            showOnlyFavorites={showOnlyFavorites}
            onToggleFavorites={() => setShowOnlyFavorites(!showOnlyFavorites)}
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
            totalTools={ALL_TOOLS.length}
          />

          <div className="max-w-5xl mx-auto w-full">
            <CategoryFilter
              selectedCategory={selectedCategory}
              onSelectCategory={setSelectedCategory}
              lang={lang}
            />
          </div>

          <main className="flex-1 max-w-5xl mx-auto w-full px-4 py-3">
            <div className="flex items-center justify-between mb-3 px-1">
              <span className="text-xs font-semibold text-slate-400">
                {showOnlyFavorites
                  ? lang === 'ms'
                    ? `Alatan Kegemaran (${filteredTools.length})`
                    : `Favorite Tools (${filteredTools.length})`
                  : lang === 'ms'
                  ? `Semua Alatan (${filteredTools.length})`
                  : `All Tools (${filteredTools.length})`}
              </span>

              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="text-xs text-cyan-400 hover:underline"
                >
                  {lang === 'ms' ? 'Kosongkan carian' : 'Clear search'}
                </button>
              )}
            </div>

            {filteredTools.length > 0 ? (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3 sm:gap-4">
                {filteredTools.map((tool) => (
                  <ToolCard
                    key={tool.id}
                    tool={tool}
                    lang={lang}
                    isFavorite={favorites.includes(tool.id)}
                    onToggleFavorite={() => handleToggleFavorite(tool.id)}
                    onClick={() => setActiveToolId(tool.id)}
                  />
                ))}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
                <div className="w-16 h-16 rounded-2xl bg-slate-800/80 text-slate-500 flex items-center justify-center mb-3">
                  {showOnlyFavorites ? <Heart className="w-8 h-8" /> : <SearchX className="w-8 h-8" />}
                </div>
                <h4 className="text-base font-bold text-white">
                  {showOnlyFavorites ? t.favorites : t.noResults}
                </h4>
                <p className="text-xs text-slate-400 max-w-xs mt-1">
                  {showOnlyFavorites ? t.noFavorites : t.noResults}
                </p>
                {showOnlyFavorites && (
                  <button
                    onClick={() => setShowOnlyFavorites(false)}
                    className="mt-4 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold transition"
                  >
                    {t.allTools}
                  </button>
                )}
              </div>
            )}
          </main>

          <footer className="mt-auto border-t border-slate-800/80 py-4 px-4 text-center text-[11px] text-slate-500">
            <p>{t.footerNote}</p>
            <p className="text-[10px] text-slate-600 mt-0.5">Versi Release 1.0 (Stabil) · Sifar Data Demo</p>
          </footer>
        </>
      )}

      <OfflineIndicator lang={lang} />

      {/* ===================== TOAST NOTIFIKASI ===================== */}
      {showExitToast && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 z-50 flex items-center gap-2.5 px-4 py-2.5 rounded-full bg-slate-900/95 border border-slate-700/80 text-white text-xs font-medium shadow-2xl backdrop-blur-md pointer-events-none transition-all duration-200">
          <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
          <span>{lang === 'ms' ? 'Tekan sekali lagi untuk keluar' : 'Press back again to exit'}</span>
        </div>
      )}
    </div>
  );
}
