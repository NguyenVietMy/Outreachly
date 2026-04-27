"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import AuthGuard from "@/components/AuthGuard";
import DashboardLayout from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/use-toast";
import { useIntegrations, Integration } from "@/hooks/useIntegrations";
import {
  Clock3,
  Link2,
  Loader2,
  Mail,
  PanelsTopLeft,
  RefreshCw,
} from "lucide-react";
import { GitHubIcon, SlackIcon, LinearIcon, ObsidianIcon } from "@/components/icons/BrandIcons";
import { ComponentType, SVGProps } from "react";

type IconComponent = ComponentType<{ className?: string } & SVGProps<SVGSVGElement>>;

const PROVIDER_CONFIG: Record<
  string,
  {
    name: string;
    description: string;
    icon: IconComponent;
    iconTone: string;
    pitch: string;
    connectLabel: string;
    connectType: "oauth" | "apikey" | "obsidian";
    syncedAtDisconnected: string;
    connectedSubtext?: string;
  }
> = {
  gmail: {
    name: "Gmail",
    description: "Connect your sending account",
    icon: Mail,
    iconTone: "bg-[#fde7d7] text-[#d14836]",
    pitch: "Connect Gmail so future sending features can use your Google account.",
    connectLabel: "Connect Gmail",
    connectType: "oauth",
    syncedAtDisconnected: "Google OAuth - gmail.send",
    connectedSubtext: "Connected for future sending features",
  },
  github: {
    name: "GitHub",
    description: "Commits, PRs, issues",
    icon: GitHubIcon,
    iconTone: "bg-[#d4fae8] text-[#0fa76e]",
    pitch: "Track your commits, pull requests, and issues. See your coding activity across all repositories.",
    connectLabel: "Connect GitHub",
    connectType: "oauth",
    syncedAtDisconnected: "OAuth - repo access",
  },
  obsidian: {
    name: "Obsidian",
    description: "Notes via Git sync",
    icon: ObsidianIcon,
    iconTone: "bg-[#e5e5e5] text-[#6c31e3]",
    pitch: "Track your Obsidian vault activity through its Git sync repo. Requires GitHub to be connected first.",
    connectLabel: "Connect Obsidian",
    connectType: "obsidian",
    syncedAtDisconnected: "Requires GitHub connection",
  },
  slack: {
    name: "Slack",
    description: "Messages, threads, channels",
    icon: SlackIcon,
    iconTone: "bg-[#f8ebd8] text-[#c37d0d]",
    pitch: "Track your message activity, surface active threads, and include Slack highlights in your daily digest.",
    connectLabel: "Connect Slack",
    connectType: "oauth",
    syncedAtDisconnected: "OAuth - workspace access",
  },
  linear: {
    name: "Linear",
    description: "Issues, cycles, projects",
    icon: LinearIcon,
    iconTone: "bg-[#f7e5e5] text-[#d45656]",
    pitch: "See tickets you closed, issues you opened, and cycle progress alongside your code and notes.",
    connectLabel: "Connect Linear",
    connectType: "apikey",
    syncedAtDisconnected: "API key - read-only",
  },
};

const PROVIDER_ORDER = ["gmail", "github", "obsidian", "slack", "linear"];

const comingSoon = [
  { name: "Notion", description: "Pages, databases, updates", icon: PanelsTopLeft },
  { name: "Toggl", description: "Time tracking, projects", icon: Clock3 },
];

export default function IntegrationsPage() {
  return (
    <Suspense fallback={null}>
      <IntegrationsContent />
    </Suspense>
  );
}

