"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import AuthGuard from "@/components/AuthGuard";
import DashboardLayout from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/use-toast";
import {
  useIntegrations,
  Integration,
  IntegrationResourceOption,
} from "@/hooks/useIntegrations";
import {
  Check,
  Link2,
  Loader2,
  PanelsTopLeft,
  RefreshCw,
  Settings2,
} from "lucide-react";
import {
  GitHubIcon,
  SlackIcon,
  LinearIcon,
  ObsidianIcon,
} from "@/components/icons/BrandIcons";
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
    connectType: "oauth" | "manual";
    disconnectedHint: string;
  }
> = {
  github: {
    name: "GitHub",
    description: "Repo webhooks, commits, PRs, issues",
    icon: GitHubIcon,
    iconTone: "bg-[#d4fae8] text-[#0fa76e]",
    pitch:
      "Install the Pulse GitHub App, choose the repositories you want to track, and stream repo activity into your workspace.",
    connectLabel: "Install GitHub App",
    connectType: "oauth",
    disconnectedHint: "GitHub App install required",
  },
  obsidian: {
    name: "Obsidian",
    description: "Vault activity through a Git-backed repo",
    icon: ObsidianIcon,
    iconTone: "bg-[#e5e5e5] text-[#6c31e3]",
    pitch:
      "Pick one GitHub-tracked repo as your vault source so note edits flow in without separate polling.",
    connectLabel: "Enable Obsidian",
    connectType: "manual",
    disconnectedHint: "Requires GitHub first",
  },
  slack: {
    name: "Slack",
    description: "Channel message events",
    icon: SlackIcon,
    iconTone: "bg-[#f8ebd8] text-[#c37d0d]",
    pitch:
      "Install the Slack app, then limit tracking to the channels that should contribute activity to Pulse.",
    connectLabel: "Connect Slack",
    connectType: "oauth",
    disconnectedHint: "OAuth workspace install",
  },
  linear: {
    name: "Linear",
    description: "Team issues, projects, cycles",
    icon: LinearIcon,
    iconTone: "bg-[#f7e5e5] text-[#d45656]",
    pitch:
      "Authorize the Linear app and choose the teams whose activity should appear in your Pulse feed.",
    connectLabel: "Connect Linear",
    connectType: "oauth",
    disconnectedHint: "OAuth workspace install",
  },
};

const PROVIDER_ORDER = ["github", "obsidian", "slack", "linear"];

const comingSoon = [
  { name: "Notion", description: "Pages, databases, updates", icon: PanelsTopLeft },
];

export default function IntegrationsPage() {
  return (
    <Suspense fallback={null}>
      <IntegrationsContent />
    </Suspense>
  );
}

