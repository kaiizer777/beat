'use client';

import React, { useState, useEffect } from 'react';
import { X, Clock, Hash, AlertCircle, Sparkles, Zap, Globe, Radio, Check } from 'lucide-react';
import { Channel, ChannelFormData } from '../types/channel';
import { ApiError } from '../lib/api';

interface ChannelFormModalProps {
  isOpen: boolean;
  editingChannel?: Channel | null;
  onClose: () => void;
  onSubmit: (data: ChannelFormData) => Promise<void>;
}

const TIMEZONE_OPTIONS = [
  { value: 'Asia/Kolkata', label: 'India Standard Time (IST - UTC+5:30)' },
  { value: 'UTC', label: 'Coordinated Universal Time (UTC)' },
  { value: 'America/New_York', label: 'US Eastern (EST/EDT - UTC-5)' },
  { value: 'America/Los_Angeles', label: 'US Pacific (PST/PDT - UTC-8)' },
  { value: 'Europe/London', label: 'British Time (GMT/BST - UTC+0)' },
  { value: 'Asia/Tokyo', label: 'Japan Standard Time (JST - UTC+9)' },
  { value: 'Australia/Sydney', label: 'Australian Eastern (AEST - UTC+10)' },
];

const ARTICLE_PRESETS = [5, 10, 15, 20, 25];