function IntegrationsContent() {
  const { integrations, loading, connectOAuth, connectApiKey, disconnect, sync, refetch } =
    useIntegrations();
  const { toast } = useToast();
  const searchParams = useSearchParams();

  const [connectingProvider, setConnectingProvider] = useState<string | null>(null);
  const [syncingProvider, setSyncingProvider] = useState<string | null>(null);
  const [apiKeyInput, setApiKeyInput] = useState("");
  const [repoInput, setRepoInput] = useState("");
  const [showInputFor, setShowInputFor] = useState<string | null>(null);

  useEffect(() => {
    const connected = searchParams.get("connected");
    const error = searchParams.get("error");
    if (connected) {
      toast({ title: `${PROVIDER_CONFIG[connected]?.name || connected} connected!` });
      refetch();
      window.history.replaceState({}, "", "/integrations");
    }
    if (error) {
      toast({
        title: `Failed to connect ${PROVIDER_CONFIG[error]?.name || error}`,
        variant: "destructive",
      });
      window.history.replaceState({}, "", "/integrations");
    }
  }, [searchParams, toast, refetch]);

  const integrationMap: Record<string, Integration> = {};
  for (const i of integrations) {
    integrationMap[i.provider] = i;
  }

  const connectedCount = integrations.filter((i) => i.status === "connected").length;

  const handleConnect = async (provider: string) => {
    const config = PROVIDER_CONFIG[provider];
    setConnectingProvider(provider);

    try {
      if (config.connectType === "oauth") {
        await connectOAuth(provider);
      } else if (config.connectType === "apikey") {
        setShowInputFor(provider);
        setConnectingProvider(null);
      } else if (config.connectType === "obsidian") {
        setShowInputFor(provider);
        setConnectingProvider(null);
      }
    } catch {
      toast({ title: "Connection failed", variant: "destructive" });
      setConnectingProvider(null);
    }
  };

  const handleApiKeySubmit = async (provider: string) => {
    setConnectingProvider(provider);
    try {
      await connectApiKey(provider, apiKeyInput, undefined);
      toast({ title: `${PROVIDER_CONFIG[provider].name} connected!` });
      setShowInputFor(null);
      setApiKeyInput("");
    } catch {
      toast({ title: "Invalid API key", variant: "destructive" });
    } finally {
      setConnectingProvider(null);
    }
  };

  const handleObsidianSubmit = async () => {
    setConnectingProvider("obsidian");
    try {
      await connectApiKey("obsidian", null, { repoFullName: repoInput });
      toast({ title: "Obsidian connected!" });
      setShowInputFor(null);
      setRepoInput("");
    } catch {
      toast({ title: "Failed to connect Obsidian. Is GitHub connected?", variant: "destructive" });
    } finally {
      setConnectingProvider(null);
    }
  };

  const handleDisconnect = async (provider: string) => {
    try {
      await disconnect(provider);
      toast({ title: `${PROVIDER_CONFIG[provider].name} disconnected` });
    } catch {
      toast({ title: "Failed to disconnect", variant: "destructive" });
    }
  };

  const handleSync = async (provider: string) => {
    setSyncingProvider(provider);
    try {
      const result = await sync(provider);
      toast({ title: `Synced ${result?.newEvents ?? 0} new events` });
    } catch {
      toast({ title: "Sync failed", variant: "destructive" });
    } finally {
      setSyncingProvider(null);
    }
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="min-h-full bg-transparent px-6 py-10 text-[#0d0d0d] md:px-8 md:py-12">
          <div className="flex w-full flex-col gap-12">
            <section>
              <h1 className="text-[40px] font-semibold leading-[1.1] tracking-[-0.8px] text-[#0d0d0d]">
                Integrations
              </h1>
              <p className="mt-3 text-[16px] leading-[1.5] text-[#666666]">
                Connect your tools. Activity syncs automatically in the background.
              </p>
            </section>

            <section>
              <p className="mb-4 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                {loading ? "Loading..." : `Connected - ${connectedCount} of ${PROVIDER_ORDER.length}`}
              </p>
              <div className="grid gap-4 md:grid-cols-2">
                {PROVIDER_ORDER.map((providerKey) => {
                  const config = PROVIDER_CONFIG[providerKey];
                  const integration = integrationMap[providerKey];
                  const isConnected = integration?.status === "connected";
                  const Icon = config.icon;

                  return (
                    <article
                      key={providerKey}
                      className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px] transition-colors hover:border-[rgba(0,0,0,0.08)]"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex items-center gap-3">
                          <div
                            className={`flex h-10 w-10 items-center justify-center rounded-[10px] ${config.iconTone}`}
                          >
                            <Icon className="h-5 w-5" />
                          </div>
                          <div>
                            <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                              {config.name}
                            </h2>
                            <p className="mt-1 text-[14px] leading-[1.5] text-[#666666]">
                              {config.description}
                            </p>
                          </div>
                        </div>
                        <span
                          className={
                            "inline-flex items-center gap-1 rounded-full px-3 py-1 font-mono text-[12px] font-semibold uppercase tracking-[0.6px] " +
                            (isConnected
                              ? "bg-[#d4fae8] text-[#0fa76e]"
                              : "bg-[#f5f5f5] text-[#666666]")
                          }
                        >
                          <span
                            className={
                              "h-1.5 w-1.5 rounded-full " +
                              (isConnected ? "bg-[#18E299]" : "bg-[#888888]")
                            }
                          />
                          {isConnected ? "Connected" : "Not connected"}
                        </span>
                      </div>

                      {isConnected && integration ? (
                        <div className="mt-5 space-y-3">
                          {integration.accountLabel && integration.accountValue && (
                            <div className="flex items-center justify-between">
                              <p className="text-[14px] leading-[1.5] text-[#666666]">
                                {integration.accountLabel}
                              </p>
                              <p className="font-mono text-[12px] font-medium text-[#333333]">
                                {integration.accountValue}
                              </p>
                            </div>
                          )}
                          {integration.supportsSync && integration.eventsLabel && integration.eventsValue && (
                            <div className="flex items-center justify-between">
                              <p className="text-[14px] leading-[1.5] text-[#666666]">
                                {integration.eventsLabel}
                              </p>
                              <p className="font-mono text-[12px] font-medium text-[#333333]">
                                {integration.eventsValue}
                              </p>
                            </div>
                          )}
                          {integration.supportsSync && integration.activitySparkline.length > 0 ? (
                            <div className="flex items-center justify-between">
                              <p className="text-[14px] leading-[1.5] text-[#666666]">Activity</p>
                              <div className="flex h-6 items-end gap-1">
                                {integration.activitySparkline.map((value, idx) => {
                                  const max = Math.max(...integration.activitySparkline, 1);
                                  return (
                                    <div
                                      key={`${providerKey}-bar-${idx}`}
                                      className="w-1.5 rounded-[2px]"
                                      style={{
                                        height: `${(value / max) * 100}%`,
                                        minHeight: value > 0 ? "2px" : "0px",
                                        backgroundColor:
                                          config.iconTone.includes("0fa76e")
                                            ? "#18E299"
                                            : config.iconTone.includes("3772cf")
                                              ? "#3772cf"
                                              : config.iconTone.includes("c37d0d")
                                                ? "#c37d0d"
                                                : "#d45656",
                                      }}
                                    />
                                  );
                                })}
                              </div>
                            </div>
                          ) : (
                            <p className="text-[14px] leading-[1.5] text-[#666666]">
                              {config.connectedSubtext || "Connected"}
                            </p>
                          )}
                        </div>
                      ) : (
                        <>
                          <p className="mt-5 text-[14px] leading-[1.5] text-[#666666]">
                            {config.pitch}
                          </p>
                          {showInputFor === providerKey && config.connectType === "apikey" && (
                            <div className="mt-3 flex gap-2">
                              <input
                                type="password"
                                placeholder="Paste your API key"
                                value={apiKeyInput}
                                onChange={(e) => setApiKeyInput(e.target.value)}
                                className="flex-1 rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none focus:border-[#0d0d0d]"
                              />
                              <Button
                                onClick={() => handleApiKeySubmit(providerKey)}
                                disabled={!apiKeyInput || connectingProvider === providerKey}
                                className="h-auto rounded-lg bg-[#0d0d0d] px-4 py-2 text-[14px] text-white"
                              >
                                {connectingProvider === providerKey ? (
                                  <Loader2 className="h-4 w-4 animate-spin" />
                                ) : (
                                  "Connect"
                                )}
                              </Button>
                            </div>
                          )}
                          {showInputFor === providerKey && config.connectType === "obsidian" && (
                            <div className="mt-3 flex gap-2">
                              <input
                                type="text"
                                placeholder="owner/vault-repo"
                                value={repoInput}
                                onChange={(e) => setRepoInput(e.target.value)}
                                className="flex-1 rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none focus:border-[#0d0d0d]"
                              />
                              <Button
                                onClick={handleObsidianSubmit}
                                disabled={!repoInput || connectingProvider === "obsidian"}
                                className="h-auto rounded-lg bg-[#0d0d0d] px-4 py-2 text-[14px] text-white"
                              >
                                {connectingProvider === "obsidian" ? (
                                  <Loader2 className="h-4 w-4 animate-spin" />
                                ) : (
                                  "Connect"
                                )}
                              </Button>
                            </div>
                          )}
                        </>
                      )}

                      <div className="my-5 h-px bg-[rgba(0,0,0,0.05)]" />

                      <div className="flex items-center justify-between gap-3">
                        <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                          {isConnected
                            ? integration?.lastSyncedAt || "Never synced"
                            : config.syncedAtDisconnected}
                        </p>
                        {isConnected ? (
                          <div className="flex gap-2">
                            {integration?.supportsSync && (
                              <Button
                                variant="outline"
                                onClick={() => handleSync(providerKey)}
                                disabled={syncingProvider === providerKey}
                                className="h-auto rounded-full border-[rgba(0,0,0,0.08)] bg-white px-4 py-2 text-[15px] font-medium text-[#0d0d0d]"
                              >
                                {syncingProvider === providerKey ? (
                                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                ) : (
                                  <RefreshCw className="mr-2 h-4 w-4" />
                                )}
                                Sync
                              </Button>
                            )}
                            <Button
                              variant="outline"
                              onClick={() => handleDisconnect(providerKey)}
                              className="h-auto rounded-full border-[rgba(0,0,0,0.08)] bg-white px-4 py-2 text-[15px] font-medium text-[#0d0d0d] hover:text-[#d45656]"
                            >
                              Disconnect
                            </Button>
                          </div>
                        ) : (
                          <Button
                            onClick={() => handleConnect(providerKey)}
                            disabled={connectingProvider === providerKey}
                            className="h-auto rounded-full bg-[#0d0d0d] px-4 py-2 text-[15px] font-medium text-white shadow-[rgba(0,0,0,0.06)_0px_1px_2px] hover:opacity-90"
                          >
                            {connectingProvider === providerKey ? (
                              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            ) : (
                              <Link2 className="mr-2 h-4 w-4" />
                            )}
                            {config.connectLabel}
                          </Button>
                        )}
                      </div>
                    </article>
                  );
                })}
              </div>
            </section>

            <section>
              <p className="mb-4 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                Coming soon
              </p>
              <div className="space-y-3">
                {comingSoon.map((item) => {
                  const Icon = item.icon;
                  return (
                    <article
                      key={item.name}
                      className="flex items-center gap-4 rounded-[16px] border border-dashed border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]"
                    >
                      <div className="flex h-9 w-9 items-center justify-center rounded-[8px] bg-[#f5f5f5] text-[#666666]">
                        <Icon className="h-4 w-4" />
                      </div>
                      <div>
                        <h3 className="text-[16px] font-medium leading-[1.5] text-[#0d0d0d]">
                          {item.name}
                        </h3>
                        <p className="text-[14px] leading-[1.5] text-[#666666]">
                          {item.description}
                        </p>
                      </div>
                      <span className="ml-auto rounded-full bg-[#f5f5f5] px-3 py-1 font-mono text-[12px] font-semibold uppercase tracking-[0.6px] text-[#666666]">
                        Soon
                      </span>
                    </article>
                  );
                })}
              </div>
            </section>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