function IntegrationsContent() {
  const {
    integrations,
    loading,
    connectOAuth,
    connect,
    getResources,
    updateScope,
    disconnect,
    sync,
    refetch,
  } = useIntegrations();
  const { toast } = useToast();
  const searchParams = useSearchParams();

  const [connectingProvider, setConnectingProvider] = useState<string | null>(null);
  const [syncingProvider, setSyncingProvider] = useState<string | null>(null);
  const [loadingResourcesFor, setLoadingResourcesFor] = useState<string | null>(null);
  const [savingScopeFor, setSavingScopeFor] = useState<string | null>(null);
  const [scopeEditorFor, setScopeEditorFor] = useState<string | null>(null);
  const [resourceOptions, setResourceOptions] = useState<Record<string, IntegrationResourceOption[]>>({});
  const [scopeDrafts, setScopeDrafts] = useState<Record<string, string[]>>({});

  useEffect(() => {
    const connected = searchParams.get("connected");
    const error = searchParams.get("error");
    if (connected) {
      toast({ title: `${PROVIDER_CONFIG[connected]?.name || connected} connected` });
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
  for (const integration of integrations) {
    integrationMap[integration.provider] = integration;
  }

  const connectedCount = integrations.filter((i) => i.status === "connected").length;

  const handleConnect = async (provider: string) => {
    const config = PROVIDER_CONFIG[provider];
    setConnectingProvider(provider);

    try {
      if (config.connectType === "oauth") {
        await connectOAuth(provider);
        return;
      }

      await connect(provider);
      toast({ title: `${config.name} connected` });
    } catch (error) {
      toast({
        title: error instanceof Error ? error.message : "Connection failed",
        variant: "destructive",
      });
    } finally {
      setConnectingProvider(null);
    }
  };

  const openScopeEditor = async (provider: string, integration: Integration) => {
    setLoadingResourcesFor(provider);
    try {
      const options = await getResources(provider);
      setResourceOptions((current) => ({ ...current, [provider]: options }));
      setScopeDrafts((current) => ({
        ...current,
        [provider]: integration.selectedResourceIds ?? [],
      }));
      setScopeEditorFor(provider);
    } catch (error) {
      toast({
        title: error instanceof Error ? error.message : "Failed to load scope options",
        variant: "destructive",
      });
    } finally {
      setLoadingResourcesFor(null);
    }
  };

  const toggleDraftSelection = (provider: string, resourceId: string) => {
    setScopeDrafts((current) => {
      const existing = current[provider] ?? [];
      const alreadySelected = existing.includes(resourceId);
      if (provider === "obsidian") {
        return { ...current, [provider]: alreadySelected ? [] : [resourceId] };
      }
      return {
        ...current,
        [provider]: alreadySelected
          ? existing.filter((id) => id !== resourceId)
          : [...existing, resourceId],
      };
    });
  };

  const handleSaveScope = async (provider: string) => {
    setSavingScopeFor(provider);
    try {
      await updateScope(provider, scopeDrafts[provider] ?? []);
      toast({ title: `${PROVIDER_CONFIG[provider].name} scope updated` });
      setScopeEditorFor(null);
    } catch (error) {
      toast({
        title: error instanceof Error ? error.message : "Failed to save scope",
        variant: "destructive",
      });
    } finally {
      setSavingScopeFor(null);
    }
  };

  const handleDisconnect = async (provider: string) => {
    try {
      await disconnect(provider);
      toast({ title: `${PROVIDER_CONFIG[provider].name} disconnected` });
      setScopeEditorFor((current) => (current === provider ? null : current));
    } catch {
      toast({ title: "Failed to disconnect", variant: "destructive" });
    }
  };

  const handleSync = async (provider: string) => {
    setSyncingProvider(provider);
    try {
      const result = await sync(provider);
      toast({ title: `Synced ${result.newEvents} new events` });
    } catch {
      toast({ title: "Manual sync failed", variant: "destructive" });
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
              <p className="mt-3 max-w-3xl text-[16px] leading-[1.6] text-[#666666]">
                Connect your tools, choose the repos, channels, and teams that matter,
                and let activity arrive through webhooks instead of background polling.
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
                  const statusTone =
                    integration?.webhookStatus === "active"
                      ? "bg-[#d4fae8] text-[#0fa76e]"
                      : "bg-[#fff4db] text-[#a16207]";

                  return (
                    <article
                      key={providerKey}
                      className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]"
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
                            (isConnected ? "bg-[#d4fae8] text-[#0fa76e]" : "bg-[#f5f5f5] text-[#666666]")
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
                            <div className="flex items-center justify-between gap-3">
                              <p className="text-[14px] leading-[1.5] text-[#666666]">
                                {integration.accountLabel}
                              </p>
                              <p className="font-mono text-[12px] font-medium text-[#333333]">
                                {integration.accountValue}
                              </p>
                            </div>
                          )}
                          {integration.eventsLabel && integration.eventsValue && (
                            <div className="flex items-center justify-between gap-3">
                              <p className="text-[14px] leading-[1.5] text-[#666666]">
                                {integration.eventsLabel}
                              </p>
                              <p className="font-mono text-[12px] font-medium text-[#333333]">
                                {integration.eventsValue}
                              </p>
                            </div>
                          )}
                          <div className="flex items-center justify-between gap-3">
                            <p className="text-[14px] leading-[1.5] text-[#666666]">Scope</p>
                            <p className="font-mono text-[12px] font-medium text-[#333333]">
                              {integration.scopeSummary || "Not configured"}
                            </p>
                          </div>
                          <div className="flex items-center justify-between gap-3">
                            <p className="text-[14px] leading-[1.5] text-[#666666]">Webhook</p>
                            <span
                              className={`inline-flex items-center gap-1 rounded-full px-3 py-1 font-mono text-[11px] font-semibold uppercase tracking-[0.6px] ${statusTone}`}
                            >
                              <span
                                className={
                                  "h-1.5 w-1.5 rounded-full " +
                                  (integration.webhookStatus === "active"
                                    ? "bg-[#18E299]"
                                    : "bg-[#d97706]")
                                }
                              />
                              {integration.webhookStatus.replace(/_/g, " ")}
                            </span>
                          </div>
                          {integration.webhookError && (
                            <p className="rounded-[12px] bg-[#fff4db] px-3 py-2 text-[12px] leading-[1.5] text-[#9a6700]">
                              {integration.webhookError}
                            </p>
                          )}

                          {scopeEditorFor === providerKey && (
                            <div className="rounded-[14px] border border-[rgba(0,0,0,0.06)] bg-[#fafafa] p-4">
                              <div className="mb-3 flex items-center justify-between gap-3">
                                <div>
                                  <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.6px] text-[#666666]">
                                    Select scope
                                  </p>
                                  <p className="mt-1 text-[13px] leading-[1.5] text-[#666666]">
                                    {providerKey === "obsidian"
                                      ? "Choose one Git-backed vault repo."
                                      : "Only selected resources will feed activity into Pulse."}
                                  </p>
                                </div>
                                <Button
                                  variant="ghost"
                                  className="h-auto px-2 py-1 text-[12px] text-[#666666]"
                                  onClick={() => setScopeEditorFor(null)}
                                >
                                  Close
                                </Button>
                              </div>

                              <div className="max-h-[220px] space-y-2 overflow-y-auto">
                                {(resourceOptions[providerKey] ?? []).map((option) => {
                                  const checked = (scopeDrafts[providerKey] ?? []).includes(option.id);
                                  return (
                                    <label
                                      key={option.id}
                                      className="flex cursor-pointer items-center gap-3 rounded-[12px] border border-[rgba(0,0,0,0.05)] bg-white px-3 py-2"
                                    >
                                      <input
                                        type="checkbox"
                                        checked={checked}
                                        onChange={() => toggleDraftSelection(providerKey, option.id)}
                                        className="h-4 w-4 rounded border-[rgba(0,0,0,0.2)]"
                                      />
                                      <div className="min-w-0">
                                        <p className="text-[14px] leading-[1.4] text-[#0d0d0d]">
                                          {option.label}
                                        </p>
                                        <p className="font-mono text-[11px] uppercase tracking-[0.6px] text-[#999999]">
                                          {option.type}
                                        </p>
                                      </div>
                                      {checked && <Check className="ml-auto h-4 w-4 text-[#18E299]" />}
                                    </label>
                                  );
                                })}
                              </div>

                              <div className="mt-4 flex items-center justify-end gap-2">
                                <Button
                                  variant="outline"
                                  className="h-auto rounded-full px-4 py-2 text-[14px]"
                                  onClick={() => setScopeEditorFor(null)}
                                >
                                  Cancel
                                </Button>
                                <Button
                                  className="h-auto rounded-full bg-[#0d0d0d] px-4 py-2 text-[14px] text-white"
                                  onClick={() => handleSaveScope(providerKey)}
                                  disabled={savingScopeFor === providerKey}
                                >
                                  {savingScopeFor === providerKey ? (
                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                  ) : (
                                    <Settings2 className="mr-2 h-4 w-4" />
                                  )}
                                  Save scope
                                </Button>
                              </div>
                            </div>
                          )}
                        </div>
                      ) : (
                        <p className="mt-5 text-[14px] leading-[1.6] text-[#666666]">
                          {config.pitch}
                        </p>
                      )}

                      <div className="my-5 h-px bg-[rgba(0,0,0,0.05)]" />

                      <div className="flex items-center justify-between gap-3">
                        <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                          {isConnected
                            ? integration?.lastActivityLabel || "Waiting for events"
                            : config.disconnectedHint}
                        </p>
                        {isConnected ? (
                          <div className="flex flex-wrap justify-end gap-2">
                            <Button
                              variant="outline"
                              onClick={() => openScopeEditor(providerKey, integration!)}
                              disabled={loadingResourcesFor === providerKey}
                              className="h-auto rounded-full border-[rgba(0,0,0,0.08)] bg-white px-4 py-2 text-[15px] font-medium text-[#0d0d0d]"
                            >
                              {loadingResourcesFor === providerKey ? (
                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                              ) : (
                                <Settings2 className="mr-2 h-4 w-4" />
                              )}
                              Scope
                            </Button>
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
