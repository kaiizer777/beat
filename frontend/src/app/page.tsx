'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { Header } from '../components/Header';
import { ChannelCard } from '../components/ChannelCard';
import { ChannelFormModal } from '../components/ChannelFormModal';
import { ConfirmModal } from '../components/ConfirmModal';
import { ToastContainer, ToastMessage } from '../components/Toast';
import { Channel, ChannelFormData } from '../types/channel';
import {
  fetchChannels,
  createChannel,
  updateChannel,
  deleteChannel,
  triggerRunNow,
  checkBackendHealth,
} from '../lib/api';
import {
  Search,
  Plus,
  RefreshCw,
  Radio,
  SlidersHorizontal,
  Layers,
  AlertTriangle,
  Zap,
} from 'lucide-react';

export default function Home() {
  const [channels, setChannels] = useState<Channel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isBackendOnline, setIsBackendOnline] = useState<boolean | null>(null);
  const [pendingChannelIds, setPendingChannelIds] = useState<Set<number>>(new Set());

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState('');
  const [filterActiveOnly, setFilterActiveOnly] = useState(false);

  // Modal states
  const [isFormModalOpen, setIsFormModalOpen] = useState(false);
  const [editingChannel, setEditingChannel] = useState<Channel | null>(null);

  // Confirm delete modal state
  const [deletingChannel, setDeletingChannel] = useState<Channel | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Toasts
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const addToast = (type: 'success' | 'error' | 'info', title: string, message?: string) => {
    const id = Date.now().toString();
    setToasts((prev) => [...prev, { id, type, title, message }]);
  };

  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  // Check health and load channels
  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    const health = await checkBackendHealth();
    setIsBackendOnline(health);

    try {
      const data = await fetchChannels();
      setChannels(data);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch channels from backend.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Handle Manual Run Trigger directly from card
  const handleRunNow = async (channel: Channel) => {
    if (pendingChannelIds.has(channel.id)) return;

    setPendingChannelIds((prev) => new Set(prev).add(channel.id));
    addToast(
      'info',
      'Digest Run Started',
      `Executing research & LLM pipeline for "${channel.name}"...`
    );

    try {
      await triggerRunNow(channel.id);
      // Update channel card status optimistically
      setChannels((prev) =>
        prev.map((c) =>
          c.id === channel.id
            ? { ...c, lastRunStatus: 'PENDING', lastRunAt: new Date().toISOString() }
            : c
        )
      );
    } catch (err: any) {
      setPendingChannelIds((prev) => {
        const next = new Set(prev);
        next.delete(channel.id);
        return next;
      });
      addToast('error', 'Trigger Failed', err.message || 'Could not start manual digest run.');
    }
  };

  // Handle Create or Update submission
  const handleFormSubmit = async (formData: ChannelFormData) => {
    if (editingChannel) {
      // Update
      const updated = await updateChannel(editingChannel.id, formData);
      setChannels((prev) =>
        prev.map((c) => (c.id === updated.id ? updated : c))
      );
      addToast('success', 'Channel Updated', `"${updated.name}" updated successfully.`);
    } else {
      // Create
      const created = await createChannel(formData);
      setChannels((prev) => [created, ...prev]);
      addToast('success', 'Channel Created', `"${created.name}" created successfully.`);
    }
  };

  // Handle Toggle Active switch directly on card
  const handleToggleActive = async (channel: Channel, nextState: boolean) => {
    // Optimistic UI update
    setChannels((prev) =>
      prev.map((c) => (c.id === channel.id ? { ...c, isActive: nextState } : c))
    );

    try {
      const updated = await updateChannel(channel.id, {
        name: channel.name,
        topicQuery: channel.topicQuery,
        articleCount: channel.articleCount,
        cronTime: channel.cronTime,
        timezone: channel.timezone,
        isActive: nextState,
      });
      setChannels((prev) =>
        prev.map((c) => (c.id === updated.id ? updated : c))
      );
      addToast(
        'info',
        nextState ? 'Channel Activated' : 'Channel Deactivated',
        `"${channel.name}" schedule is now ${nextState ? 'active' : 'inactive'}.`
      );
    } catch (err: any) {
      // Revert on error
      setChannels((prev) =>
        prev.map((c) => (c.id === channel.id ? { ...c, isActive: channel.isActive } : c))
      );
      addToast('error', 'Update Failed', err.message || 'Failed to update channel status.');
    }
  };

  // Handle Delete Confirmation
  const handleConfirmDelete = async () => {
    if (!deletingChannel) return;
    setIsDeleting(true);
    try {
      await deleteChannel(deletingChannel.id);
      setChannels((prev) => prev.filter((c) => c.id !== deletingChannel.id));
      addToast('success', 'Channel Deleted', `"${deletingChannel.name}" was removed.`);
      setDeletingChannel(null);
    } catch (err: any) {
      addToast('error', 'Delete Failed', err.message || 'Could not delete channel.');
    } finally {
      setIsDeleting(false);
    }
  };

  // Filter channels based on search and active toggle
  const filteredChannels = channels.filter((c) => {
    const matchesSearch =
      c.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      c.topicQuery.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesActive = filterActiveOnly ? c.isActive : true;
    return matchesSearch && matchesActive;
  });

  const totalChannels = channels.length;
  const activeChannels = channels.filter((c) => c.isActive).length;

  return (
    <div className="min-h-screen bg-black text-slate-100 antialiased font-sans selection:bg-cyan-500 selection:text-white relative">
      {/* Enhanced Ambient Background System */}
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        {/* Subtle grid pattern with radial spotlight mask */}
        <div className="absolute inset-0 bg-grid-pattern [mask-image:radial-gradient(ellipse_80%_60%_at_50%_0%,#000_65%,transparent_100%)] opacity-50" />
        
        {/* Center-top ambient cyan spotlight cone */}
        <div className="absolute -top-40 left-1/2 -translate-x-1/2 h-[550px] w-[950px] rounded-full bg-cyan-500/10 blur-[130px] pointer-events-none" />
        
        {/* Deep ambient peripheral atmospheric glows */}
        <div className="absolute top-1/4 -right-48 h-[400px] w-[400px] rounded-full bg-blue-600/10 blur-[140px] pointer-events-none" />
        <div className="absolute top-1/2 -left-48 h-[400px] w-[400px] rounded-full bg-cyan-600/8 blur-[140px] pointer-events-none" />
      </div>

      <div className="relative z-10 flex min-h-screen flex-col">
        {/* Header */}
        <Header
          onOpenCreateModal={() => {
            setEditingChannel(null);
            setIsFormModalOpen(true);
          }}
          isBackendOnline={isBackendOnline}
        />

        {/* Main Content */}
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
          {/* Top Info Banner & Stats */}
          <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div className="flex items-center space-x-4 rounded-2xl border border-white/[0.07] bg-slate-900/40 p-5 backdrop-blur-md shadow-[0_8px_25px_rgba(0,0,0,0.4)]">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                <Radio className="h-6 w-6" />
              </div>
              <div>
                <p className="text-xs uppercase tracking-wider text-slate-400">Total Channels</p>
                <p className="text-2xl font-bold text-white">{totalChannels}</p>
              </div>
            </div>

            <div className="flex items-center space-x-4 rounded-2xl border border-white/[0.07] bg-slate-900/40 p-5 backdrop-blur-md shadow-[0_8px_25px_rgba(0,0,0,0.4)]">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <Zap className="h-6 w-6" />
              </div>
              <div>
                <p className="text-xs uppercase tracking-wider text-slate-400">Active Schedules</p>
                <p className="text-2xl font-bold text-white">{activeChannels}</p>
              </div>
            </div>

            <div className="flex items-center space-x-4 rounded-2xl border border-white/[0.07] bg-slate-900/40 p-5 backdrop-blur-md shadow-[0_8px_25px_rgba(0,0,0,0.4)]">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20">
                <Layers className="h-6 w-6" />
              </div>
              <div>
                <p className="text-xs uppercase tracking-wider text-slate-400">Execution Engine</p>
                <p className="text-sm font-semibold text-slate-200">Spring TaskScheduler</p>
              </div>
            </div>
          </div>

          {/* Search, Filter & Controls Bar */}
          <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="relative flex-1 max-w-md">
              <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search channels by name or topic..."
                className="w-full rounded-xl border border-white/[0.08] bg-slate-950/60 pl-10 pr-4 py-2.5 text-sm text-white placeholder-slate-500 shadow-inner backdrop-blur-md focus:border-cyan-500/60 focus:outline-none focus:ring-2 focus:ring-cyan-500/20"
              />
            </div>

            <div className="flex items-center space-x-3">
              <button
                onClick={() => setFilterActiveOnly(!filterActiveOnly)}
                className={`flex items-center space-x-2 rounded-xl border px-4 py-2.5 text-xs font-medium transition ${
                  filterActiveOnly
                    ? 'border-cyan-500/50 bg-cyan-500/10 text-cyan-300'
                    : 'border-slate-800 bg-slate-900/80 text-slate-400 hover:text-white'
                }`}
              >
                <SlidersHorizontal className="h-3.5 w-3.5" />
                <span>{filterActiveOnly ? 'Showing Active Only' : 'Filter Active'}</span>
              </button>

              <button
                onClick={loadData}
                disabled={isLoading}
                title="Refresh channel list"
                className="flex items-center justify-center rounded-xl border border-slate-800 bg-slate-900/80 p-2.5 text-slate-400 hover:border-slate-700 hover:text-white transition disabled:opacity-50"
              >
                <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
              </button>
            </div>
          </div>

          {/* Error Banner */}
          {error && (
            <div className="mb-8 flex items-start space-x-4 rounded-2xl border border-rose-500/20 bg-rose-500/10 p-6 text-rose-300">
              <AlertTriangle className="h-6 w-6 flex-shrink-0 text-rose-400 mt-0.5" />
              <div className="flex-1">
                <h3 className="text-base font-semibold">Backend Unreachable</h3>
                <p className="mt-1 text-sm text-rose-200/80">{error}</p>
                <p className="mt-2 text-xs text-slate-400">
                  Ensure the Spring Boot backend is running (e.g., at <code className="font-mono text-cyan-400">http://localhost:8080</code> or your public Oracle VM URL).
                </p>
                <button
                  onClick={loadData}
                  className="mt-4 rounded-lg bg-rose-600 px-4 py-2 text-xs font-semibold text-white hover:bg-rose-500 transition"
                >
                  Retry Connection
                </button>
              </div>
            </div>
          )}

          {/* Loading State Skeletons */}
          {isLoading && !error && (
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {[1, 2, 3].map((n) => (
                <div
                  key={n}
                  className="h-64 rounded-2xl border border-slate-800/80 bg-slate-900/50 p-6 animate-pulse"
                >
                  <div className="h-6 w-3/4 rounded-lg bg-slate-800" />
                  <div className="mt-4 h-12 w-full rounded-xl bg-slate-800/60" />
                  <div className="mt-6 grid grid-cols-2 gap-3">
                    <div className="h-10 rounded-lg bg-slate-800/40" />
                    <div className="h-10 rounded-lg bg-slate-800/40" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Empty State */}
          {!isLoading && !error && filteredChannels.length === 0 && (
            totalChannels === 0 ? (
              <div className="my-8 flex flex-col items-center justify-center rounded-3xl border border-slate-800 bg-gradient-to-b from-slate-900/80 to-slate-950 p-8 sm:p-12 text-center shadow-xl backdrop-blur-md">
                <div className="flex h-20 w-20 items-center justify-center rounded-3xl bg-gradient-to-tr from-cyan-500/20 to-blue-500/20 text-cyan-400 border border-cyan-500/30 shadow-lg shadow-cyan-500/10">
                  <Radio className="h-10 w-10 animate-pulse" />
                </div>
                <h3 className="mt-6 text-2xl font-extrabold text-white sm:text-3xl tracking-tight">
                  Welcome to Beat Research
                </h3>
                <p className="mt-3 max-w-lg text-sm sm:text-base text-slate-300 leading-relaxed">
                  You don&apos;t have any research channels set up yet. Get started by creating your first personalized news topic channel below.
                </p>

                {/* 3 Step Onboarding Flow */}
                <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-3 max-w-3xl w-full text-left">
                  <div className="rounded-2xl border border-slate-800/80 bg-slate-900/50 p-4">
                    <div className="flex items-center space-x-2 text-cyan-400 font-bold text-xs uppercase tracking-wider mb-1">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-cyan-500/20 text-[11px]">1</span>
                      <span>Choose Topic</span>
                    </div>
                    <p className="text-xs text-slate-400">Specify any keyword or query, like &quot;AI Hardware&quot; or &quot;Biotech&quot;.</p>
                  </div>

                  <div className="rounded-2xl border border-slate-800/80 bg-slate-900/50 p-4">
                    <div className="flex items-center space-x-2 text-cyan-400 font-bold text-xs uppercase tracking-wider mb-1">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-cyan-500/20 text-[11px]">2</span>
                      <span>Set Schedule</span>
                    </div>
                    <p className="text-xs text-slate-400">Pick your daily delivery time, timezone, and article count.</p>
                  </div>

                  <div className="rounded-2xl border border-slate-800/80 bg-slate-900/50 p-4">
                    <div className="flex items-center space-x-2 text-cyan-400 font-bold text-xs uppercase tracking-wider mb-1">
                      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-cyan-500/20 text-[11px]">3</span>
                      <span>Receive Digests</span>
                    </div>
                    <p className="text-xs text-slate-400">Beat automatically fetches, ranks, and emails synthesized reports.</p>
                  </div>
                </div>

                <button
                  onClick={() => {
                    setEditingChannel(null);
                    setIsFormModalOpen(true);
                  }}
                  className="mt-8 flex items-center space-x-2 rounded-2xl bg-gradient-to-r from-cyan-500 to-blue-600 px-6 py-3.5 text-base font-semibold text-white shadow-xl shadow-cyan-500/25 hover:from-cyan-400 hover:to-blue-500 transition transform hover:-translate-y-0.5"
                >
                  <Plus className="h-5 w-5" />
                  <span>Create Your First Channel</span>
                </button>
              </div>
            ) : (
              <div className="my-12 flex flex-col items-center justify-center rounded-3xl border border-dashed border-slate-800 bg-slate-900/40 p-12 text-center">
                <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                  <Radio className="h-8 w-8" />
                </div>
                <h3 className="mt-4 text-xl font-bold text-white">No channels match filter</h3>
                <p className="mt-2 max-w-md text-sm text-slate-400 leading-relaxed">
                  Try clearing your search query or active filter to see all channels.
                </p>
                <button
                  onClick={() => {
                    setSearchQuery('');
                    setFilterActiveOnly(false);
                  }}
                  className="mt-6 flex items-center space-x-2 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 px-5 py-2.5 text-sm font-medium text-white shadow-lg shadow-cyan-500/20 hover:from-cyan-400 hover:to-blue-500 transition"
                >
                  <Plus className="h-4 w-4" />
                  <span>Clear Filters</span>
                </button>
              </div>
            )
          )}

          {/* Channel Cards Grid */}
          {!isLoading && !error && filteredChannels.length > 0 && (
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {filteredChannels.map((channel) => (
                <ChannelCard
                  key={channel.id}
                  channel={channel}
                  onEdit={(ch) => {
                    setEditingChannel(ch);
                    setIsFormModalOpen(true);
                  }}
                  onDelete={(ch) => setDeletingChannel(ch)}
                  onToggleActive={handleToggleActive}
                  onRunNow={handleRunNow}
                  isRunPending={pendingChannelIds.has(channel.id)}
                />
              ))}
            </div>
          )}
        </main>
      </div>

      {/* Create / Edit Form Modal */}
      <ChannelFormModal
        isOpen={isFormModalOpen}
        editingChannel={editingChannel}
        onClose={() => {
          setIsFormModalOpen(false);
          setEditingChannel(null);
        }}
        onSubmit={handleFormSubmit}
      />

      {/* Delete Confirmation Modal */}
      <ConfirmModal
        isOpen={!!deletingChannel}
        title="Delete Channel"
        message={`Are you sure you want to delete "${deletingChannel?.name}"? This will permanently remove the channel definition and its associated dynamic trigger.`}
        confirmLabel="Delete Channel"
        cancelLabel="Keep Channel"
        isDanger={true}
        isLoading={isDeleting}
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeletingChannel(null)}
      />

      {/* Toast Notifications */}
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />
    </div>
  );
}