export function ChannelFormModal({
  isOpen,
  editingChannel,
  onClose,
  onSubmit,
}: ChannelFormModalProps) {
  const [name, setName] = useState('');
  const [topicQuery, setTopicQuery] = useState('');
  const [articleCount, setArticleCount] = useState(15);
  const [cronTime, setCronTime] = useState('08:00');
  const [timezone, setTimezone] = useState('Asia/Kolkata');
  const [isActive, setIsActive] = useState(true);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (editingChannel) {
      setName(editingChannel.name || '');
      setTopicQuery(editingChannel.topicQuery || '');
      setArticleCount(editingChannel.articleCount || 15);
      const timeStr = editingChannel.cronTime ? editingChannel.cronTime.substring(0, 5) : '08:00';
      setCronTime(timeStr);
      setTimezone(editingChannel.timezone || 'Asia/Kolkata');
      setIsActive(editingChannel.isActive ?? true);
    } else {
      setName('');
      setTopicQuery('');
      setArticleCount(15);
      setCronTime('08:00');
      setTimezone('Asia/Kolkata');
      setIsActive(true);
    }
    setGlobalError(null);
    setFieldErrors({});
  }, [editingChannel, isOpen]);

  if (!isOpen) return null;

  // Real-time inline field validation
  const validateField = (fieldName: string, value: any) => {
    const nextErrors = { ...fieldErrors };

    if (fieldName === 'name') {
      if (!value.trim()) {
        nextErrors.name = 'Channel name is required';
      } else {
        delete nextErrors.name;
      }
    }

    if (fieldName === 'topicQuery') {
      if (!value.trim()) {
        nextErrors.topicQuery = 'Research prompt query is required';
      } else if (value.trim().length < 5) {
        nextErrors.topicQuery = 'Research prompt should be at least 5 characters';
      } else {
        delete nextErrors.topicQuery;
      }
    }

    setFieldErrors(nextErrors);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setGlobalError(null);

    const localErrors: Record<string, string> = {};
    if (!name.trim()) localErrors.name = 'Channel name is required';
    if (!topicQuery.trim()) localErrors.topicQuery = 'Research prompt query is required';
    if (articleCount < 5 || articleCount > 25) localErrors.articleCount = 'Must be between 5 and 25';
    if (!cronTime) localErrors.cronTime = 'Schedule time is required';

    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors);
      setGlobalError('Please resolve the highlighted field errors.');
      return;
    }

    setIsSubmitting(true);
    try {
      const formattedTime = cronTime.length === 5 ? `${cronTime}:00` : cronTime;
      await onSubmit({
        name: name.trim(),
        topicQuery: topicQuery.trim(),
        articleCount,
        cronTime: formattedTime,
        timezone,
        isActive,
      });
      onClose();
    } catch (err: any) {
      if (err instanceof ApiError) {
        setGlobalError(err.message || 'Validation failed on backend');
        if (err.fieldErrors) setFieldErrors(err.fieldErrors);
      } else {
        setGlobalError(err.message || 'An unexpected error occurred. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto bg-black/75 backdrop-blur-md animate-fade-in font-sans">
      <div className="relative my-8 w-full max-w-xl rounded-2xl border border-slate-800 bg-[#0d131f] p-6 sm:p-7 shadow-2xl text-slate-100">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-slate-800/80 pb-4 mb-5">
          <div className="flex items-center space-x-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-800 text-sky-400 border border-slate-700/80 shadow-sm">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white tracking-tight">
                {editingChannel ? 'Edit News Channel' : 'Create Research Channel'}
              </h2>
              <p className="text-xs text-slate-400">
                Configure schedule & topic preferences for automatic digests
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="rounded-lg border border-slate-800 bg-slate-900/80 p-1.5 text-slate-400 hover:text-white hover:border-slate-700 transition disabled:opacity-50"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Global Error Banner */}
        {globalError && (
          <div className="mb-5 flex items-start space-x-3 rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-xs text-rose-300">
            <AlertCircle className="h-4 w-4 text-rose-400 flex-shrink-0 mt-0.5" />
            <span>{globalError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Channel Name */}
          <div>
            <label htmlFor="channel-name" className="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">
              Channel Name <span className="text-sky-400">*</span>
            </label>
            <input
              id="channel-name"
              type="text"
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                validateField('name', e.target.value);
              }}
              onBlur={(e) => validateField('name', e.target.value)}
              placeholder="e.g. AI & LLM Innovations"
              className={`w-full rounded-xl border bg-slate-950/60 px-4 py-2.5 text-sm text-white placeholder-slate-500 shadow-sm focus:outline-none transition ${
                fieldErrors.name
                  ? 'border-rose-500/60 focus:ring-1 focus:ring-rose-500/20'
                  : 'border-slate-800 focus:border-sky-500 focus:ring-1 focus:ring-sky-500/20'
              }`}
            />
            {fieldErrors.name && (
              <p className="mt-1.5 text-xs text-rose-400 font-medium">{fieldErrors.name}</p>
            )}
          </div>

          {/* Research Prompt Query */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label htmlFor="topic-query" className="block text-xs font-semibold uppercase tracking-wider text-slate-300">
                Research Prompt <span className="text-sky-400">*</span>
              </label>
              <span className="text-[11px] text-slate-500">TinyFish multi-query search target</span>
            </div>
            <textarea
              id="topic-query"
              rows={3}
              value={topicQuery}
              onChange={(e) => {
                setTopicQuery(e.target.value);
                validateField('topicQuery', e.target.value);
              }}
              onBlur={(e) => validateField('topicQuery', e.target.value)}
              placeholder="Specify keywords, domains, or research targets (e.g. 'AI Hardware, GPU architectures, semiconductor supply chain')..."
              className={`w-full rounded-xl border bg-slate-950/60 p-3 text-xs text-white placeholder-slate-500 shadow-sm focus:outline-none transition font-sans leading-relaxed ${
                fieldErrors.topicQuery
                  ? 'border-rose-500/60 focus:ring-1 focus:ring-rose-500/20'
                  : 'border-slate-800 focus:border-sky-500 focus:ring-1 focus:ring-sky-500/20'
              }`}
            />
            {fieldErrors.topicQuery && (
              <p className="mt-1.5 text-xs text-rose-400 font-medium">{fieldErrors.topicQuery}</p>
            )}
          </div>

          {/* Target Article Count with Presets & Slider */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="flex items-center space-x-1.5 text-xs font-semibold uppercase tracking-wider text-slate-300">
                <Hash className="h-3.5 w-3.5 text-sky-400" />
                <span>Articles Per Digest</span>
              </label>
              <span className="rounded-full bg-sky-500/10 px-3 py-0.5 text-xs font-bold text-sky-300 border border-sky-500/20">
                {articleCount} articles
              </span>
            </div>

            {/* Quick Presets */}
            <div className="flex items-center gap-2 mb-3">
              <span className="text-[11px] text-slate-500 mr-1">Presets:</span>
              {ARTICLE_PRESETS.map((count) => (
                <button
                  key={count}
                  type="button"
                  onClick={() => setArticleCount(count)}
                  className={`rounded-lg px-2.5 py-1 text-xs font-semibold transition ${
                    articleCount === count
                      ? 'bg-slate-800 text-sky-300 border border-slate-700 shadow-sm'
                      : 'bg-slate-900/80 text-slate-400 hover:text-white hover:bg-slate-800 border border-slate-800'
                  }`}
                >
                  {count}
                </button>
              ))}
            </div>

            {/* Range Slider */}
            <input
              type="range"
              min={5}
              max={25}
              step={1}
              value={articleCount}
              onChange={(e) => setArticleCount(Number(e.target.value))}
              className="w-full accent-sky-500 cursor-pointer"
            />
          </div>

          {/* Schedule Time & Timezone Grid */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {/* Daily Delivery Time */}
            <div>
              <label htmlFor="cron-time" className="flex items-center space-x-1.5 text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">
                <Clock className="h-3.5 w-3.5 text-sky-400" />
                <span>Daily Run Time</span>
              </label>
              <input
                id="cron-time"
                type="time"
                value={cronTime}
                onChange={(e) => setCronTime(e.target.value)}
                className="w-full rounded-xl border border-slate-800 bg-slate-950/60 px-3.5 py-2.5 text-sm text-white focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500/20 transition color-scheme-dark"
              />
            </div>

            {/* Timezone Selector */}
            <div>
              <label htmlFor="timezone" className="flex items-center space-x-1.5 text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">
                <Globe className="h-3.5 w-3.5 text-slate-400" />
                <span>Timezone</span>
              </label>
              <select
                id="timezone"
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                className="w-full rounded-xl border border-slate-800 bg-slate-950/60 px-3 py-2.5 text-xs text-white focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500/20 transition"
              >
                {TIMEZONE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value} className="bg-slate-900 text-white">
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Active Schedule Toggle */}
          <div
            className={`flex items-center justify-between rounded-xl border p-4 transition ${
              isActive
                ? 'border-slate-700 bg-slate-900/60'
                : 'border-slate-800 bg-slate-950/40'
            }`}
          >
            <div className="flex items-center space-x-3">
              <div
                className={`flex h-9 w-9 items-center justify-center rounded-xl border transition ${
                  isActive
                    ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                    : 'bg-slate-800 text-slate-500 border-slate-700'
                }`}
              >
                <Radio className="h-4 w-4" />
              </div>
              <div>
                <p className="text-xs font-bold text-white">Automated Schedule Active</p>
                <p className="text-[11px] text-slate-400">
                  {isActive
                    ? 'Digest pipeline will run automatically every day'
                    : 'Automatic triggers paused until activated'}
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setIsActive(!isActive)}
              className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border border-slate-700 transition-colors duration-200 ease-in-out focus:outline-none ${
                isActive ? 'bg-sky-500' : 'bg-slate-800'
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition duration-200 ease-in-out ${
                  isActive ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          {/* Modal Action Buttons */}
          <div className="flex items-center justify-end space-x-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="rounded-xl border border-slate-800 bg-slate-900/80 px-5 py-2.5 text-xs font-semibold text-slate-300 hover:bg-slate-800 hover:text-white transition shadow-sm"
            >
              Cancel
            </button>

            <button
              type="submit"
              disabled={isSubmitting}
              className="flex items-center space-x-2 rounded-xl bg-slate-800 hover:bg-slate-700/90 px-6 py-2.5 text-xs font-bold text-slate-200 hover:text-white border border-slate-700/80 shadow-sm transition disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-sky-400 border-t-transparent" />
                  <span>Saving Channel...</span>
                </>
              ) : (
                <>
                  <Zap className="h-3.5 w-3.5 text-sky-400" />
                  <span>{editingChannel ? 'Update Channel' : 'Create Channel'}</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
