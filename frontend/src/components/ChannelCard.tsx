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
    badgeClass: 'bg-slate-800/50 border-slate-700/50 border-b-[2px] border-b-slate-800 text-slate-400',
    Icon: Calendar,
  };  return (
    <div
      className={`group relative flex flex-col justify-between rounded-2xl p-5 border transition-all duration-200 shadow-sm backdrop-blur-md ${
        channel.isActive
          ? 'border-slate-800/90 bg-slate-900/50 hover:bg-slate-900/80 hover:border-slate-700/80'
          : 'border-slate-800/60 bg-slate-900/30 opacity-75 hover:opacity-100 hover:border-slate-700/70'
      }`}
    >
      <div>
        {/* Header: Title + Pulse + Active Toggle */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center space-x-2.5 min-w-0">
            <Link
              href={`/channels/${channel.id}`}
              className="text-base font-semibold text-slate-100 hover:text-sky-300 truncate tracking-tight transition"
            >
              {channel.name}
            </Link>
            <span className="relative flex h-2 w-2 flex-shrink-0">
              <span
                className={`h-2 w-2 rounded-full ${
                  channel.isActive ? 'bg-emerald-400' : 'bg-slate-600'
                }`}
              />
            </span>
          </div>

          {/* Active Switch */}
          <button
            onClick={() => onToggleActive(channel, !channel.isActive)}
            title={channel.isActive ? 'Pause Schedule' : 'Activate Schedule'}
            className={`relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full border border-slate-700 transition-colors duration-200 ease-in-out focus:outline-none ${
              channel.isActive ? 'bg-sky-500' : 'bg-slate-800'
            }`}
          >
            <span
              className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-sm transition duration-200 ease-in-out ${
                channel.isActive ? 'translate-x-4' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Topic Query Badge */}
        <div className="mt-3 rounded-xl border border-slate-800/80 bg-slate-950/40 p-2.5">
          <p className="text-xs text-slate-300 line-clamp-2 leading-relaxed">
            {channel.topicQuery}
          </p>
        </div>

        {/* Schedule & Article Info Grid */}
        <div className="mt-4 grid grid-cols-2 gap-2">
          {/* Next Scheduled Run */}
          <div className="col-span-2 rounded-xl border border-slate-800/70 bg-slate-800/30 p-2.5 flex items-center space-x-2.5">
            <Clock className="h-3.5 w-3.5 text-sky-400 flex-shrink-0" />
            <div className="min-w-0 flex-1">
              <span className="block text-[10px] font-medium uppercase tracking-wider text-slate-400">
                Next Run
              </span>
              <span className="block text-xs font-semibold text-sky-300 truncate">
                {nextRun}
              </span>
            </div>
          </div>

          {/* Articles Target */}
          <div className="rounded-xl border border-slate-800/70 bg-slate-800/30 p-2.5 flex items-center space-x-2">
            <Hash className="h-3.5 w-3.5 text-slate-400 flex-shrink-0" />
            <div>
              <span className="block text-[9px] font-medium uppercase tracking-wider text-slate-400">
                Articles
              </span>
              <span className="block text-xs font-semibold text-slate-200">
                {channel.articleCount} <span className="text-[10px] text-slate-500 font-normal">stories</span>
              </span>
            </div>
          </div>

          {/* Last Run Date */}
          <div className="rounded-xl border border-slate-800/70 bg-slate-800/30 p-2.5 flex items-center space-x-2">
            <Calendar className="h-3.5 w-3.5 text-slate-400 flex-shrink-0" />
            <div className="min-w-0">
              <span className="block text-[9px] font-medium uppercase tracking-wider text-slate-400">
                Last Run
              </span>
              <span className="block text-xs font-medium text-slate-300 truncate">
                {lastRun ?? 'Never'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Action Footer */}
      <div className="mt-5 pt-3.5 border-t border-slate-800/80 flex items-center justify-between gap-2">
        {/* Status Badge */}
        <div
          className={`inline-flex items-center space-x-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium shadow-sm ${statusConfig.badgeClass}`}
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
              className="inline-flex items-center space-x-1.5 rounded-lg border border-slate-700/80 bg-slate-800 hover:bg-slate-700/90 px-2.5 py-1.5 text-xs font-semibold text-slate-200 hover:text-white shadow-sm transition disabled:opacity-50"
            >
              {isRunPending ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin text-sky-400" />
              ) : (
                <Zap className="h-3.5 w-3.5 text-sky-400" />
              )}
              <span className="hidden sm:inline">{isRunPending ? 'Running…' : 'Run Now'}</span>
            </button>
          )}

          <Link
            href={`/channels/${channel.id}`}
            className="inline-flex items-center space-x-1.5 rounded-lg border border-slate-700/80 bg-slate-800 hover:bg-slate-700/90 px-2.5 py-1.5 text-xs font-medium text-slate-300 hover:text-white shadow-sm transition"
            title="Read Digest History"
          >
            <BookOpen className="h-3.5 w-3.5 text-indigo-400" />
            <span className="hidden sm:inline">History</span>
          </Link>

          <button
            onClick={() => onEdit(channel)}
            title="Edit Channel Settings"
            className="rounded-lg border border-slate-700/80 bg-slate-800 hover:bg-slate-700/90 p-1.5 text-slate-300 hover:text-white shadow-sm transition"
          >
            <Edit3 className="h-3.5 w-3.5" />
          </button>

          <button
            onClick={() => onDelete(channel)}
            title="Delete Channel"
            className="rounded-lg border border-rose-500/30 bg-rose-500/10 hover:bg-rose-500/20 p-1.5 text-rose-400 shadow-sm transition"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}
