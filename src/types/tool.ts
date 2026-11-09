/**
 * @file src/types/tool.ts
 * @description Takrifan jenis TypeScript teras untuk aplikasi OmniKitMY.
 * Mengandungi struktur data untuk alatan, kategori, tetapan bahasa, dan keadaan storan.
 */

export type ToolCategory =
  | 'all'
  | 'audio'
  | 'sensor'
  | 'measure'
  | 'media'
  | 'security'
  | 'utility'
  | 'developer';

export type Language = 'ms' | 'en';

export interface ToolMetadata {
  id: string;
  name: {
    ms: string;
    en: string;
  };
  description: {
    ms: string;
    en: string;
  };
  category: ToolCategory;
  iconName: string;
  iconBgColor: string;
  iconColor: string;
  keywords: string[];
  isSensorBased?: boolean;
  requiresCamera?: boolean;
  requiresMic?: boolean;
  badge?: string;
}

export interface VaultItem {
  id: string;
  title: string;
  type: 'note' | 'password' | 'file';
  ciphertext: string;
  iv: string;
  salt: string;
  createdAt: number;
  tags?: string[];
}
