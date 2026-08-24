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
    <header className="sticky top-0 z-30 border-b border-slate-800/80 bg-[#090d16]/85 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3.5 sm:px-6 lg:px-8">
        <Link href="/" className="group flex items-center space-x-3 transition">
          <div>
            <div className="flex items-center space-x-1.5">
              <h1 className="font-sans text-xl sm:text-2xl font-bold tracking-tight text-white">
                BEAT
              </h1>
            </div>
            <p className="hidden sm:block text-[11px] font-medium text-slate-400 tracking-normal">
              Personalized News & Intelligence Pipeline
            </p>
          </div>
        </Link>

        <div className="flex items-center space-x-3 sm:space-x-4">
          {/* Backend Status Indicator */}
          <div className="hidden md:flex items-center space-x-2 rounded-full border border-slate-800 bg-slate-900/90 px-3 py-1 text-xs font-medium shadow-sm">
            <Activity className="h-3.5 w-3.5 text-slate-400" />
            <span className="text-slate-400">API:</span>
            {isBackendOnline === null ? (
              <span className="text-amber-400 animate-pulse">Connecting...</span>
            ) : isBackendOnline ? (
              <span className="flex items-center text-emerald-400">
                <span className="mr-1.5 h-2 w-2 rounded-full bg-emerald-400 animate-ping inline-block shadow-[0_0_8px_rgba(52,211,153,0.8)]" />
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
              className="flex items-center space-x-2 rounded-xl bg-slate-800 hover:bg-slate-700/90 px-3.5 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-medium text-slate-200 hover:text-white border border-slate-700/80 shadow-sm transition focus:outline-none"
            >
              <PlusCircle className="h-4 w-4 text-sky-400" />
              <span className="hidden sm:inline">New Channel</span>
              <span className="sm:hidden">New</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
