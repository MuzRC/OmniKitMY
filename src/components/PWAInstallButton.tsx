/**
 * @file src/components/PWAInstallButton.tsx
 * @description Butang Pemasangan Aplikasi PWA Dalam Antaramuka (In-App PWA Install Banner).
 * Membolehkan pengguna Android memasang OmniKitMY terus ke skrin utama telefon.
 */

import React, { useState, useEffect } from 'react';
import { Download, Sparkles, X } from 'lucide-react';
import { Language } from '../types/tool';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

interface Props {
  lang: Language;
}

export const PWAInstallButton: React.FC<Props> = ({ lang }) => {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isDismissed, setIsDismissed] = useState<boolean>(false);
  const [isStandalone, setIsStandalone] = useState<boolean>(false);

  useEffect(() => {
    // Semak sama ada sudah berjalan dalam mod standalone aplikasi
    const standaloneMode =
      window.matchMedia('(display-mode: standalone)').matches ||
      (window.navigator as unknown as { standalone?: boolean }).standalone === true;
    setIsStandalone(standaloneMode);

    const handleBeforeInstall = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstall);
    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstall);
    };
  }, []);

  const handleInstallClick = async () => {
    if (!deferredPrompt) return;
    await deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      setDeferredPrompt(null);
    }
  };

  if (isStandalone || isDismissed || !deferredPrompt) {
    return null;
  }

  return (
    <div className="mx-4 my-2 p-3.5 rounded-2xl bg-gradient-to-r from-blue-700/40 via-indigo-700/40 to-slate-900 border border-blue-500/40 flex items-center justify-between gap-3 shadow-lg">
      <div className="flex items-center gap-2.5">
        <div className="w-9 h-9 rounded-xl bg-blue-600/30 text-cyan-400 flex items-center justify-center shrink-0 border border-blue-400/30">
          <Sparkles className="w-5 h-5 text-yellow-400" />
        </div>
        <div>
          <h4 className="text-xs font-bold text-white">
            {lang === 'ms' ? 'Pasang OmniKitMY ke Telefon' : 'Install OmniKitMY to Phone'}
          </h4>
          <p className="text-[10px] text-slate-300">
            {lang === 'ms' ? 'Akses pantas & penggunaan luar talian' : 'Fast standalone launcher access'}
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={handleInstallClick}
          className="py-1.5 px-3 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs shadow transition active:scale-95"
        >
          {lang === 'ms' ? 'Pasang' : 'Install'}
        </button>
        <button
          onClick={() => setIsDismissed(true)}
          className="p-1 rounded-lg text-slate-400 hover:text-white"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
