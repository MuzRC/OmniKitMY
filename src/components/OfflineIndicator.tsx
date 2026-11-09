/**
 * @file src/components/OfflineIndicator.tsx
 * @description Penunjuk Status Luar Talian & PWA (Offline & Connectivity Banner).
 * Memaklumkan pengguna bahawa data disimpan dalam peranti dan alatan kekal berfungsi tanpa internet.
 */

import React, { useState, useEffect } from 'react';
import { WifiOff, ShieldCheck } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
}

export const OfflineIndicator: React.FC<Props> = ({ lang }) => {
  const [isOnline, setIsOnline] = useState<boolean>(navigator.onLine);

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  if (isOnline) return null;

  return (
    <div className="fixed bottom-4 left-4 right-4 max-w-sm mx-auto z-50 flex items-center gap-2.5 p-3 rounded-2xl bg-amber-500/90 text-slate-950 font-semibold text-xs shadow-xl backdrop-blur-md animate-in fade-in slide-in-from-bottom-3">
      <WifiOff className="w-4 h-4 shrink-0 text-slate-950" />
      <span className="flex-1">
        {lang === 'ms'
          ? 'Mod Luar Talian Aktif — Semua alatan berfungsi tanpa internet!'
          : 'Offline Mode Active — All tools function without internet!'}
      </span>
    </div>
  );
};
