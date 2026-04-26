"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useSession } from "next-auth/react";

import { apiClient } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { pricingPlans } from "@/lib/trademind-content";
import { cn } from "@/lib/utils";

type SettingsTab = "profile" | "plan" | "notifications";

const notificationRows = [
  { key: "signalDigest", label: "Signal digest", description: "Receive a daily summary of signals and portfolio changes." },
  { key: "liveAlerts", label: "Live alerts", description: "Notify me when a new BUY, SELL, or HOLD signal is published." },
  { key: "riskWarnings", label: "Risk warnings", description: "Surface messages when plan limits or risk thresholds are close to breach." },
  { key: "strategyChanges", label: "Strategy changes", description: "Track when a strategy is created, updated, or deactivated." },
  { key: "weeklyRecap", label: "Weekly recap", description: "Send a weekly performance summary with top signals and P&L." },
] as const;

type NotificationKey = (typeof notificationRows)[number]["key"];

export default function SettingsPage() {
  const { data: session } = useSession();
  const [activeTab, setActiveTab] = useState<SettingsTab>("profile");
  const [name, setName] = useState(session?.user?.name ?? "TradeMind Operator");
  const [email, setEmail] = useState(session?.user?.email ?? "user@tradermind.ai");
  const [timezone, setTimezone] = useState("Europe/Madrid");
  const [currentPlan, setCurrentPlan] = useState("FREE");
  const [message, setMessage] = useState("Loading account settings...");
  const [isSaving, setIsSaving] = useState(false);
  const [notifications, setNotifications] = useState<Record<NotificationKey, boolean>>({
    signalDigest: true,
    liveAlerts: true,
    riskWarnings: true,
    strategyChanges: false,
    weeklyRecap: true,
  });

  useEffect(() => {
    let mounted = true;

    async function loadAccountSettings() {
      try {
        const [profile, prefs] = await Promise.all([
          apiClient.getCurrentUser(),
          apiClient.getNotificationPreferences(),
        ]);

        if (!mounted) {
          return;
        }

        setName(`${profile.firstName} ${profile.lastName}`.trim());
        setEmail(profile.email);
        setTimezone(profile.timezone);
        setCurrentPlan(profile.plan);
        setNotifications({
          signalDigest: prefs.signalDigest,
          liveAlerts: prefs.liveAlerts,
          riskWarnings: prefs.riskWarnings,
          strategyChanges: prefs.strategyChanges,
          weeklyRecap: prefs.weeklyRecap,
        });
        setMessage("Account settings loaded from the backend.");
      } catch {
        if (mounted) {
          setMessage("Using local defaults until the account API responds.");
        }
      }
    }

    loadAccountSettings();

    return () => {
      mounted = false;
    };
  }, []);

  async function handleSaveProfile() {
    setIsSaving(true);
    setMessage("Saving profile changes...");

    try {
      const [firstName, ...rest] = name.trim().split(/\s+/);
      const lastName = rest.length > 0 ? rest.join(" ") : "Operator";
      const updated = await apiClient.updateCurrentUser({
        firstName: firstName || "TradeMind",
        lastName,
        timezone,
      });

      setName(`${updated.firstName} ${updated.lastName}`.trim());
      setEmail(updated.email);
      setTimezone(updated.timezone);
      setCurrentPlan(updated.plan);
      setMessage("Profile changes saved to the backend.");
    } catch {
      setMessage("Unable to save profile changes right now.");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleSaveNotifications() {
    setIsSaving(true);
    setMessage("Saving notification preferences...");

    try {
      await apiClient.updateNotificationPreferences(notifications);
      setMessage("Notification preferences saved to the backend.");
    } catch {
      setMessage("Unable to save notification preferences right now.");
    } finally {
      setIsSaving(false);
    }
  }

  function resetProfile() {
    setName(session?.user?.name ?? "TradeMind Operator");
    setEmail(session?.user?.email ?? "user@tradermind.ai");
    setTimezone("Europe/Madrid");
    setMessage("Profile changes reset locally.");
  }

  return (
    <div className="space-y-8">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Settings</div>
            <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
              Configure your workspace
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-text-2">
              Update your profile, view the current plan, and control notification preferences from a single screen.
            </p>
          </div>

          <div className="grid grid-cols-3 rounded-full border border-border bg-bg-2 p-1 text-sm">
            {(["profile", "plan", "notifications"] as SettingsTab[]).map((tab) => (
              <button
                key={tab}
                className={cn(
                  "rounded-full px-4 py-2 text-center uppercase tracking-[0.2em] transition",
                  activeTab === tab ? "bg-bg-3 text-white" : "text-text-2 hover:text-text-1"
                )}
                onClick={() => setActiveTab(tab)}
                type="button"
              >
                {tab}
              </button>
            ))}
          </div>
        </div>
      </section>

      {activeTab === "profile" ? (
        <section className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Profile</div>
            <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
              Update your workspace details
            </h3>

            <div className="mt-6 grid gap-5">
              <label className="block">
                <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Display name</span>
                <input
                  className="w-full rounded-2xl border border-border bg-bg-2 px-4 py-3 text-sm text-white outline-none focus:border-cyan/40"
                  onChange={(event) => setName(event.target.value)}
                  value={name}
                />
              </label>

              <label className="block">
                <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Email</span>
                <input
                  className="w-full rounded-2xl border border-border bg-bg-2 px-4 py-3 text-sm text-text-2 outline-none"
                  disabled
                  value={email}
                />
              </label>

              <label className="block">
                <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Timezone</span>
                <select
                  className="w-full rounded-2xl border border-border bg-bg-2 px-4 py-3 text-sm text-white outline-none focus:border-cyan/40"
                  onChange={(event) => setTimezone(event.target.value)}
                  value={timezone}
                >
                  <option value="Europe/Madrid">Europe/Madrid</option>
                  <option value="UTC">UTC</option>
                  <option value="America/New_York">America/New_York</option>
                  <option value="Asia/Singapore">Asia/Singapore</option>
                </select>
              </label>

              <div className="flex flex-wrap items-center gap-3">
                <Button disabled={isSaving} onClick={handleSaveProfile} variant="cyan">
                  {isSaving ? "Saving..." : "Save changes"}
                </Button>
                <Button variant="outline" onClick={resetProfile}>
                  Cancel
                </Button>
                <p className="text-sm text-text-2">{message}</p>
              </div>
            </div>
          </article>

          <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Account</div>
            <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Session summary</h3>

            <div className="mt-6 space-y-3 rounded-[20px] border border-cyan/25 bg-cyan-dim p-5">
              <div className="text-xs uppercase tracking-[0.22em] text-cyan">Current user</div>
              <div className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">{name}</div>
              <div className="text-sm text-text-1">{email}</div>
              <div className="mt-4 text-xs uppercase tracking-[0.22em] text-text-3">Timezone: {timezone}</div>
            </div>

            <div className="mt-6 space-y-3">
              <div className="rounded-2xl border border-border bg-bg-2 p-4 text-sm text-text-2">
                Trading limits and subscription gates are enforced by the backend services.
              </div>
              <div className="rounded-2xl border border-border bg-bg-2 p-4 text-sm text-text-2">
                Profile settings now sync through the account API.
              </div>
            </div>
          </article>
        </section>
      ) : null}

      {activeTab === "plan" ? (
        <section className="space-y-6">
          <article className="rounded-[24px] border border-cyan/35 bg-[linear-gradient(180deg,rgba(0,200,212,0.10),rgba(17,23,32,0.86))] p-6 shadow-glow">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Current plan</div>
                <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
                  {currentPlan}, active
                </h3>
                <p className="mt-2 text-sm text-text-1">Signals today 3/5. Upgrade to Basic to unlock higher limits.</p>
              </div>
              <Button asChild variant="gold">
                <Link href="/pricing">Upgrade to Basic</Link>
              </Button>
            </div>
          </article>

          <div className="grid gap-6 lg:grid-cols-3">
            {pricingPlans.map((plan) => (
              <article
                key={plan.tier}
                className={cn(
                  "rounded-[20px] border p-6 shadow-glow",
                  plan.highlighted ? "border-cyan/35 bg-cyan-dim" : "border-border bg-bg-1/80"
                )}
              >
                <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">{plan.tier}</div>
                <h4 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">{plan.name}</h4>
                <div className="mt-4 font-mono text-[32px] text-white">{plan.price}</div>
                <p className="mt-2 font-mono text-sm text-cyan">{plan.tagline}</p>
                <div className="my-5 h-px bg-border" />
                <ul className="space-y-3 text-sm text-text-1">
                  {plan.features.map((feature) => (
                    <li key={feature} className="flex items-start gap-3">
                      <span className="mt-1 h-2 w-2 rounded-full bg-cyan" />
                      <span>{feature}</span>
                    </li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        </section>
      ) : null}

      {activeTab === "notifications" ? (
        <section className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Notifications</div>
            <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Alert preferences</h3>

            <div className="mt-6 space-y-4">
              {notificationRows.map((row) => (
                <div key={row.key} className="flex items-start justify-between gap-4 rounded-2xl border border-border bg-bg-2 p-4">
                  <div>
                    <div className="font-semibold text-white">{row.label}</div>
                    <p className="mt-1 text-sm leading-6 text-text-2">{row.description}</p>
                  </div>
                  <button
                    aria-pressed={notifications[row.key]}
                    className={cn(
                      "relative h-7 w-12 rounded-full border transition",
                      notifications[row.key] ? "border-cyan/30 bg-cyan-dim" : "border-border bg-bg-3"
                    )}
                    onClick={() =>
                      setNotifications((current) => ({
                        ...current,
                        [row.key]: !current[row.key],
                      }))
                    }
                    type="button"
                  >
                    <span
                      className={cn(
                        "absolute top-1 h-5 w-5 rounded-full bg-white transition-transform",
                        notifications[row.key] ? "translate-x-6" : "translate-x-1"
                      )}
                    />
                  </button>
                </div>
              ))}
            </div>

            <div className="mt-6 flex flex-wrap items-center gap-3">
              <Button disabled={isSaving} onClick={handleSaveNotifications} variant="cyan">
                {isSaving ? "Saving..." : "Save preferences"}
              </Button>
              <p className="text-sm text-text-2">{message}</p>
            </div>
          </article>

          <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Sync</div>
            <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Delivery summary</h3>
            <div className="mt-6 space-y-3">
              <div className="rounded-2xl border border-border bg-bg-2 p-4 text-sm text-text-2">
                Signal, portfolio, and backtest notifications are ready to sync through the account API.
              </div>
              <div className="rounded-2xl border border-border bg-bg-2 p-4 text-sm text-text-2">
                The toggle state is now persisted server-side instead of only living in the browser.
              </div>
            </div>
          </article>
        </section>
      ) : null}
    </div>
  );
}
