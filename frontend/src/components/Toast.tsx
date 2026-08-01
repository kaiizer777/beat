'use client';

import React, { useEffect } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

export interface ToastMessage {
  id: string;
  type: 'success' | 'error' | 'info';
  title: string;
  message?: string;
}

interface ToastProps {
  toasts: ToastMessage[];
  onDismiss: (id: string) => void;
}

export function ToastContainer({ toasts, onDismiss }: ToastProps) {
  return (
    <div className="fixed bottom-5 right-5 z-50 flex flex-col space-y-3 max-w-sm w-full">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onDismiss={onDismiss} />
      ))}
    </div>
  );
}

function ToastItem({ toast, onDismiss }: { toast: ToastMessage; onDismiss: (id: string) => void }) {
  useEffect(() => {
    const timer = setTimeout(() => {
      onDismiss(toast.id);
    }, 4000);
    return () => clearTimeout(timer);
  }, [toast.id, onDismiss]);

  const getBg = () => {
    switch (toast.type) {
      case 'success':
        return 'border-emerald-500/30 bg-slate-900/95 text-emerald-300 shadow-emerald-500/10';
      case 'error':
        return 'border-rose-500/30 bg-slate-900/95 text-rose-300 shadow-rose-500/10';
      default:
        return 'border-cyan-500/30 bg-slate-900/95 text-cyan-300 shadow-cyan-500/10';
    }
  };

  const getIcon = () => {
    switch (toast.type) {
      case 'success':
        return <CheckCircle2 className="h-5 w-5 text-emerald-400 flex-shrink-0" />;
      case 'error':
        return <AlertCircle className="h-5 w-5 text-rose-400 flex-shrink-0" />;
      default:
        return <Info className="h-5 w-5 text-cyan-400 flex-shrink-0" />;
    }
  };

  return (
    <div
      className={`flex items-start justify-between rounded-xl border p-4 shadow-xl backdrop-blur-md transition duration-300 animate-slide-up ${getBg()}`}
    >
      <div className="flex items-start space-x-3">
        {getIcon()}
        <div>
          <h4 className="text-sm font-semibold text-white">{toast.title}</h4>
          {toast.message && <p className="mt-0.5 text-xs text-slate-300">{toast.message}</p>}
        </div>
      </div>
      <button
        onClick={() => onDismiss(toast.id)}
        className="ml-3 text-slate-400 hover:text-white transition"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
