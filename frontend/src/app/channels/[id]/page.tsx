'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { Header } from '../../../components/Header';
import { ToastContainer, ToastMessage } from '../../../components/Toast';
import { Channel, DigestRun, NewsItem } from '../../../types/channel';
import {
  fetchChannelById,
  fetchChannelRuns,
  fetchRunDetails,
  fetchRunItems,
  triggerRunNow,
  checkBackendHealth,
} from '../../../lib/api';
import {
  ArrowLeft,
  Calendar,
  CheckCircle2,
  Clock,
  ExternalLink,
  Globe,
  Hash,
  Loader2,
  Play,
  RefreshCw,
  Sparkles,
  XCircle,
  AlertTriangle,
  MailCheck,
  BookOpen,
  Layers,
} from 'lucide-react';

export default function ChannelDetailPage() {
  const params = useParams();
  const channelIdStr = params?.id as string;
  const channelId = parseInt(channelIdStr, 10);

  const [channel, setChannel] = useState<Channel | null>(null);
  const [runs, setRuns] = useState<DigestRun[]>([]);
  const [selectedRun, setSelectedRun] = useState<DigestRun | null>(null);
  const [newsItems, setNewsItems] = useState<NewsItem[]>([]);

  const [isLoadingChannel, setIsLoadingChannel] = useState(true);
  const [isLoadingRuns, setIsLoadingRuns] = useState(true);
  const [isLoadingItems, setIsLoadingItems] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isBackendOnline, setIsBackendOnline] = useState<boolean | null>(null);

  // Mobile layout tab: 'history' vs 'reader'
  const [mobileTab, setMobileTab] = useState<'history' | 'reader'>('reader');

  // Manual run trigger & polling state
  const [isTriggering, setIsTriggering] = useState(false);
  const [pendingRunId, setPendingRunId] = useState<number | null>(null);
  const [cooldown, setCooldown] = useState(0);

  // Toasts
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const addToast = (type: 'success' | 'error' | 'info', title: string, message?: string) => {
    const id = Date.now().toString();
    setToasts((prev) => [...prev, { id, type, title, message }]);
  };

  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  // Cooldown timer effect
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  // Load Channel Info & Runs History
  const loadChannelData = useCallback(async () => {
    if (isNaN(channelId)) return;
    setIsLoadingChannel(true);
    setIsLoadingRuns(true);
    setError(null);

    const health = await checkBackendHealth();
    setIsBackendOnline(health);

    try {
      const chData = await fetchChannelById(channelId);
      setChannel(chData);
      setIsLoadingChannel(false);

      const runsData = await fetchChannelRuns(channelId);
      setRuns(runsData);

      // Check if there is a currently pending run
      const activePending = runsData.find((r) => r.status === 'PENDING');
      if (activePending) {
        setPendingRunId(activePending.id);
      }

      // Automatically select the first run if none selected yet
      if (runsData.length > 0) {
        setSelectedRun((prev) => prev || runsData[0]);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load channel details');
    } finally {
      setIsLoadingChannel(false);
      setIsLoadingRuns(false);
    }
  }, [channelId]);

  useEffect(() => {
    loadChannelData();
  }, [loadChannelData]);

  // Load News Items when selectedRun changes
  useEffect(() => {
    if (!selectedRun) {
      setNewsItems([]);
      return;
    }

    let isMounted = true;
    const loadItems = async () => {
      setIsLoadingItems(true);
      try {
        const items = await fetchRunItems(selectedRun.id);
        if (isMounted) {
          setNewsItems(items);
        }
      } catch (err: any) {
        console.error('Failed to load run items:', err);
      } finally {
        if (isMounted) {
          setIsLoadingItems(false);
        }
      }
    };

    loadItems();
    return () => {
      isMounted = false;
    };
  }, [selectedRun?.id]);

  // Polling for Pending Run
  useEffect(() => {
    if (!pendingRunId) return;

    const pollInterval = setInterval(async () => {
      try {
        const updatedRun = await fetchRunDetails(pendingRunId);
        if (updatedRun.status !== 'PENDING') {
          setPendingRunId(null);
          addToast(
            updatedRun.status === 'SUCCESS' ? 'success' : 'error',
            updatedRun.status === 'SUCCESS' ? 'Digest Pipeline Completed' : 'Digest Pipeline Failed',
            updatedRun.status === 'SUCCESS'
              ? `Fetched & synthesized ${updatedRun.itemCount || 0} news stories!`
              : updatedRun.errorMessage || 'An error occurred during digest processing.'
          );
          const freshRuns = await fetchChannelRuns(channelId);
          setRuns(freshRuns);
          const currentFresh = freshRuns.find((r) => r.id === updatedRun.id);
          if (currentFresh) {
            setSelectedRun(currentFresh);
          }
        }
      } catch (err) {
        console.error('Error polling run status:', err);
      }
    }, 3000);

    return () => clearInterval(pollInterval);
  }, [pendingRunId, channelId]);

  // Handle Manual Trigger "Run Now"
  const handleRunNow = async () => {
    if (!channel || isTriggering || pendingRunId || cooldown > 0) return;
    setIsTriggering(true);
    try {
      const newRun = await triggerRunNow(channel.id);
      setPendingRunId(newRun.id);
      setCooldown(10);
      addToast(
        'info',
        'Digest Run Triggered',
        'TinyFish research and Groq synthesis pipeline is now executing...'
      );
      setRuns((prev) => [newRun, ...prev]);
      setSelectedRun(newRun);
      setMobileTab('reader');
    } catch (err: any) {
      addToast('error', 'Trigger Failed', err.message || 'Could not start manual run.');
    } finally {
      setIsTriggering(false);
    }
  };

  const formatRunDate = (dateStr?: string) => {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      return d.toLocaleString(undefined, {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch (e) {
      return dateStr;
    }
  };

  const renderStatusBadge = (status: string) => {
    const s = status.toUpperCase();
    if (s === 'SUCCESS') {
      return (
        <span className="inline-flex items-center space-x-1.5 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-semibold text-emerald-400">
          <CheckCircle2 className="h-3.5 w-3.5" />
          <span>Success</span>
        </span>
      );
    }
    if (s === 'FAILED') {
      return (
        <span className="inline-flex items-center space-x-1.5 rounded-full border border-rose-500/30 bg-rose-500/10 px-3 py-1 text-xs font-semibold text-rose-400">
          <XCircle className="h-3.5 w-3.5" />
          <span>Failed</span>
        </span>
      );
    }
    return (
      <span className="inline-flex items-center space-x-1.5 rounded-full border border-amber-500/30 bg-amber-500/10 px-3 py-1 text-xs font-semibold text-amber-400">
        <Loader2 className="h-3.5 w-3.5 animate-spin" />
        <span>Processing...</span>
      </span>
    );
  };

  return (
    <div className="min-h-screen bg-[#090a0f] text-zinc-100 antialiased font-sans selection:bg-blue-600 selection:text-white">
      {/* Background Gradient Orbs */}
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute -top-40 left-1/4 h-96 w-96 rounded-full bg-blue-600/15 blur-3xl" />
        <div className="absolute top-1/3 -right-40 h-96 w-96 rounded-full bg-sky-600/10 blur-3xl" />
      </div>

      <div className="relative z-10 flex min-h-screen flex-col">
        {/* Header */}
        <Header isBackendOnline={isBackendOnline} />

        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
          {/* Back Navigation Bar */}
          <div className="mb-6 flex items-center justify-between">
            <Link
              href="/"
              className="inline-flex items-center space-x-2 text-sm font-medium text-zinc-400 hover:text-blue-300 transition"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Back to All Channels</span>
            </Link>

            <button
              onClick={loadChannelData}
              disabled={isLoadingRuns}
              className="flex items-center space-x-2 rounded-xl bg-zinc-800 hover:bg-zinc-700/90 px-3.5 py-2 text-xs font-medium text-zinc-200 hover:text-white border border-zinc-700 border-b-[3px] border-b-zinc-950 shadow-[0_2px_5px_rgba(0,0,0,0.3)] transition disabled:opacity-50"
            >
              <RefreshCw className={`h-3.5 w-3.5 text-blue-400 ${isLoadingRuns ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>

          {/* Channel Header Banner */}
          {isLoadingChannel ? (
            <div className="mb-8 h-32 rounded-3xl border border-zinc-800 bg-zinc-900/50 p-6 animate-pulse" />
          ) : channel ? (
            <div className="mb-8 rounded-3xl border border-zinc-800/80 bg-gradient-to-r from-[#131522] via-[#0e1017] to-[#0a0b10] p-6 sm:p-8 shadow-xl">
              <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="flex items-center space-x-3">
                    <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                      {channel.name}
                    </h1>
                    <span
                      className={`inline-block h-3 w-3 rounded-full ${
                        channel.isActive ? 'bg-emerald-400 shadow-sm shadow-emerald-400/50' : 'bg-zinc-600'
                      }`}
                    />
                  </div>

                  <p className="mt-3 max-w-3xl text-xs sm:text-sm text-zinc-300 bg-zinc-950/70 rounded-xl p-3.5 border border-zinc-800 font-mono leading-relaxed">
                    {channel.topicQuery}
                  </p>

                  <div className="mt-4 flex flex-wrap items-center gap-4 text-xs text-zinc-400">
                    <div className="flex items-center space-x-1.5">
                      <Clock className="h-4 w-4 text-blue-400" />
                      <span>{channel.cronTime} daily</span>
                    </div>
                    <div className="flex items-center space-x-1.5">
                      <Globe className="h-4 w-4 text-sky-400" />
                      <span className="font-mono">{channel.timezone}</span>
                    </div>
                    <div className="flex items-center space-x-1.5">
                      <Hash className="h-4 w-4 text-blue-400" />
                      <span>{channel.articleCount} target stories</span>
                    </div>
                  </div>
                </div>

                {/* Manual Run Trigger Action */}
                <div className="flex-shrink-0">
                  <button
                    onClick={handleRunNow}
                    disabled={isTriggering || !!pendingRunId || cooldown > 0}
                    className="w-full sm:w-auto flex items-center justify-center space-x-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 px-6 py-3 text-sm font-semibold text-white border border-blue-500/40 border-b-[3px] border-b-blue-900 shadow-[0_4px_16px_rgba(37,99,235,0.4)] transition disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none"
                  >
                    {isTriggering || pendingRunId ? (
                      <Loader2 className="h-4 w-4 animate-spin text-white" />
                    ) : (
                      <Play className="h-4 w-4 text-blue-200 fill-blue-200" />
                    )}
                    <span>
                      {pendingRunId
                        ? 'Pipeline Executing...'
                        : cooldown > 0
                        ? `Cooldown (${cooldown}s)`
                        : 'Trigger Run Now'}
                    </span>
                  </button>
                </div>
              </div>
            </div>
          ) : null}

          {/* Mobile Segmented View Switcher (Visible on mobile only) */}
          <div className="mb-6 flex rounded-xl border border-zinc-800 bg-zinc-900/80 p-1 lg:hidden">
            <button
              onClick={() => setMobileTab('history')}
              className={`flex-1 flex items-center justify-center space-x-2 rounded-lg py-2 text-xs font-semibold transition ${
                mobileTab === 'history'
                  ? 'bg-blue-500/20 text-blue-300 border border-blue-500/30'
                  : 'text-zinc-400 hover:text-white'
              }`}
            >
              <Calendar className="h-3.5 w-3.5" />
              <span>History ({runs.length})</span>
            </button>

            <button
              onClick={() => setMobileTab('reader')}
              className={`flex-1 flex items-center justify-center space-x-2 rounded-lg py-2 text-xs font-semibold transition ${
                mobileTab === 'reader'
                  ? 'bg-blue-500/20 text-blue-300 border border-blue-500/30'
                  : 'text-zinc-400 hover:text-white'
              }`}
            >
              <BookOpen className="h-3.5 w-3.5" />
              <span>Digest Reader</span>
            </button>
          </div>

          {/* Main 2-Column Layout */}
          <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
            {/* Sidebar: History List (4 cols) */}
            <div
              className={`lg:col-span-4 ${
                mobileTab === 'history' ? 'block' : 'hidden lg:block'
              }`}
            >
              <div className="rounded-2xl border border-zinc-800 bg-zinc-900/60 p-5 backdrop-blur-sm shadow-[0_4px_20px_rgba(0,0,0,0.3)]">
                <div className="flex items-center justify-between border-b border-zinc-800/80 pb-4 mb-4">
                  <h2 className="text-base font-bold text-white flex items-center space-x-2">
                    <Calendar className="h-4 w-4 text-blue-400" />
                    <span>Digest History</span>
                  </h2>
                  <span className="rounded-full bg-zinc-800 px-2.5 py-0.5 text-xs text-zinc-400 font-medium">
                    {runs.length} runs
                  </span>
                </div>

                {isLoadingRuns ? (
                  <div className="space-y-3">
                    {[1, 2, 3].map((n) => (
                      <div key={n} className="h-16 rounded-xl bg-zinc-800/40 animate-pulse" />
                    ))}
                  </div>
                ) : runs.length === 0 ? (
                  <div className="py-8 text-center text-sm text-zinc-400">
                    No digest runs recorded yet. Click "Trigger Run Now" to generate your first digest!
                  </div>
                ) : (
                  <div className="space-y-2.5 max-h-[600px] overflow-y-auto pr-1 custom-scrollbar">
                    {runs.map((run) => {
                      const isSelected = selectedRun?.id === run.id;
                      return (
                        <button
                          key={run.id}
                          onClick={() => {
                            setSelectedRun(run);
                            setMobileTab('reader');
                          }}
                          className={`w-full text-left rounded-xl p-4 transition duration-150 border ${
                            isSelected
                              ? 'border-blue-500/60 bg-blue-500/10 shadow-md shadow-blue-500/5'
                              : 'border-zinc-800/70 bg-zinc-950/40 hover:border-zinc-700 hover:bg-zinc-900/90'
                          }`}
                        >
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-semibold text-zinc-200">
                              {formatRunDate(run.runAt)}
                            </span>
                            {renderStatusBadge(run.status)}
                          </div>

                          <div className="mt-2 flex items-center justify-between text-[11px] text-zinc-400">
                            <span>{run.itemCount ?? 0} stories</span>
                            {run.emailSent && (
                              <span className="flex items-center space-x-1 text-emerald-400 font-medium">
                                <MailCheck className="h-3 w-3" />
                                <span>Email Delivered</span>
                              </span>
                            )}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            {/* Main Reader View (8 cols) */}
            <div
              className={`lg:col-span-8 ${
                mobileTab === 'reader' ? 'block' : 'hidden lg:block'
              }`}
            >
              {selectedRun ? (
                <div className="rounded-2xl border border-zinc-800 bg-zinc-900/60 p-6 sm:p-8 backdrop-blur-sm shadow-[0_4px_20px_rgba(0,0,0,0.3)]">
                  {/* Selected Run Top Header */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-zinc-800/80 pb-6 mb-6 gap-4">
                    <div>
                      <div className="flex items-center space-x-3">
                        <h2 className="text-xl font-bold text-white">Digest Reader</h2>
                        {renderStatusBadge(selectedRun.status)}
                      </div>
                      <p className="mt-1 text-xs text-zinc-400">
                        Run Date: <span className="font-semibold text-zinc-300">{formatRunDate(selectedRun.runAt)}</span>
                      </p>
                    </div>

                    {selectedRun.emailSent && (
                      <div className="inline-flex items-center space-x-2 rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-3.5 py-2 text-xs font-semibold text-emerald-400">
                        <MailCheck className="h-4 w-4" />
                        <span>Delivered to Email Inbox</span>
                      </div>
                    )}
                  </div>

                  {/* Pending State Banner with Step Progress */}
                  {selectedRun.status === 'PENDING' && (
                    <div className="my-8 flex flex-col items-center justify-center rounded-2xl border border-amber-500/30 bg-amber-500/10 p-8 text-center">
                      <Loader2 className="h-10 w-10 animate-spin text-amber-400 mb-2" />
                      <h3 className="text-lg font-bold text-amber-200">
                        Digest Pipeline Executing
                      </h3>

                      {/* 3 Step Execution Progress Bar */}
                      <div className="mt-6 grid grid-cols-3 gap-2 w-full max-w-md text-left">
                        <div className="rounded-lg bg-amber-900/40 p-2 border border-amber-500/20">
                          <span className="block text-[10px] font-bold text-amber-400 uppercase">Step 1</span>
                          <span className="block text-[11px] text-amber-200">TinyFish Fetch</span>
                        </div>
                        <div className="rounded-lg bg-amber-900/40 p-2 border border-amber-500/20">
                          <span className="block text-[10px] font-bold text-amber-400 uppercase">Step 2</span>
                          <span className="block text-[11px] text-amber-200">Groq Synthesis</span>
                        </div>
                        <div className="rounded-lg bg-amber-900/40 p-2 border border-amber-500/20">
                          <span className="block text-[10px] font-bold text-amber-400 uppercase">Step 3</span>
                          <span className="block text-[11px] text-amber-200">Email Delivery</span>
                        </div>
                      </div>

                      <span className="mt-5 text-[11px] font-mono text-amber-400/90">
                        Polling for status updates every 3 seconds...
                      </span>
                    </div>
                  )}

                  {/* Failed State Banner */}
                  {selectedRun.status === 'FAILED' && (
                    <div className="my-6 rounded-2xl border border-rose-500/30 bg-rose-500/10 p-6 text-rose-300">
                      <div className="flex items-start space-x-3">
                        <AlertTriangle className="h-6 w-6 flex-shrink-0 text-rose-400 mt-0.5" />
                        <div className="flex-1">
                          <h4 className="text-base font-bold text-rose-200">Digest Run Failed</h4>
                          <p className="mt-1 text-xs text-rose-200/80 font-mono">
                            {selectedRun.errorMessage || 'An error occurred during pipeline execution.'}
                          </p>
                          <button
                            onClick={handleRunNow}
                            className="mt-4 inline-flex items-center space-x-2 rounded-lg bg-rose-600 px-4 py-2 text-xs font-semibold text-white hover:bg-rose-500 transition"
                          >
                            <RefreshCw className="h-3.5 w-3.5" />
                            <span>Retry Pipeline Run</span>
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Success / Articles Reading View */}
                  {selectedRun.status === 'SUCCESS' && (
                    <div className="max-w-3xl">
                      {isLoadingItems ? (
                        <div className="space-y-4">
                          {[1, 2, 3].map((n) => (
                            <div key={n} className="h-36 rounded-2xl bg-zinc-800/40 animate-pulse" />
                          ))}
                        </div>
                      ) : newsItems.length === 0 ? (
                        <div className="py-12 text-center text-sm text-zinc-400">
                          No news articles were returned for this digest run.
                        </div>
                      ) : (
                        <div className="space-y-6">
                          {newsItems.map((item) => (
                            <article
                              key={item.id}
                              className="group relative rounded-2xl border border-zinc-800/80 bg-zinc-950/70 p-6 transition duration-200 hover:border-blue-500/40 hover:shadow-lg hover:shadow-blue-500/5"
                            >
                              <div className="flex items-start justify-between gap-4">
                                <div className="flex items-start space-x-3 min-w-0">
                                  {/* Rank Badge */}
                                  <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg bg-blue-500/10 text-xs font-extrabold text-blue-400 border border-blue-500/20">
                                    #{item.rankPosition}
                                  </span>

                                  <div className="min-w-0 flex-1">
                                    {/* Article Title Link */}
                                    <a
                                      href={item.url}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="text-base font-bold text-zinc-100 group-hover:text-blue-300 transition inline-flex items-center space-x-1.5 leading-snug"
                                    >
                                      <span className="break-words">{item.title}</span>
                                      <ExternalLink className="h-4 w-4 opacity-70 group-hover:opacity-100 flex-shrink-0 ml-1" />
                                    </a>

                                    {/* Publisher & Published Date */}
                                    <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-zinc-400">
                                      {item.sourceName && (
                                        <span className="rounded-md bg-zinc-800/80 px-2 py-0.5 font-medium text-zinc-300 border border-zinc-700/50">
                                          {item.sourceName}
                                        </span>
                                      )}
                                      {item.publishedAt && (
                                        <span>
                                          Published: {new Date(item.publishedAt).toLocaleDateString()}
                                        </span>
                                      )}
                                    </div>
                                  </div>
                                </div>
                              </div>

                              {/* Synthesized 'Why It Matters' Blurb */}
                              {item.summaryBlurb && (
                                <div className="mt-4 rounded-xl border border-blue-500/20 bg-blue-500/5 p-4 text-xs text-zinc-300 leading-relaxed">
                                  <div className="mb-1.5 flex items-center space-x-1.5 font-semibold text-blue-400">
                                    <Sparkles className="h-3.5 w-3.5" />
                                    <span>Why It Matters</span>
                                  </div>
                                  <p className="text-zinc-200 leading-normal font-sans">
                                    {item.summaryBlurb}
                                  </p>
                                </div>
                              )}
                            </article>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ) : (
                <div className="flex h-64 items-center justify-center rounded-2xl border border-dashed border-zinc-800 bg-zinc-900/30 p-8 text-center text-sm text-zinc-400">
                  Select a digest run from the history list to read its articles.
                </div>
              )}
            </div>
          </div>
        </main>
      </div>

      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
    </div>
  );
}
