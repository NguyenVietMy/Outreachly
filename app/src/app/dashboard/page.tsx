"use client";

import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Button } from "@/components/ui/button";
import {
  useDashboard,
  ActivityItem,
  Integration,
  TrendData,
  DailySuggestionTask,
} from "@/hooks/useDashboard";
import {
  ArrowRight,
  Bot,
  Loader2,
  RefreshCw,
} from "lucide-react";
import {
  GitHubIcon,
  SlackIcon,
  LinearIcon,
  ObsidianIcon,
} from "@/components/icons/BrandIcons";
import Link from "next/link";
import { ComponentType, SVGProps, useMemo, useState } from "react";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

type IconComponent = ComponentType<
  { className?: string } & SVGProps<SVGSVGElement>
>;

const PROVIDER_META: Record<
  string,
  {
    label: string;
    color: string;
    icon: IconComponent;
    tone: string;
    sub: string;
    metricsKey:
      | "githubCommits"
      | "obsidianNotes"
      | "slackMessages"
      | "linearTickets";
  }
> = {
  github: {
    label: "GitHub",
    color: "#18E299",
    icon: GitHubIcon,
    tone: "bg-[#d4fae8] text-[#0fa76e]",
    sub: "events today",
    metricsKey: "githubCommits",
  },
  obsidian: {
    label: "Obsidian",
    color: "#3772cf",
    icon: ObsidianIcon,
    tone: "bg-[#e5e5e5] text-[#6c31e3]",
    sub: "notes edited",
    metricsKey: "obsidianNotes",
  },
  slack: {
    label: "Slack",
    color: "#c37d0d",
    icon: SlackIcon,
    tone: "bg-[#f8ebd8] text-[#c37d0d]",
    sub: "messages sent",
    metricsKey: "slackMessages",
  },
  linear: {
    label: "Linear",
    color: "#d45656",
    icon: LinearIcon,
    tone: "bg-[#f7e5e5] text-[#d45656]",
    sub: "tickets today",
    metricsKey: "linearTickets",
  },
};

const PROVIDER_ORDER = ["github", "obsidian", "slack", "linear"];

function getIconAndTone(activity: ActivityItem) {
  const meta = PROVIDER_META[activity.provider];
  if (meta) return { Icon: meta.icon, tone: meta.tone };
  return { Icon: Bot, tone: "bg-[#f5f5f5] text-[#666666]" };
}

