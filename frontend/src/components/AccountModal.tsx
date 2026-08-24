'use client';

import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useSession, signOut } from 'next-auth/react';
import { X, User, Mail, LogOut, ShieldCheck, Send } from 'lucide-react';

interface AccountModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function AccountModal({ isOpen, onClose }: AccountModalProps) {
  const { data: session } = useSession();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!isOpen || !mounted) return null;

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-in fade-in duration-150 font-sans">
      <div
        className="w-full max-w-md rounded-2xl border border-slate-800 bg-[#0d131f] shadow-2xl text-slate-100 relative overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6">
          {/* Top Header */}
          <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-5">
            <div className="flex items-center space-x-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-800 text-sky-400 border border-slate-700/80 shadow-sm">
                <User className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white tracking-tight">Account Settings</h2>
                <p className="text-xs text-slate-400">Manage your profile & delivery options</p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="rounded-lg border border-slate-800 bg-slate-900/80 p-1.5 text-slate-400 hover:text-white hover:border-slate-700 transition"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          {/* User Details */}
          <div className="space-y-3.5">
            <div className="rounded-xl border border-slate-800/80 bg-slate-950/40 p-4">
              <div className="flex items-center space-x-2.5 mb-1.5 text-slate-400 text-xs font-medium uppercase tracking-wider">
                <Mail className="h-3.5 w-3.5 text-sky-400" />
                <span>Signed-In Email</span>
              </div>
              <p className="text-sm font-semibold text-white break-all">
                {session?.user?.email || 'Not signed in'}
              </p>
            </div>

            <div className="rounded-xl border border-slate-800/80 bg-slate-950/40 p-4">
              <div className="flex items-center space-x-2.5 mb-1.5 text-slate-400 text-xs font-medium uppercase tracking-wider">
                <Send className="h-3.5 w-3.5 text-emerald-400" />
                <span>Digest Delivery Email</span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                All scheduled digests from your active channels will be sent to{' '}
                <strong className="text-emerald-400 font-semibold">{session?.user?.email || 'your email'}</strong>.
              </p>
            </div>

            <div className="rounded-xl border border-slate-800/80 bg-slate-950/40 p-4 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <ShieldCheck className="h-4 w-4 text-sky-400" />
                <div>
                  <p className="text-xs font-medium text-slate-400">Authentication Method</p>
                  <p className="text-xs font-semibold text-slate-200">Resend Passwordless Magic Link</p>
                </div>
              </div>
              <span className="rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-400 border border-emerald-500/20">
                Active
              </span>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-slate-800 bg-slate-900/60">
          <button
            onClick={() => signOut()}
            className="flex items-center space-x-2 rounded-xl border border-rose-500/30 bg-rose-500/10 hover:bg-rose-500/20 px-4 py-2 text-xs font-semibold text-rose-400 shadow-sm transition"
          >
            <LogOut className="h-3.5 w-3.5" />
            <span>Sign Out</span>
          </button>

          <button
            onClick={onClose}
            className="rounded-xl border border-slate-700/80 bg-slate-800 hover:bg-slate-700/90 px-4 py-2 text-xs font-medium text-slate-200 shadow-sm transition"
          >
            Close
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
