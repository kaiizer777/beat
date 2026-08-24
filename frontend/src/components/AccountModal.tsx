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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="w-full max-w-md rounded-2xl border border-zinc-800 bg-[#0e1017] shadow-[0_10px_40px_rgba(0,0,0,0.8)] text-zinc-100 relative overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6">
          {/* Top Header */}
          <div className="flex items-center justify-between border-b border-zinc-800 pb-4 mb-6">
            <div className="flex items-center space-x-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20 border-b-[3px] border-b-blue-500/30">
                <User className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white drop-shadow-sm">Account Settings</h2>
                <p className="text-xs text-zinc-400">Manage your profile & delivery options</p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="rounded-lg border border-zinc-700 bg-zinc-800 border-b-[3px] border-b-zinc-950 p-1.5 text-zinc-400 hover:text-white shadow-[0_2px_5px_rgba(0,0,0,0.3)] transition"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* User Details */}
          <div className="space-y-4">
            <div className="rounded-xl border border-zinc-800 bg-zinc-950/70 border-b-[3px] border-b-zinc-950 p-4 shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
              <div className="flex items-center space-x-3 mb-2 text-zinc-400 text-xs font-medium uppercase tracking-wider">
                <Mail className="h-4 w-4 text-blue-400" />
                <span>Signed-In Email</span>
              </div>
              <p className="text-base font-semibold text-white break-all drop-shadow-sm">
                {session?.user?.email || 'Not signed in'}
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-950/70 border-b-[3px] border-b-zinc-950 p-4 shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
              <div className="flex items-center space-x-3 mb-2 text-zinc-400 text-xs font-medium uppercase tracking-wider">
                <Send className="h-4 w-4 text-emerald-400" />
                <span>Digest Delivery Email</span>
              </div>
              <p className="text-sm text-zinc-300">
                All scheduled digests from your active channels will be sent to{' '}
                <strong className="text-emerald-400 drop-shadow-sm">{session?.user?.email || 'your email'}</strong>.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-950/70 border-b-[3px] border-b-zinc-950 p-4 flex items-center justify-between shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
              <div className="flex items-center space-x-3">
                <ShieldCheck className="h-5 w-5 text-blue-400" />
                <div>
                  <p className="text-xs font-medium text-zinc-400">Authentication Method</p>
                  <p className="text-sm font-semibold text-zinc-200 drop-shadow-sm">Resend Passwordless Magic Link</p>
                </div>
              </div>
              <span className="rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-400 border border-emerald-500/20 shadow-[0_2px_4px_rgba(0,0,0,0.2)]">
                Active
              </span>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-between px-6 py-6 border-t border-zinc-800 bg-zinc-900/40">
          <button
            onClick={() => signOut()}
            className="flex items-center space-x-2 rounded-xl border border-rose-700/80 bg-rose-500/15 hover:bg-rose-500/25 border-b-[3px] border-b-rose-950 px-4 py-2.5 text-sm font-medium text-rose-300 shadow-[0_4px_10px_rgba(225,29,72,0.2)] transition"
          >
            <LogOut className="h-4 w-4" />
            <span>Sign Out</span>
          </button>

          <button
            onClick={onClose}
            className="rounded-xl border border-zinc-700 bg-zinc-800 hover:bg-zinc-700 border-b-[3px] border-b-zinc-950 px-5 py-2.5 text-sm font-medium text-zinc-200 shadow-[0_4px_10px_rgba(0,0,0,0.3)] transition"
          >
            Close
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
