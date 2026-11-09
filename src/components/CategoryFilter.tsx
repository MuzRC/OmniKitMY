/**
 * @file src/components/CategoryFilter.tsx
 * @description Bar Penapis Kategori Alatan (Segmented Category Filter Tabs).
 * Membolehkan pengguna menapis alatan mengikut kategori dengan pantas.
 */

import React from 'react';
import { ToolCategory, Language } from '../types/tool';
import { getTranslation } from '../services/i18n';

interface CategoryFilterProps {
  selectedCategory: ToolCategory;
  onSelectCategory: (cat: ToolCategory) => void;
  lang: Language;
}

export const CategoryFilter: React.FC<CategoryFilterProps> = ({
  selectedCategory,
  onSelectCategory,
  lang,
}) => {
  const t = getTranslation(lang);

  const categories: { id: ToolCategory; label: string }[] = [
    { id: 'all', label: t.categories.all },
    { id: 'audio', label: t.categories.audio },
    { id: 'sensor', label: t.categories.sensor },
    { id: 'measure', label: t.categories.measure },
    { id: 'media', label: t.categories.media },
    { id: 'security', label: t.categories.security },
    { id: 'utility', label: t.categories.utility },
    { id: 'developer', label: t.categories.developer },
  ];

  return (
    <div className="w-full overflow-x-auto no-scrollbar py-2.5 px-4">
      <div className="flex items-center gap-1.5 min-w-max">
        {categories.map((cat) => (
          <button
            key={cat.id}
            onClick={() => onSelectCategory(cat.id)}
            className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all duration-150 active:scale-95 ${
              selectedCategory === cat.id
                ? 'bg-blue-600 text-white shadow-md shadow-blue-900/30'
                : 'bg-[#121e36]/80 text-slate-400 hover:text-white hover:bg-slate-800 border border-slate-700/40'
            }`}
          >
            {cat.label}
          </button>
        ))}
      </div>
    </div>
  );
};
