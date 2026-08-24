'use client';

import React from 'react';
import Link from 'next/link';
import {
  Clock,
  Hash,
  Edit3,
  Trash2,
  CheckCircle2,
  XCircle,
  Loader2,
  Calendar,
  BookOpen,
  Zap,
} from 'lucide-react';
import { Channel } from '../types/channel';

interface ChannelCardProps {
  channel: Channel;
  onEdit: (channel: Channel) => void;
  onDelete: (channel: Channel) => void;
  onToggleActive: (channel: Channel, nextState: boolean) => void;
  onRunNow?: (channel: Channel) => void;
  isRunPending?: boolean;
}

export function ChannelCard({
  channel,
  onEdit,
  onDelete,
  onToggleActive,
  onRunNow,
  isRunPending = false,
}: ChannelCardProps) {
  const formatTime12h = (timeStr?: string) => {
    if (!timeStr) return '--:--';
    try {
      const parts = timeStr.split(':');
      let hours = parseInt(parts[0], 10);
      const minutes = parts[1] || '00';
      const ampm = hours >= 12 ? 'PM' : 'AM';
      hours = hours % 12 || 12;
      return `${hours}:${minutes} ${ampm}`;
    } catch (e) {
      return timeStr;
    }
  };

  const getTimezoneAbbr = (tz?: string) => {
    if (!tz) return 'IST';
    const tzMap: Record<string, string> = {
      'Asia/Kolkata': 'IST',
      'UTC': 'UTC',
      'America/New_York': 'EST',
      'America/Los_Angeles': 'PST',
      'Europe/London': 'GMT',
      'Asia/Tokyo': 'JST',
    };
    return tzMap[tz] || tz.split('/')[1] || tz;
  };

  const calculateNextRun = (cronTime?: string, tz?: string, isActive?: boolean) => {
    if (!isActive) return 'Schedule Paused';
    if (!cronTime) return 'Daily';

    const timeFormatted = formatTime12h(cronTime);
    const tzAbbr = getTimezoneAbbr(tz);

    try {
      const parts = cronTime.split(':');
      const targetHour = parseInt(parts[0], 10);
      const targetMin = parseInt(parts[1], 10);

      const now = new Date();
      const currentHour = now.getHours();
      const currentMin = now.getMinutes();

      const isUpcomingToday =
        targetHour > currentHour ||
        (targetHour === currentHour && targetMin > currentMin);

      return isUpcomingToday
        ? `Today at ${timeFormatted} ${tzAbbr}`
        : `Tomorrow at ${timeFormatted} ${tzAbbr}`;
    } catch (e) {
      return `Daily at ${timeFormatted} ${tzAbbr}`;
    }
  };

  const formatLastRunAt = (dateStr?: string | null) => {
    if (!dateStr) return null;
    try {
      const date = new Date(dateStr);
      return date.toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch (e) {
      return dateStr;
    }
  };

  const lastRun = formatLastRunAt(channel.lastRunAt);
  const nextRun = calculateNextRun(channel.cronTime, channel.timezone, channel.isActive);
  const status = isRunPending ? 'PENDING' : channel.lastRunStatus?.toUpperCase() ?? null;

  const statusConfig = {
    SUCCESS: {
      label: 'Success',
      badgeClass: 'bg-emerald-500/10 border-emerald-500/25 border-b-[2px] border-b-emerald-600/50 text-emerald-400',
      Icon: CheckCircle2,
    },
    FAILED: {
      label: 'Failed',
      badgeClass: 'bg-rose-500/10 border-rose-500/25 border-b-[2px] border-b-rose-600/50 text-rose-400',
      Icon: XCircle,
    },
    PENDING: {
      label: 'Running…',
      badgeClass: 'bg-amber-500/10 border-amber-500/25 border-b-[2px] border-b-amber-600/50 text-amber-400',
      Icon: Loader2,
    },
  }[status ?? ''] ?? {
    label: 'No runs yet',
    badgeClass: 'bg-zinc-800/60 border-zinc-700/60 border-b-[2px] border-b-zinc-800 text-zinc-400',
    Icon: Calendar,
  };

  return (
    <div
      className={`relative flex flex-col justify-between rounded-2xl p-5 ${
        channel.isActive
          ? 'bg-gradient-to-b from-[#131522] via-[#0e1017] to-[#0a0b10] border-x border-t border-zinc-700/70 border-b-[6px] border-b-zinc-950 shadow-[0_12px_36px_rgba(0,0,0,0.7)]'
          : 'bg-[#0e1017] border-x border-t border-zinc-800/70 border-b-[6px] border-b-zinc-950 opacity-80 shadow-[0_4px_15px_rgba(0,0,0,0.4)]'
      }`}
    >
      {/* Active Glow Accent Line */}
      {channel.isActive && (
        <div className="absolute top-0 left-1/4 right-1/4 h-[2px] bg-gradient-to-r from-transparent via-blue-400 to-transparent shadow-[0_0_12px_rgba(59,130,246,0.6)]" />
      )}

      <div>
        {/* Header: Title + Pulse + Active Toggle */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center space-x-2.5 min-w-0">
            <Link
              href={`/channels/${channel.id}`}
              className="text-base font-bold text-zinc-100 hover:text-blue-300 transition truncate tracking-tight"
            >
              {channel.name}
            </Link>
            <span className="relative flex h-2.5 w-2.5 flex-shrink-0">
              <span
                className={`h-2.5 w-2.5 rounded-full ${
                  channel.isActive ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]' : 'bg-zinc-600'
                }`}
              />
              {channel.isActive && (
                <span className="absolute inset-0 rounded-full bg-emerald-400 animate-ping opacity-50" />
              )}
            </span>
          </div>

          {/* Active Switch */}
          <button
            onClick={() => onToggleActive(channel, !channel.isActive)}
            title={channel.isActive ? 'Pause Schedule' : 'Activate Schedule'}
            className={`relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full border border-zinc-700 border-b-[3px] border-b-zinc-950 transition-colors duration-200 ease-in-out focus:outline-none ${
              channel.isActive ? 'bg-blue-600' : 'bg-zinc-800'
            }`}
          >
            <span
              className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-[0_2px_4px_rgba(0,0,0,0.5)] transition duration-200 ease-in-out ${
                channel.isActive ? 'translate-x-4' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Topic Query Badge */}
        <div className="mt-3 rounded-xl border border-zinc-800 bg-zinc-950/70 border-b-[3px] border-b-zinc-950 p-2.5 shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
          <p className="text-xs text-zinc-300 font-mono line-clamp-2 leading-relaxed">
            {channel.topicQuery}
          </p>
        </div>

        {/* Schedule & Article Info Grid */}
        <div className="mt-4 grid grid-cols-2 gap-2.5">
          {/* Next Scheduled Run */}
          <div className="col-span-2 rounded-xl border border-blue-500/30 bg-blue-500/[0.08] border-b-[3px] border-b-blue-950/60 p-2.5 flex items-center space-x-2.5 shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
            <Clock className="h-4 w-4 text-blue-400 flex-shrink-0" />
            <div className="min-w-0 flex-1">
              <span className="block text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
                Next Run
              </span>
              <span className="block text-xs font-bold text-blue-200 truncate drop-shadow-md">
                {nextRun}
              </span>
            </div>
          </div>

          {/* Articles Target */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-950/70 border-b-[3px] border-b-zinc-950 p-2.5 flex items-center space-x-2 shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
            <Hash className="h-3.5 w-3.5 text-sky-400 flex-shrink-0" />
            <div>
              <span className="block text-[9px] font-semibold uppercase tracking-wider text-zinc-500">
                Articles
              </span>
              <span className="block text-xs font-bold text-zinc-200 drop-shadow-md">
                {channel.articleCount} <span className="text-[10px] text-zinc-500 font-normal">stories</span>
              </span>
            </div>
          </div>

          {/* Last Run Date */}
          <div className="rounded-xl border border-zinc-800 bg-zinc-950/70 border-b-[3px] border-b-zinc-950 p-2.5 flex items-center space-x-2 shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)]">
            <Calendar className="h-3.5 w-3.5 text-zinc-500 flex-shrink-0" />
            <div className="min-w-0">
              <span className="block text-[9px] font-semibold uppercase tracking-wider text-zinc-500">
                Last Run
              </span>
              <span className="block text-xs font-medium text-zinc-300 truncate drop-shadow-md">
                {lastRun ?? 'Never'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Action Footer */}
      <div className="mt-5 pt-4 border-t-2 border-zinc-800/80 flex items-center justify-between gap-2">
        {/* Status Badge */}
        <div
          className={`inline-flex items-center space-x-1.5 rounded-full border px-2.5 py-1 text-xs font-medium shadow-[0_2px_5px_rgba(0,0,0,0.3)] ${statusConfig.badgeClass}`}
        >
          <statusConfig.Icon
            className={`h-3 w-3 ${status === 'PENDING' ? 'animate-spin' : ''}`}
          />
          <span>{statusConfig.label}</span>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center space-x-1.5">
          {onRunNow && (
            <button
              onClick={() => onRunNow(channel)}
              disabled={isRunPending}
              title="Trigger research pipeline now"
              className="inline-flex items-center space-x-1.5 rounded-lg border border-zinc-700 bg-zinc-800/90 hover:bg-zinc-750 border-b-[3px] border-b-zinc-950 px-2.5 py-1.5 text-xs font-semibold text-zinc-200 hover:text-white shadow-[0_2px_5px_rgba(0,0,0,0.3)] transition disabled:opacity-50 disabled:border-b disabled:translate-y-[3px]"
            >
              {isRunPending ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-400" />
              ) : (
                <Zap className="h-3.5 w-3.5 text-blue-400" />
              )}
              <span className="hidden sm:inline">{isRunPending ? 'Running…' : 'Run Now'}</span>
            </button>
          )}

          <Link
            href={`/channels/${channel.id}`}
            className="inline-flex items-center space-x-1.5 rounded-lg border border-zinc-700 bg-zinc-800/90 hover:bg-zinc-750 border-b-[3px] border-b-zinc-950 px-2.5 py-1.5 text-xs font-medium text-zinc-300 hover:text-white shadow-[0_3px_8px_rgba(0,0,0,0.3)] transition"
            title="Read Digest History"
          >
            <BookOpen className="h-3.5 w-3.5 text-sky-400" />
            <span className="hidden sm:inline">History</span>
          </Link>

          <button
            onClick={() => onEdit(channel)}
            title="Edit Channel Settings"
            className="rounded-lg border border-zinc-700 bg-zinc-800/90 hover:bg-zinc-750 border-b-[3px] border-b-zinc-950 p-1.5 text-zinc-300 hover:text-white shadow-[0_3px_8px_rgba(0,0,0,0.3)] transition"
          >
            <Edit3 className="h-3.5 w-3.5" />
          </button>

          <button
            onClick={() => onDelete(channel)}
            title="Delete Channel"
            className="rounded-lg border border-rose-800/70 bg-rose-500/15 hover:bg-rose-500/25 border-b-[3px] border-b-rose-950 p-1.5 text-rose-300 hover:text-rose-200 shadow-[0_3px_8px_rgba(225,29,72,0.2)] transition"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}
