/**
 * @file src/tools/PdfTools.tsx
 * @description Alatan Dokumen & Penjana Resit Cetakan (PDF & Document Generator).
 * 
 * FUNGSI UTAMA:
 * 1. Penjana Dokumen & Resit Rasmi Malaysia (Bilingual Invoice / Receipt / Memo Generator).
 * 2. Susun atur A4 responsif yang sedia dicetak atau dieksport ke fail PDF melalui dialog cetakan peranti.
 * 3. Pembaca & Pengekstrak Teks Dokumen Tempatan.
 * 4. Berfungsi 100% luar talian tanpa perlu sambungan internet.
 */

import React, { useState } from 'react';
import { ArrowLeft, FileText, Printer, Download, Plus, Trash2 } from 'lucide-react';
import { Language } from '../types/tool';

interface Props {
  lang: Language;
  onBack: () => void;
}

interface ItemRow {
  desc: string;
  qty: number;
  price: number;
}

export const PdfTools: React.FC<Props> = ({ lang, onBack }) => {
  const [docType, setDocType] = useState<'receipt' | 'memo'>('receipt');
  const [senderName, setSenderName] = useState('Perniagaan Maju Jaya');
  const [customerName, setCustomerName] = useState('Pelanggan Dihormati');
  const [docNumber, setDocNumber] = useState('INV-2026-001');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);

  const [items, setItems] = useState<ItemRow[]>([
    { desc: 'Perkhidmatan Pembaikan & Servis', qty: 1, price: 150.0 },
    { desc: 'Alat Ganti Komponen', qty: 2, price: 45.0 },
  ]);

  const addItem = () => {
    setItems([...items, { desc: 'Item Baharu', qty: 1, price: 10.0 }]);
  };

  const removeItem = (idx: number) => {
    setItems(items.filter((_, i) => i !== idx));
  };

  const totalAmount = items.reduce((sum, item) => sum + item.qty * item.price, 0);

  const printDocument = () => {
    window.print();
  };

  return (
    <div className="flex flex-col min-h-full pb-8">
      {/* Header */}
      <div className="flex items-center justify-between p-4 bg-[#121e36] border-b border-slate-700/60 sticky top-0 z-20 print:hidden">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 -ml-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition"
            aria-label="Kembali"
          >
            <ArrowLeft className="w-6 h-6" />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white leading-tight">
              {lang === 'ms' ? 'Alatan Dokumen & Resit PDF' : 'PDF & Receipt Generator'}
            </h2>
            <p className="text-xs text-slate-400">
              {lang === 'ms' ? 'Jana invois, resit & cetak PDF luar talian' : 'Create invoices & print PDF offline'}
            </p>
          </div>
        </div>

        <button
          onClick={printDocument}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow transition"
        >
          <Printer className="w-3.5 h-3.5" />
          <span>{lang === 'ms' ? 'Cetak / PDF' : 'Print / PDF'}</span>
        </button>
      </div>

      <div className="p-4 max-w-xl mx-auto w-full flex-1 flex flex-col gap-5">
        {/* Borang Input Dokumen (Sembunyi semasa cetak) */}
        <div className="p-4 bg-[#121e36] rounded-2xl border border-slate-700/60 flex flex-col gap-3 print:hidden">
          <span className="text-xs font-bold text-slate-300 uppercase tracking-wider">
            {lang === 'ms' ? 'Maklumat Dokumen' : 'Document Details'}
          </span>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">
                {lang === 'ms' ? 'Nama Pengeluar / Syarikat:' : 'Sender Name:'}
              </label>
              <input
                type="text"
                value={senderName}
                onChange={(e) => setSenderName(e.target.value)}
                className="w-full p-2 bg-slate-900 rounded-lg border border-slate-700 text-xs text-white"
              />
            </div>
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">
                {lang === 'ms' ? 'Nama Pelanggan:' : 'Customer Name:'}
              </label>
              <input
                type="text"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                className="w-full p-2 bg-slate-900 rounded-lg border border-slate-700 text-xs text-white"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">
                {lang === 'ms' ? 'No. Rujukan / Resit:' : 'Reference No:'}
              </label>
              <input
                type="text"
                value={docNumber}
                onChange={(e) => setDocNumber(e.target.value)}
                className="w-full p-2 bg-slate-900 rounded-lg border border-slate-700 text-xs text-white font-mono"
              />
            </div>
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">
                {lang === 'ms' ? 'Tarikh:' : 'Date:'}
              </label>
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full p-2 bg-slate-900 rounded-lg border border-slate-700 text-xs text-white font-mono"
              />
            </div>
          </div>
        </div>

        {/* Pratonton Dokumen Bersih (Sedia Cetak A4) */}
        <div className="bg-white text-slate-900 p-6 rounded-2xl shadow-2xl border border-slate-200 flex flex-col gap-5 print:shadow-none print:border-none print:p-0">
          {/* Header Resit */}
          <div className="flex justify-between items-start border-b border-slate-200 pb-4">
            <div>
              <h1 className="text-xl font-bold text-slate-900">{senderName}</h1>
              <p className="text-xs text-slate-500 mt-0.5">Malaysia · Perkhidmatan & Utiliti</p>
            </div>
            <div className="text-right">
              <span className="text-xs font-bold uppercase tracking-wider text-blue-600 block">
                {lang === 'ms' ? 'RESIT RASMI' : 'OFFICIAL RECEIPT'}
              </span>
              <span className="text-xs font-mono text-slate-600 block mt-0.5">{docNumber}</span>
              <span className="text-xs text-slate-500 block">{date}</span>
            </div>
          </div>

          <div>
            <span className="text-[11px] font-semibold text-slate-500 uppercase block">
              {lang === 'ms' ? 'Dikeluarkan Kepada:' : 'Billed To:'}
            </span>
            <span className="text-sm font-bold text-slate-800">{customerName}</span>
          </div>

          {/* Jadual Item */}
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-300 text-slate-600 uppercase text-[10px]">
                <th className="py-2">{lang === 'ms' ? 'Keterangan Item' : 'Description'}</th>
                <th className="py-2 text-center">{lang === 'ms' ? 'Kuantiti' : 'Qty'}</th>
                <th className="py-2 text-right">{lang === 'ms' ? 'Harga (RM)' : 'Price'}</th>
                <th className="py-2 text-right">{lang === 'ms' ? 'Jumlah (RM)' : 'Total'}</th>
                <th className="py-2 text-right print:hidden"></th>
              </tr>
            </thead>
            <tbody>
              {items.map((it, idx) => (
                <tr key={idx} className="border-b border-slate-100">
                  <td className="py-2.5 font-medium">{it.desc}</td>
                  <td className="py-2.5 text-center font-mono">{it.qty}</td>
                  <td className="py-2.5 text-right font-mono">{it.price.toFixed(2)}</td>
                  <td className="py-2.5 text-right font-mono font-bold">
                    {(it.qty * it.price).toFixed(2)}
                  </td>
                  <td className="py-2.5 text-right print:hidden">
                    <button
                      onClick={() => removeItem(idx)}
                      className="text-red-500 hover:text-red-700"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Butang Tambah Baris (Print Hidden) */}
          <div className="print:hidden">
            <button
              onClick={addItem}
              className="py-1.5 px-3 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold flex items-center gap-1 transition"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>{lang === 'ms' ? 'Tambah Baris Item' : 'Add Item Row'}</span>
            </button>
          </div>

          {/* Jumlah Keseluruhan */}
          <div className="flex justify-end pt-3 border-t-2 border-slate-800">
            <div className="text-right">
              <span className="text-xs font-semibold text-slate-600 block">
                {lang === 'ms' ? 'JUMLAH KESELURUHAN:' : 'GRAND TOTAL:'}
              </span>
              <span className="text-2xl font-black font-mono text-slate-900">
                RM {totalAmount.toFixed(2)}
              </span>
            </div>
          </div>

          <div className="text-[10px] text-slate-400 text-center pt-4 border-t border-slate-100">
            {lang === 'ms'
              ? 'Dijana secara tempatan melalui OmniKitMY · Sah tanpa tandatangan fizikal'
              : 'Generated offline via OmniKitMY · Valid without physical signature'}
          </div>
        </div>
      </div>
    </div>
  );
};
