"use client";

import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Button } from "@/components/ui/button";
import { useDashboard, ActivityItem } from "@/hooks/useDashboard";
import { ArrowRight, Bot, Loader2 } from "lucide-react";
import { GitHubIcon, SlackIcon, LinearIcon, ObsidianIcon } from "@/components/icons/BrandIcons";
import Link from "next/link";
import { ComponentType, SVGProps } from "react";

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
    sub: "commits today",
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
    sub: "tickets closed",
    metricsKey: "linearTickets",
  },
};

const PROVIDER_ORDER = ["github", "obsidian", "slack", "linear"];

function getIconAndTone(activity: ActivityItem) {
  const meta = PROVIDER_META[activity.provider];
  if (meta) return { Icon: meta.icon, tone: meta.tone };
  return { Icon: Bot, tone: "bg-[#f5f5f5] text-[#666666]" };
}

export default function Dashboard() {
  const {
    metrics,
    activity,
    activityBySource,
    digest,
    loadingMetrics,
    loadingActivity,
    loadingDigest,
    requestDigest,
  } = useDashboard();

  const maxSourceCount = Math.max(...Object.values(activityBySource), 1);

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="min-h-[calc(100vh-64px)] bg-[#ffffff] px-6 py-12 text-[#0d0d0d] md:px-8 md:py-16">
          <div className="mx-auto flex w-full max-w-[1200px] flex-col gap-12">
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
                    ? `${metrics.dateLabel} - here's what's going on across your workspace.`
                    : "Loading your workspace..."}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <Button
                  onClick={requestDigest}
                  disabled={loadingDigest}
                  className="h-auto rounded-full bg-primary px-4 py-2 text-[15px] font-medium text-primary-foreground hover:bg-primary/90"
                >
                  {loadingDigest && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Request digest
                </Button>
                <Link href="/integrations">
                  <Button className="h-auto rounded-full bg-brand-accent px-6 py-2 text-[15px] font-medium text-[#0d0d0d] hover:bg-brand-deep hover:text-white">
                    Manage integrations
                  </Button>
                </Link>
              </div>
            </section>

            <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {PROVIDER_ORDER.map((provider) => {
                const meta = PROVIDER_META[provider];
                const value = metrics?.[meta.metricsKey] ?? 0;

                return (
                  <article
                    key={provider}
                    className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px] transition-colors hover:border-[rgba(0,0,0,0.08)]"
                  >
                    <div className="mb-3 flex items-center gap-2 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                      <span
                        className="h-2 w-2 rounded-full"
                        style={{ backgroundColor: meta.color }}
                      />
                      {meta.label}
                    </div>
                    {loadingMetrics ? (
                      <div className="h-8 w-12 animate-pulse rounded bg-[#f5f5f5]" />
                    ) : (
                      <p className="text-[24px] font-medium leading-[1.3] tracking-[-0.24px] text-[#0d0d0d]">
                        {value}
                      </p>
                    )}
                    <p className="mt-2 text-[14px] leading-[1.5] text-[#666666]">{meta.sub}</p>
                  </article>
                );
              })}
            </section>

            <section className="grid gap-6 xl:grid-cols-2">
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
                <div className="space-y-1">
                  {loadingActivity ? (
                    Array.from({ length: 4 }).map((_, i) => (
                      <div key={i} className="flex items-start gap-3 py-3">
                        <div className="h-8 w-8 animate-pulse rounded-[8px] bg-[#f5f5f5]" />
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
                          className="flex items-start gap-3 border-b border-[rgba(0,0,0,0.05)] py-3 last:border-b-0 last:pb-0"
                        >
                          <div className={`mt-0.5 rounded-[8px] p-2 ${tone}`}>
                            <Icon className="h-4 w-4" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-[16px] leading-[1.5] text-[#0d0d0d]">
                              {item.title}
                            </p>
                            <div className="mt-1 flex flex-wrap items-center gap-2 text-[14px] leading-[1.5] text-[#666666]">
                              <span>{item.timeAgo}</span>
                              <span
                                className={`rounded-full px-3 py-1 font-mono text-[12px] font-semibold uppercase tracking-[0.6px] ${tone}`}
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

              <div className="flex flex-col gap-6">
                <article className="rounded-[24px] border border-[rgba(0,0,0,0.05)] bg-white p-8 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                  <div className="mb-3 flex items-center gap-2 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                    <Bot className="h-4 w-4 text-[#18E299]" />
                    AI digest
                  </div>
                  {loadingDigest ? (
                    <div className="space-y-2">
                      <div className="h-4 w-full animate-pulse rounded bg-[#f5f5f5]" />
                      <div className="h-4 w-3/4 animate-pulse rounded bg-[#f5f5f5]" />
                    </div>
                  ) : digest ? (
                    <p className="text-[16px] leading-[1.5] text-[#333333]">{digest}</p>
                  ) : (
                    <p className="text-[16px] leading-[1.5] text-[#999999]">
                      Click &quot;Request digest&quot; to generate an AI summary of your recent activity.
                    </p>
                  )}
                </article>

                <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                  <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                    Activity by source
                  </h2>
                  <div className="space-y-3">
                    {PROVIDER_ORDER.map((provider) => {
                      const meta = PROVIDER_META[provider];
                      const count = activityBySource[provider] ?? 0;
                      const widthPct = maxSourceCount > 0 ? (count / maxSourceCount) * 100 : 0;

                      return (
                        <div key={provider} className="flex items-center gap-3">
                          <p className="w-16 shrink-0 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                            {meta.label}
                          </p>
                          <div className="h-2 flex-1 overflow-hidden rounded-full bg-[#f5f5f5]">
                            <div
                              className="h-full rounded-full transition-all duration-500"
                              style={{
                                width: `${widthPct}%`,
                                backgroundColor: meta.color,
                              }}
                            />
                          </div>
                          <p className="w-7 text-right font-mono text-[12px] font-medium text-[#666666]">
                            {count}
                          </p>
                        </div>
                      );
                    })}
                  </div>
                </article>
              </div>
            </section>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
