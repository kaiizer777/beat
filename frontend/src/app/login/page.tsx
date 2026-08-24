"use client";

import { useState } from "react";
import { signIn } from "next-auth/react";
import { Radio, Mail, ArrowRight, CheckCircle2, AlertCircle, Loader2 } from "lucide-react";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) return;

    setLoading(true);
    setError("");
    try {
      const res = await signIn("resend", {
        email,
        redirect: false,
        callbackUrl: "/",
      });
      if (res?.error) {
        if (res.error === "Configuration") {
          setError("Unable to connect to authentication service. Please retry in a few seconds.");
        } else {
          setError(res.error);
        }
      } else {
        setSubmitted(true);
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to send magic link";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#090d16] text-slate-100 flex items-center justify-center p-4 relative overflow-hidden font-sans">
      {/* Atmospheric subtle studio grid */}
      <div className="pointer-events-none fixed inset-0 z-0 bg-studio-grid opacity-80" />

      <div className="relative z-10 w-full max-w-md rounded-2xl border border-slate-800 bg-[#0d131f] p-8 shadow-2xl backdrop-blur-xl animate-fade-in">
        {/* Logo Header */}
        <div className="flex flex-col items-center text-center mb-8">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-slate-800 text-sky-400 border border-slate-700/80 shadow-sm mb-4">
            <Radio className="h-6 w-6 text-sky-400 animate-pulse" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white sm:text-3xl">
            Welcome to BEAT
          </h1>
          <p className="mt-2 text-xs sm:text-sm text-slate-400 max-w-xs">
            Sign in with your email address to access your personalized news digests.
          </p>
        </div>

        {submitted ? (
          <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-6 text-center animate-slide-up">
            <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-400 mb-3">
              <CheckCircle2 className="h-5 w-5" />
            </div>
            <h3 className="text-base font-bold text-emerald-300">Magic Link Sent!</h3>
            <p className="mt-2 text-xs text-slate-300 leading-relaxed">
              We&apos;ve sent a passwordless sign-in link to <strong className="text-white">{email}</strong>.
            </p>
            <p className="mt-3 text-[11px] text-slate-400">
              Click the link in your inbox to open BEAT. You can close this tab now.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label htmlFor="email" className="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-2">
                Email Address
              </label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                <input
                  id="email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  className="w-full rounded-xl border border-slate-800 bg-slate-950/60 pl-10 pr-4 py-2.5 text-sm text-white placeholder-slate-500 shadow-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500/20 transition"
                />
              </div>
            </div>

            {error && (
              <div className="flex items-center space-x-2 rounded-xl border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300">
                <AlertCircle className="h-4 w-4 text-rose-400 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center space-x-2 rounded-xl bg-slate-800 hover:bg-slate-700/90 py-2.5 px-4 text-sm font-semibold text-slate-200 hover:text-white border border-slate-700/80 shadow-sm transition disabled:opacity-50"
            >
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin text-sky-400" />
                  <span>Sending magic link...</span>
                </>
              ) : (
                <>
                  <span>Send Magic Link</span>
                  <ArrowRight className="h-4 w-4 text-sky-400" />
                </>
              )}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
