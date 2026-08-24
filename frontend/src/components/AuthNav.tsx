'use client';

import React, { useState } from 'react';
import { useSession, signOut } from 'next-auth/react';
import Link from 'next/link';
import { User, LogOut, Settings } from 'lucide-react';
import { AccountModal } from './AccountModal';

interface AuthNavProps {
  onOpenAccountModal?: () => void;
}

export function AuthNav({ onOpenAccountModal }: AuthNavProps) {
  const { data: session, status } = useSession();
  const [isAccountModalOpen, setIsAccountModalOpen] = useState(false);

  if (status === 'loading') {
    return <div className="text-xs text-slate-400 animate-pulse">Loading auth...</div>;
  }

  if (!session) {
    return (
      <Link
        href="/login"
        className="flex items-center space-x-2 text-xs bg-slate-800 hover:bg-slate-700/90 text-slate-200 hover:text-white font-medium px-3.5 py-2 rounded-xl border border-slate-700 border-b-[3px] border-b-slate-950 shadow-[0_2px_5px_rgba(0,0,0,0.3)] transition"
      >
        <User className="h-3.5 w-3.5 text-cyan-400" />
        <span>Sign in</span>
      </Link>
    );
  }

  const handleOpenModal = () => {
    if (onOpenAccountModal) {
      onOpenAccountModal();
    } else {
      setIsAccountModalOpen(true);
    }
  };

  return (
    <>
      <div className="flex items-center space-x-2">
        <button
          onClick={handleOpenModal}
          className="flex items-center space-x-2 text-xs bg-slate-800 text-slate-200 font-medium px-3 py-1.5 rounded-xl border border-slate-700 border-b-[3px] border-b-slate-950 shadow-[0_2px_5px_rgba(0,0,0,0.3)]"
          title="Account Settings"
        >
          <div className="h-5 w-5 rounded-full bg-cyan-500/20 text-cyan-400 flex items-center justify-center font-bold text-[10px] shadow-[inset_0_1px_2px_rgba(0,0,0,0.2)]">
            {session.user?.email ? session.user.email.charAt(0).toUpperCase() : 'U'}
          </div>
          <span className="hidden sm:inline max-w-[140px] truncate">{session.user?.email}</span>
          <Settings className="h-3.5 w-3.5 text-slate-400" />
        </button>

        <button
          onClick={() => signOut()}
          className="text-xs bg-slate-800 text-slate-400 p-2 rounded-xl border border-slate-700 border-b-[3px] border-b-slate-950 shadow-[0_2px_5px_rgba(0,0,0,0.3)]"
          title="Sign out"
        >
          <LogOut className="h-3.5 w-3.5" />
        </button>
      </div>

      <AccountModal
        isOpen={isAccountModalOpen}
        onClose={() => setIsAccountModalOpen(false)}
      />
    </>
  );
}
