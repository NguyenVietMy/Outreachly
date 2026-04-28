"use client";

import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Button } from "@/components/ui/button";
import {
  useDashboard,
  ActivityItem,
  UserGoal,
  Integration,
  TrendData,
} from "@/hooks/useDashboard";
import {
  ArrowRight,
  Bot,
  Loader2,
  Target,
  AlertTriangle,
  CheckCircle2,
  Clock,
  RefreshCw,
} from "lucide-react";
import {
  GitHubIcon,
  SlackIcon,
  LinearIcon,
  ObsidianIcon,
} from "@/components/icons/BrandIcons";
import Link from "next/link";
import { ComponentType, SVGProps, useMemo } from "react";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

type IconComponent = ComponentType<{ className?: string } & SVGProps<SVGSVGElement>>;

const PROVIDER_META: Record<
  string,
  {
    label: string;
    color: string;
    icon: IconComponent;
    tone: string;
    sub: string;
    metricsKey: "githubCommits" | "obsidianNotes" | "slackMessages" | "linearTickets";
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

const AXIS_LABELS: Record<string, string> = {
  dsa: "DSA",
  projects: "Projects",
  systemDesign: "System Design",
  coreCs: "Core CS",
  resume: "Resume",
};

function getIconAndTone(activity: ActivityItem) {
  const meta = PROVIDER_META[activity.provider];
  if (meta) return { Icon: meta.icon, tone: meta.tone };
  return { Icon: Bot, tone: "bg-[#f5f5f5] text-[#666666]" };
}

function formatEventType(eventType: string): string {
  return eventType.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

function daysUntil(deadline: string): number {
  const d = new Date(deadline);
  const now = new Date();
  return Math.ceil((d.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
}

function deadlineColor(days: number): string {
  if (days <= 3) return "bg-red-100 text-red-700";
  if (days <= 7) return "bg-amber-100 text-amber-700";
  return "bg-[#f5f5f5] text-[#666666]";
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
                    <span className="text-[11px] text-[#999999]">{meta.sub}</span>
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
    (d) => d.github + d.obsidian + d.slack + d.linear > 0
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

// --- Goals Panel ---
function GoalsPanel({ goals }: { goals: UserGoal[] }) {
  const activeGoals = goals.filter((g) => g.status === "active").slice(0, 3);

  return (
    <article className="flex h-full flex-col rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="flex items-center gap-2 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
          <Target className="h-3.5 w-3.5 text-[#18E299]" />
          Active goals
        </h2>
        <Link
          href="/personal"
          className="text-[12px] font-medium text-[#999999] hover:text-[#18E299]"
        >
          Manage
        </Link>
      </div>
      {activeGoals.length === 0 ? (
        <div className="flex flex-1 items-center justify-center">
          <div className="text-center">
            <p className="text-[14px] text-[#666666]">No active goals yet.</p>
            <Link
              href="/personal"
              className="mt-1 inline-flex items-center gap-1 text-[13px] font-medium text-[#18E299] hover:underline"
            >
              Set your first goal <ArrowRight className="h-3 w-3" />
            </Link>
          </div>
        </div>
      ) : (
        <div className="flex flex-1 flex-col justify-center gap-4">
          {activeGoals.map((goal) => {
            const progress =
              goal.targetValue && goal.targetValue > 0
                ? Math.min((goal.currentValue / goal.targetValue) * 100, 100)
                : 0;
            const days = goal.deadline ? daysUntil(goal.deadline) : null;

            return (
              <div key={goal.id}>
                <div className="mb-1 flex items-center justify-between">
                  <p className="text-[13px] font-medium text-[#0d0d0d] truncate pr-2">
                    {goal.title}
                  </p>
                  {days !== null && (
                    <span
                      className={`shrink-0 rounded-full px-2 py-0.5 font-mono text-[10px] font-medium ${deadlineColor(days)}`}
                    >
                      {days <= 0 ? "Overdue" : `${days}d left`}
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#f0f0f0]">
                    <div
                      className="h-full rounded-full bg-[#18E299] transition-all duration-500"
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                  {goal.targetValue && (
                    <span className="shrink-0 font-mono text-[10px] text-[#999999]">
                      {goal.currentValue}/{goal.targetValue} {goal.unit}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </article>
  );
}

// --- SWE Readiness Snapshot ---
function ReadinessSnapshot({
  profile,
}: {
  profile: ReturnType<typeof useDashboard>["profile"];
}) {
  if (!profile?.axisScores) {
    return (
      <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
        <h2 className="mb-3 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
          SWE Readiness
        </h2>
        <p className="text-[14px] text-[#666666]">
          Complete your SWE profile to unlock readiness tracking.
        </p>
        <Link
          href="/personal"
          className="mt-2 inline-flex items-center gap-1 text-[13px] font-medium text-[#18E299] hover:underline"
        >
          Get started <ArrowRight className="h-3 w-3" />
        </Link>
      </article>
    );
  }

  const scores = profile.axisScores;
  const entries = Object.entries(scores).filter(
    ([, v]) => typeof v === "number" && v > 0
  ) as [string, number][];

  if (entries.length === 0) {
    return (
      <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
        <h2 className="mb-3 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
          SWE Readiness
        </h2>
        <p className="text-[14px] text-[#666666]">
          Complete assessments on the Personal page to see your readiness snapshot.
        </p>
        <Link
          href="/personal"
          className="mt-2 inline-flex items-center gap-1 text-[13px] font-medium text-[#18E299] hover:underline"
        >
          Start assessments <ArrowRight className="h-3 w-3" />
        </Link>
      </article>
    );
  }

  const sorted = [...entries].sort((a, b) => b[1] - a[1]);
  const best = sorted[0];
  const worst = sorted[sorted.length - 1];
  const resumeDecision = profile.resumeScoreBreakdown?.decision;

  return (
    <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
          SWE Readiness
        </h2>
        <Link
          href="/personal"
          className="text-[12px] font-medium text-[#999999] hover:text-[#18E299]"
        >
          Full profile
        </Link>
      </div>
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-[13px] text-[#333333]">
            {AXIS_LABELS[best[0]] || best[0]}
          </span>
          <span className="rounded-full bg-[#d4fae8] px-2.5 py-0.5 font-mono text-[11px] font-semibold text-[#0fa76e]">
            {best[1]} — strongest
          </span>
        </div>
        {best[0] !== worst[0] && (
          <div className="flex items-center justify-between">
            <span className="text-[13px] text-[#333333]">
              {AXIS_LABELS[worst[0]] || worst[0]}
            </span>
            <span className="rounded-full bg-red-50 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-red-600">
              {worst[1]} — needs work
            </span>
          </div>
        )}
        {resumeDecision && (
          <div className="flex items-center justify-between">
            <span className="text-[13px] text-[#333333]">Resume</span>
            <span
              className={`rounded-full px-2.5 py-0.5 font-mono text-[11px] font-semibold ${
                resumeDecision === "ADVANCE"
                  ? "bg-[#d4fae8] text-[#0fa76e]"
                  : resumeDecision === "HOLD"
                    ? "bg-amber-50 text-amber-600"
                    : "bg-red-50 text-red-600"
              }`}
            >
              {resumeDecision}
            </span>
          </div>
        )}
      </div>
    </article>
  );
}

// --- Integration Health Bar ---
function HealthBar({ integrations }: { integrations: Integration[] }) {
  const syncable = integrations.filter(
    (i) => i.supportsSync && i.status === "connected"
  );

  if (syncable.length === 0) return null;

  const allHealthy = syncable.every((i) => i.consecutiveFailures === 0);
  if (allHealthy && syncable.every((i) => i.lastSyncedAt)) {
    return (
      <section className="hidden xl:flex items-center gap-4 rounded-[12px] border border-[rgba(0,0,0,0.03)] bg-[#fafafa] px-4 py-2.5">
        <CheckCircle2 className="h-3.5 w-3.5 text-[#18E299]" />
        <div className="flex flex-wrap items-center gap-4">
          {syncable.map((i) => {
            const meta = PROVIDER_META[i.provider];
            return (
              <span
                key={i.provider}
                className="flex items-center gap-1.5 text-[12px] text-[#999999]"
              >
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ backgroundColor: meta?.color ?? "#ccc" }}
                />
                {meta?.label ?? i.provider}
                <Clock className="h-3 w-3" />
                <span className="font-mono text-[11px]">{i.lastSyncedAt}</span>
              </span>
            );
          })}
        </div>
      </section>
    );
  }

  return (
    <section className="hidden xl:flex items-center gap-4 rounded-[12px] border border-[rgba(0,0,0,0.03)] bg-[#fafafa] px-4 py-2.5">
      <div className="flex flex-wrap items-center gap-4">
        {syncable.map((i) => {
          const meta = PROVIDER_META[i.provider];
          const hasFailed = i.consecutiveFailures > 0;
          return (
            <span
              key={i.provider}
              className={`flex items-center gap-1.5 text-[12px] ${hasFailed ? "text-amber-600" : "text-[#999999]"}`}
            >
              {hasFailed ? (
                <AlertTriangle className="h-3 w-3" />
              ) : (
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ backgroundColor: meta?.color ?? "#ccc" }}
                />
              )}
              {meta?.label ?? i.provider}
              {hasFailed ? (
                <span className="font-mono text-[11px]">
                  {i.consecutiveFailures} failure{i.consecutiveFailures > 1 ? "s" : ""}
                </span>
              ) : (
                <>
                  <Clock className="h-3 w-3" />
                  <span className="font-mono text-[11px]">
                    {i.lastSyncedAt ?? "Never"}
                  </span>
                </>
              )}
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
    profile,
    goals,
    integrations,
    digest,
    loadingMetrics,
    loadingActivity,
    loadingDigest,
    requestDigest,
  } = useDashboard();

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

            {/* Row 2: Trend + Goals */}
            <section className="grid gap-6 xl:grid-cols-[3fr_2fr]">
              <TrendChart trendData={trendData} />
              <GoalsPanel goals={goals} />
            </section>

            {/* Row 3: Activity + Readiness/Digest */}
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

              {/* Right column: Readiness + Digest */}
              <div className="flex flex-col gap-6">
                <ReadinessSnapshot profile={profile} />

                <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                  <div className="mb-3 flex items-center justify-between">
                    <div className="flex items-center gap-2 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                      <Bot className="h-4 w-4 text-[#18E299]" />
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
