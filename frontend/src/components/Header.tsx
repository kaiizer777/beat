'use client';

import React from 'react';
import Link from 'next/link';
import { PlusCircle, Activity } from 'lucide-react';
import { AuthNav } from './AuthNav';

interface HeaderProps {
  onOpenCreateModal?: () => void;
  isBackendOnline: boolean | null;
}

export function Header({ onOpenCreateModal, isBackendOnline }: HeaderProps) {
  return (
    <header className="sticky top-4 z-30 mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8">
      <div className="flex items-center justify-between rounded-2xl border border-zinc-800/80 bg-[#0e1017]/90 px-4 sm:px-6 py-3 backdrop-blur-xl shadow-[0_8px_32px_rgba(0,0,0,0.6),0_1px_0_0_rgba(59,130,246,0.15)]">
        <Link href="/" className="group flex items-center space-x-3 transition">
          <div>
            <div className="flex items-center space-x-1.5">
              <h1 className="font-sans text-xl sm:text-2xl font-bold tracking-tight text-white">
                BEAT
              </h1>
            </div>
            <p className="hidden sm:block text-[11px] font-medium text-zinc-400 tracking-normal">
              Personalized News & Intelligence Pipeline
            </p>
          </div>
        </Link>

        <div className="flex items-center space-x-2.5 sm:space-x-3">
          {/* Backend Status Indicator */}
          <div className="hidden md:flex items-center space-x-2 rounded-full border border-zinc-800 bg-zinc-900/90 border-b-[3px] border-b-zinc-950 px-3 py-1 text-xs font-medium shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
            <Activity className="h-3.5 w-3.5 text-zinc-400" />
            <span className="text-zinc-400">API:</span>
            {isBackendOnline === null ? (
              <span className="text-amber-400 animate-pulse drop-shadow-sm">Connecting...</span>
            ) : isBackendOnline ? (
              <span className="flex items-center text-emerald-400 drop-shadow-sm">
                <span className="mr-1.5 h-2 w-2 rounded-full bg-emerald-400 animate-ping inline-block shadow-[0_0_8px_rgba(52,211,153,0.8)]" />
                Live
              </span>
            ) : (
              <span className="flex items-center text-rose-400 drop-shadow-sm">
                <span className="mr-1.5 h-2 w-2 rounded-full bg-rose-500 inline-block shadow-[0_0_8px_rgba(244,63,94,0.8)]" />
                Offline
              </span>
            )}
          </div>

          <AuthNav />

          {onOpenCreateModal && (
            <button
              onClick={onOpenCreateModal}
              className="flex items-center space-x-1.5 sm:space-x-2 rounded-xl bg-blue-600 hover:bg-blue-500 px-3 sm:px-3.5 py-1.5 sm:py-2 text-xs sm:text-sm font-semibold text-white border border-blue-500/40 border-b-[3px] border-b-blue-900 shadow-[0_2px_10px_rgba(37,99,235,0.4)] transition focus:outline-none"
            >
              <PlusCircle className="h-4 w-4 text-blue-200" />
              <span className="hidden sm:inline">New Channel</span>
              <span className="sm:hidden">New</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
