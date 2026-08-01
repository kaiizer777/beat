'use client';

import React from 'react';
import Link from 'next/link';
import { PlusCircle, Activity, Radio } from 'lucide-react';
import { AuthNav } from './AuthNav';

interface HeaderProps {
  onOpenCreateModal?: () => void;
  isBackendOnline: boolean | null;
}

export function Header({ onOpenCreateModal, isBackendOnline }: HeaderProps) {
  return (
    <header className="sticky top-0 z-30 border-b border-slate-800/80 bg-slate-950/85 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3.5 sm:px-6 lg:px-8">
        <Link href="/" className="flex items-center space-x-3 group">
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-tr from-cyan-500 to-blue-600 shadow-lg shadow-cyan-500/25 group-hover:scale-105 transition transform">
            <Radio className="h-5 w-5 text-white animate-pulse" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-xl font-extrabold tracking-tight text-white sm:text-2xl group-hover:text-cyan-300 transition">
                BEAT
              </h1>
              <span className="rounded-full bg-cyan-500/10 px-2.5 py-0.5 text-[11px] font-bold text-cyan-400 border border-cyan-500/20">
                v1.0
              </span>
            </div>
            <p className="hidden sm:block text-[11px] text-slate-400">
              Personalized News & Intelligence Pipeline
            </p>
          </div>
        </Link>

        <div className="flex items-center space-x-3 sm:space-x-4">
          {/* Backend Status Indicator */}
          <div className="hidden md:flex items-center space-x-2 rounded-full border border-slate-800 bg-slate-900/80 px-3 py-1 text-xs font-medium">
            <Activity className="h-3.5 w-3.5 text-slate-400" />
            <span className="text-slate-400">API:</span>
            {isBackendOnline === null ? (
              <span className="text-amber-400 animate-pulse">Connecting...</span>
            ) : isBackendOnline ? (
              <span className="flex items-center text-emerald-400">
                <span className="mr-1.5 h-2 w-2 rounded-full bg-emerald-400 animate-ping inline-block" />
                Live
              </span>
            ) : (
              <span className="flex items-center text-rose-400">
                <span className="mr-1.5 h-2 w-2 rounded-full bg-rose-500 inline-block" />
                Offline
              </span>
            )}
          </div>

          <AuthNav />

          {onOpenCreateModal && (
            <button
              onClick={onOpenCreateModal}
              className="flex items-center space-x-2 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-3.5 sm:px-4 py-2 text-xs sm:text-sm font-semibold text-white shadow-lg shadow-cyan-500/20 transition hover:from-cyan-400 hover:to-blue-500 focus:outline-none"
            >
              <PlusCircle className="h-4 w-4" />
              <span className="hidden sm:inline">New Channel</span>
              <span className="sm:hidden">New</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