function formatEventType(eventType: string): string {
  return eventType.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

// --- Pulse Strip ---
function PulseStrip({
  metrics,
  loading,
}: {
  metrics: ReturnType<typeof useDashboard>["metrics"];
  loading: boolean;
}) {
  return (
    <section className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-4 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {PROVIDER_ORDER.map((provider) => {
          const meta = PROVIDER_META[provider];
          const value = metrics?.[meta.metricsKey] ?? 0;
          const breakdown = metrics?.eventTypeBreakdown?.[provider];
          const Icon = meta.icon;

          return (
            <div
              key={provider}
              className={`flex items-center gap-3 rounded-[12px] px-3 py-2.5 transition-colors ${
                value > 0 ? "bg-[#fafafa]" : "bg-[#fafafa] opacity-50"
              }`}
            >
              <div className={`shrink-0 rounded-[8px] p-1.5 ${meta.tone}`}>
                <Icon className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span
                    className="h-1.5 w-1.5 rounded-full"
                    style={{ backgroundColor: value > 0 ? meta.color : "#ccc" }}
                  />
                  <span className="font-mono text-[11px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                    {meta.label}
                  </span>
                </div>
                {loading ? (
                  <div className="mt-1 h-4 w-10 animate-pulse rounded bg-[#f0f0f0]" />
                ) : (
                  <div className="mt-0.5 flex flex-wrap items-baseline gap-1.5">
                    <span className="text-[18px] font-semibold leading-tight text-[#0d0d0d]">
                      {value}
                    </span>
                    <span className="text-[11px] text-[#999999]">
                      {meta.sub}
                    </span>
                  </div>
                )}
                {!loading && breakdown && Object.keys(breakdown).length > 0 && (
                  <div className="mt-1 flex flex-wrap gap-1">
                    {Object.entries(breakdown).map(([type, count]) => (
                      <span
                        key={type}
                        className="rounded-full bg-[#f0f0f0] px-1.5 py-0.5 font-mono text-[9px] font-medium text-[#888888]"
                      >
                        {count} {formatEventType(type).toLowerCase()}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

// --- 14-Day Trend Chart ---
function TrendChart({ trendData }: { trendData: TrendData }) {
  const chartData = useMemo(() => {
    return Object.entries(trendData).map(([date, providers]) => ({
      date,
      label: new Date(date + "T00:00:00").toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
      }),
      github: providers.github ?? 0,
      obsidian: providers.obsidian ?? 0,
      slack: providers.slack ?? 0,
      linear: providers.linear ?? 0,
    }));
  }, [trendData]);

  const hasData = chartData.some(
    (d) => d.github + d.obsidian + d.slack + d.linear > 0,
  );

  if (!hasData) {
    return (
      <div className="flex h-full items-center justify-center rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
        <p className="text-center text-[14px] text-[#666666]">
          Connect an integration to see your activity trend.
        </p>
      </div>
    );
  }

  return (
    <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
      <h2 className="mb-4 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
        14-day activity
      </h2>
      <div className="h-[180px]">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={chartData}>
            <XAxis
              dataKey="label"
              tick={{ fontSize: 10, fill: "#999" }}
              axisLine={false}
              tickLine={false}
              interval="preserveStartEnd"
            />
            <YAxis
              tick={{ fontSize: 10, fill: "#999" }}
              axisLine={false}
              tickLine={false}
              width={28}
              allowDecimals={false}
            />
            <Tooltip
              contentStyle={{
                fontSize: 12,
                borderRadius: 8,
                border: "1px solid rgba(0,0,0,0.05)",
                boxShadow: "0 2px 8px rgba(0,0,0,0.08)",
              }}
            />
            <Area
              type="monotone"
              dataKey="github"
              stackId="1"
              stroke="#18E299"
              fill="#18E299"
              fillOpacity={0.15}
              name="GitHub"
            />
            <Area
              type="monotone"
              dataKey="obsidian"
              stackId="1"
              stroke="#3772cf"
              fill="#3772cf"
              fillOpacity={0.15}
              name="Obsidian"
            />
            <Area
              type="monotone"
              dataKey="slack"
              stackId="1"
              stroke="#c37d0d"
              fill="#c37d0d"
              fillOpacity={0.15}
              name="Slack"
            />
            <Area
              type="monotone"
              dataKey="linear"
              stackId="1"
              stroke="#d45656"
              fill="#d45656"
              fillOpacity={0.15}
              name="Linear"
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </article>
  );
}

// --- Integration Status Bar ---
function HealthBar({ integrations }: { integrations: Integration[] }) {
  const connected = integrations.filter(
    (i) => i.supportsSync && i.status === "connected",
  );

  if (connected.length === 0) return null;

  return (
    <section className="hidden xl:flex items-center gap-4 rounded-[12px] border border-[rgba(0,0,0,0.03)] bg-[#fafafa] px-4 py-2.5">
      <div className="flex flex-wrap items-center gap-4">
        {connected.map((integration) => {
          const meta = PROVIDER_META[integration.provider];
          const active = integration.webhookStatus === "active";
          return (
            <span
              key={integration.provider}
              className={`flex items-center gap-1.5 text-[12px] ${active ? "text-[#999999]" : "text-amber-600"}`}
            >
              <span
                className="h-1.5 w-1.5 rounded-full"
                style={{ backgroundColor: active ? meta?.color ?? "#ccc" : "#d97706" }}
              />
              {meta?.label ?? integration.provider}
              <span className="font-mono text-[11px]">
                {integration.lastActivityLabel ?? integration.scopeSummary ?? "Waiting"}
              </span>
            </span>
          );
        })}
      </div>
    </section>
  );
}

// --- Main Dashboard ---
export default function Dashboard() {
  const {
    metrics,
    activity,
    trendData,
    integrations,
    digest,
    suggestions,
    loadingMetrics,
    loadingActivity,
    loadingDigest,
    loadingSuggestions,
    requestDigest,
    regenerateSuggestions,
  } = useDashboard();

  const [regenError, setRegenError] = useState("");

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="min-h-full bg-transparent px-6 py-10 text-[#0d0d0d] md:px-8 md:py-12">
          <div className="flex w-full flex-col gap-8">
            {/* Header */}
            <section className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                  Home dashboard
                </p>
                <h1 className="mt-2 font-mono text-[40px] font-semibold leading-[1.1] tracking-[-0.8px] text-[#0d0d0d]">
                  {metrics?.greeting || "Good morning"}
                </h1>
                <p className="mt-3 max-w-2xl text-[18px] leading-[1.5] text-[#333333]">
                  {metrics?.dateLabel
                    ? `${metrics.dateLabel} — here's what's going on across your workspace.`
                    : "Loading your workspace..."}
                </p>
              </div>
              <Link href="/integrations">
                <Button className="h-auto rounded-full bg-brand-accent px-6 py-2 text-[15px] font-medium text-[#0d0d0d] hover:bg-brand-deep hover:text-white">
                  Manage integrations
                </Button>
              </Link>
            </section>

            {/* Row 1: Pulse Strip */}
            <PulseStrip metrics={metrics} loading={loadingMetrics} />

            {/* Row 2: Trend + Digest */}
            <section className="grid gap-6 xl:grid-cols-[3fr_2fr]">
              <TrendChart trendData={trendData} />
              <article className="flex h-full flex-col rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                <div className="mb-3 flex items-center justify-between">
                  <div className="flex items-center gap-2 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                    AI digest
                  </div>
                  <Button
                    onClick={requestDigest}
                    disabled={loadingDigest}
                    variant="ghost"
                    size="sm"
                    className="h-auto px-2 py-1 text-[12px] font-medium text-[#999999] hover:text-[#18E299]"
                  >
                    {loadingDigest ? (
                      <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                    ) : (
                      <RefreshCw className="mr-1 h-3 w-3" />
                    )}
                    {loadingDigest ? "Generating..." : "Generate"}
                  </Button>
                </div>
                {digest ? (
                  <p className="text-[14px] leading-[1.6] text-[#333333]">
                    {digest}
                  </p>
                ) : (
                  <p className="text-[14px] leading-[1.6] text-[#999999]">
                    Generate an AI summary of your recent activity across all
                    integrations.
                  </p>
                )}
              </article>
            </section>

            {/* Row 3: Activity + Focus/Goals */}
            <section className="grid gap-6 xl:grid-cols-2">
              {/* Activity Feed */}
              <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                    Recent activity
                  </h2>
                  <Link
                    href="/integrations"
                    className="inline-flex items-center gap-1 text-[14px] font-medium text-[#0d0d0d] hover:text-[#18E299]"
                  >
                    View all <ArrowRight className="h-4 w-4" />
                  </Link>
                </div>
                <div className="space-y-0.5">
                  {loadingActivity ? (
                    Array.from({ length: 4 }).map((_, i) => (
                      <div key={i} className="flex items-start gap-3 py-2">
                        <div className="h-7 w-7 animate-pulse rounded-[8px] bg-[#f5f5f5]" />
                        <div className="flex-1 space-y-2">
                          <div className="h-4 w-3/4 animate-pulse rounded bg-[#f5f5f5]" />
                          <div className="h-3 w-1/3 animate-pulse rounded bg-[#f5f5f5]" />
                        </div>
                      </div>
                    ))
                  ) : activity.length === 0 ? (
                    <p className="py-8 text-center text-[14px] text-[#666666]">
                      No recent activity. Connect an integration to get started.
                    </p>
                  ) : (
                    activity.map((item) => {
                      const { Icon, tone } = getIconAndTone(item);
                      const providerLabel =
                        PROVIDER_META[item.provider]?.label || item.provider;

                      return (
                        <div
                          key={item.id}
                          className="flex items-start gap-3 border-b border-[rgba(0,0,0,0.05)] py-2 last:border-b-0 last:pb-0"
                        >
                          <div className={`mt-0.5 rounded-[8px] p-1.5 ${tone}`}>
                            <Icon className="h-3.5 w-3.5" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-[14px] leading-[1.5] text-[#0d0d0d]">
                              {item.title}
                            </p>
                            <div className="mt-0.5 flex flex-wrap items-center gap-2 text-[12px] leading-[1.5] text-[#666666]">
                              <span>{item.timeAgo}</span>
                              <span
                                className={`rounded-full px-2 py-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.6px] ${tone}`}
                              >
                                {providerLabel}
                              </span>
                            </div>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </article>

              {/* Right column: Today's Focus + Goals */}
              <div className="flex flex-col gap-6">
                <div>
                  <div className="mb-4 flex items-center justify-between">
                    <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                      Today&apos;s Focus
                    </h2>
                    <button
                      onClick={async () => {
                        setRegenError("");
                        try {
                          await regenerateSuggestions();
                        } catch (e) {
                          setRegenError(e instanceof Error ? e.message : "Failed");
                        }
                      }}
                      disabled={loadingSuggestions}
                      className="flex items-center gap-1.5 rounded-full border border-[rgba(0,0,0,0.08)] px-3 py-1.5 text-[12px] font-medium text-[#666] transition-colors hover:border-[rgba(0,0,0,0.15)] hover:text-[#0d0d0d] disabled:opacity-50"
                      title="Regenerate suggestions"
                    >
                      <RefreshCw className={`h-3 w-3 ${loadingSuggestions ? "animate-spin" : ""}`} />
                      Refresh
                    </button>
                  </div>
                  {regenError && (
                    <p className="mb-3 text-[12px] text-[#d45656]">{regenError}</p>
                  )}
                  {loadingSuggestions && !suggestions ? (
                    <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                      <div className="space-y-3">
                        {[1, 2, 3].map((i) => (
                          <div key={i} className="space-y-1.5">
                            <div className="h-4 w-3/4 animate-pulse rounded bg-[#f5f5f5]" />
                            <div className="h-3 w-full animate-pulse rounded bg-[#f5f5f5]" />
                          </div>
                        ))}
                      </div>
                    </article>
                  ) : suggestions && suggestions.tasks.length > 0 ? (
                    <div className="space-y-2">
                      {suggestions.tasks.map((task: DailySuggestionTask, i: number) => {
                        const categoryColors: Record<string, string> = {
                          project: "bg-[#e7eefb] text-[#3772cf]",
                          review: "bg-[#f8ebd8] text-[#c37d0d]",
                          learning: "bg-[#d4fae8] text-[#0fa76e]",
                          career: "bg-[#e8d8f8] text-[#7c3aed]",
                        };
                        const priorityLabels: Record<number, string> = { 0: "Today", 1: "This week", 2: "Nice to have" };
                        return (
                          <article
                            key={i}
                            className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-4 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]"
                          >
                            <div className="flex items-start justify-between gap-3">
                              <div className="min-w-0 flex-1">
                                <div className="flex flex-wrap items-center gap-2">
                                  <p className="text-[14px] font-medium leading-[1.4] text-[#0d0d0d]">
                                    {task.title}
                                  </p>
                                  <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${categoryColors[task.category] || "bg-[#f5f5f5] text-[#666]"}`}>
                                    {task.category}
                                  </span>
                                </div>
                                <p className="mt-1 text-[13px] leading-[1.5] text-[#555]">
                                  {task.description}
                                </p>
                                <div className="mt-2 flex flex-wrap items-center gap-2">
                                  {task.repoContext && (
                                    <span className="rounded bg-[#f5f5f5] px-1.5 py-0.5 font-mono text-[11px] text-[#666]">
                                      {task.repoContext}
                                    </span>
                                  )}
                                  <span className="text-[11px] text-[#999]">
                                    {priorityLabels[task.priority] || ""}
                                  </span>
                                </div>
                              </div>
                              {task.priority === 0 && (
                                <span className="mt-0.5 shrink-0 rounded-full bg-[#f7e5e5] px-2 py-0.5 text-[10px] font-semibold text-[#d45656]">
                                  High
                                </span>
                              )}
                            </div>
                            {task.rationale && (
                              <p className="mt-2 border-t border-[rgba(0,0,0,0.04)] pt-2 text-[11px] leading-[1.5] text-[#999]">
                                {task.rationale}
                              </p>
                            )}
                          </article>
                        );
                      })}
                      {suggestions.generatedAt && (
                        <p className="text-right text-[11px] text-[#bbb]">
                          Generated {new Date(suggestions.generatedAt).toLocaleTimeString()}
                        </p>
                      )}
                    </div>
                  ) : (
                    <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                      <p className="text-[14px] leading-[1.6] text-[#666]">
                        Connect GitHub and sync your projects to get daily task suggestions powered by your activity, goals, and project context.
                      </p>
                    </article>
                  )}
                </div>

              </div>
            </section>

            {/* Row 4: Integration Health */}
            <HealthBar integrations={integrations} />
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
