'use client';

import React from 'react';
import { useSession, signOut } from 'next-auth/react';
import { X, User, Mail, LogOut, ShieldCheck, Send } from 'lucide-react';

interface AccountModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function AccountModal({ isOpen, onClose }: AccountModalProps) {
  const { data: session } = useSession();

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 shadow-2xl shadow-cyan-950/30 text-slate-100 relative overflow-hidden flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6 overflow-y-auto">
          {/* Top Header */}
          <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-6">
            <div className="flex items-center space-x-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                <User className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white">Account Settings</h2>
                <p className="text-xs text-slate-400">Manage your profile & delivery options</p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white transition"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* User Details */}
          <div className="space-y-4">
            <div className="rounded-xl border border-slate-800/80 bg-slate-950/60 p-4">
              <div className="flex items-center space-x-3 mb-2 text-slate-400 text-xs font-medium uppercase tracking-wider">
                <Mail className="h-4 w-4 text-cyan-400" />
                <span>Signed-In Email</span>
              </div>
              <p className="text-base font-semibold text-white break-all">
                {session?.user?.email || 'Not signed in'}
              </p>
            </div>

            <div className="rounded-xl border border-slate-800/80 bg-slate-950/60 p-4">
              <div className="flex items-center space-x-3 mb-2 text-slate-400 text-xs font-medium uppercase tracking-wider">
                <Send className="h-4 w-4 text-emerald-400" />
                <span>Digest Delivery Email</span>
              </div>
              <p className="text-sm text-slate-300">
                All scheduled digests from your active channels will be sent to{' '}
                <strong className="text-emerald-400">{session?.user?.email || 'your email'}</strong>.
              </p>
            </div>

            <div className="rounded-xl border border-slate-800/80 bg-slate-950/60 p-4 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <ShieldCheck className="h-5 w-5 text-blue-400" />
                <div>
                  <p className="text-xs font-medium text-slate-400">Authentication Method</p>
                  <p className="text-sm font-semibold text-slate-200">Resend Passwordless Magic Link</p>
                </div>
              </div>
              <span className="rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-400 border border-emerald-500/20">
                Active
              </span>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-between p-6 pt-4 border-t border-slate-800 bg-slate-900/50 mt-auto">
          <button
            onClick={() => signOut()}
            className="flex items-center space-x-2 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-2.5 text-sm font-medium text-rose-400 hover:bg-rose-500/20 hover:text-rose-300 transition"
          >
            <LogOut className="h-4 w-4" />
            <span>Sign Out</span>
          </button>

          <button
            onClick={onClose}
            className="rounded-xl bg-slate-800 px-5 py-2.5 text-sm font-medium text-slate-200 hover:bg-slate-700 transition"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
