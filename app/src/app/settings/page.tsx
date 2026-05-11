"use client";

import { useEffect, useState, Suspense } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Loader2,
  Save,
  Trash2,
} from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { usePersonal } from "@/hooks/usePersonal";

const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "https://api.outreach-ly.com";

const VALID_TABS = ["timezone", "account", "memory"] as const;
type SettingsTab = (typeof VALID_TABS)[number];

function SettingsContent() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const { toast } = useToast();
  const { profile, updateProfile } = usePersonal();

  const tabParam = searchParams.get("tab") as SettingsTab | null;
  const activeTab: SettingsTab =
    tabParam && VALID_TABS.includes(tabParam) ? tabParam : "timezone";

  const [availableTimezones, setAvailableTimezones] = useState<string[]>([]);
  const [selectedTimezone, setSelectedTimezone] = useState<string>("");
  const [timezoneOffset, setTimezoneOffset] = useState<string>("");
  const [isUpdatingTimezone, setIsUpdatingTimezone] = useState(false);
  const [timezoneError, setTimezoneError] = useState<string>("");
  const [timezoneSuccess, setTimezoneSuccess] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const [isClearingMemory, setIsClearingMemory] = useState(false);

  const handleTabChange = (value: string) => {
    router.replace(`/settings?tab=${value}`, { scroll: false });
  };

  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/auth");
    }
  }, [user, authLoading, router]);

  useEffect(() => {
    if (!user) return;

    const loadTimezones = async () => {
      try {
        const response = await fetch(`${API_URL}/api/settings/timezones`, {
          credentials: "include",
        });
        if (!response.ok) {
          throw new Error("Failed to load timezones");
        }
        const data = await response.json();
        setAvailableTimezones(Array.isArray(data) ? data : []);
      } catch (error) {
        console.error("Failed to load timezones:", error);
      }
    };

    const loadCurrentTimezone = async () => {
      try {
        const response = await fetch(`${API_URL}/api/settings/timezone`, {
          credentials: "include",
        });
        if (!response.ok) {
          throw new Error("Failed to load current timezone");
        }
        const data = await response.json();
        setSelectedTimezone(data.timezone || user.timezone || "UTC±0");
        setTimezoneOffset(data.timezoneOffset || "");
      } catch (error) {
        console.error("Failed to load current timezone:", error);
        setSelectedTimezone(user.timezone || "UTC±0");
      }
    };

    loadTimezones();
    loadCurrentTimezone();
  }, [user]);

  const handleTimezoneUpdate = async () => {
    if (!selectedTimezone) return;

    setIsUpdatingTimezone(true);
    setTimezoneError("");
    setTimezoneSuccess(false);

    try {
      const response = await fetch(`${API_URL}/api/settings/timezone`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({ timezone: selectedTimezone }),
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error || "Failed to update timezone");
      }

      const data = await response.json();
      setTimezoneOffset(data.timezoneOffset || "");
      setTimezoneSuccess(true);
      toast({
        title: "Timezone Updated",
        description: `Timezone updated to ${selectedTimezone}`,
      });
      setTimeout(() => setTimezoneSuccess(false), 3000);
    } catch (error) {
      console.error("Failed to update timezone:", error);
      setTimezoneError(
        error instanceof Error ? error.message : "Failed to update timezone"
      );
    } finally {
      setIsUpdatingTimezone(false);
    }
  };

  const handleClearMemory = async () => {
    if (!confirmClear) {
      setConfirmClear(true);
      return;
    }
    setIsClearingMemory(true);
    try {
      await updateProfile("");
      toast({
        title: "Memory Cleared",
        description: "Your profile memory has been cleared.",
      });
    } catch {
      toast({
        title: "Error",
        description: "Failed to clear memory.",
        variant: "destructive",
      });
    } finally {
      setIsClearingMemory(false);
      setConfirmClear(false);
    }
  };

  if (!user) {
    return null;
  }

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="min-h-full bg-transparent px-6 py-10 text-[#0d0d0d] md:px-8 md:py-12">
          <div className="flex w-full flex-col gap-8 max-w-4xl">
            <div>
              <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                Account
              </p>
              <h1 className="mt-2 font-mono text-[40px] font-semibold leading-[1.1] tracking-[-0.8px] text-[#0d0d0d]">
                Settings
              </h1>
              <p className="mt-3 max-w-2xl text-[18px] leading-[1.5] text-[#333333]">
                Manage your account preferences.
              </p>
            </div>

            <Tabs value={activeTab} onValueChange={handleTabChange} className="space-y-6">
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="timezone">Timezone</TabsTrigger>
                <TabsTrigger value="account">Account</TabsTrigger>
                <TabsTrigger value="memory">Memory</TabsTrigger>
              </TabsList>

              <TabsContent value="timezone" className="space-y-6">
                <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                  <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                    Timezone Settings
                  </h2>
                  <p className="mt-1 text-[14px] text-[#666666]">
                    Set your timezone preference for times shown across the app.
                  </p>
                  <div className="mt-6 space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="timezone" className="text-[14px] font-medium text-[#333333]">
                        Current Timezone
                      </Label>
                      <div className="flex items-center gap-2">
                        <Select
                          value={selectedTimezone || user?.timezone || "UTC±0"}
                          onValueChange={setSelectedTimezone}
                        >
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder="Select timezone" />
                          </SelectTrigger>
                          <SelectContent className="max-h-[200px] overflow-y-auto">
                            {availableTimezones.map((tz) => (
                              <SelectItem key={tz} value={tz}>
                                {tz}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <Button
                          onClick={handleTimezoneUpdate}
                          disabled={isUpdatingTimezone || !selectedTimezone}
                          className="rounded-full bg-[#0d0d0d] px-6 py-2 text-[15px] font-medium text-white hover:opacity-90"
                        >
                          {isUpdatingTimezone ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <Save className="h-4 w-4" />
                          )}
                          Save
                        </Button>
                      </div>
                      {timezoneOffset && (
                        <p className="text-[13px] text-[#666666]">
                          Offset: {timezoneOffset}
                        </p>
                      )}
                    </div>

                    {timezoneError && (
                      <div className="rounded-[12px] border border-[rgba(212,86,86,0.3)] bg-[#fef2f2] px-4 py-3">
                        <p className="text-[14px] text-[#d45656]">{timezoneError}</p>
                      </div>
                    )}

                    {timezoneSuccess && (
                      <div className="rounded-[12px] border border-[rgba(24,226,153,0.3)] bg-[#d4fae8] px-4 py-3">
                        <p className="text-[14px] text-[#0fa76e]">
                          Timezone updated successfully.
                        </p>
                      </div>
                    )}
                  </div>
                </article>
              </TabsContent>

              <TabsContent value="account" className="space-y-6">
                <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                  <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                    Account Information
                  </h2>
                  <p className="mt-1 text-[14px] text-[#666666]">
                    Basic account details for your current membership.
                  </p>
                  <div className="mt-6 space-y-3 text-[14px]">
                    <div className="flex items-baseline gap-2">
                      <span className="font-medium text-[#333333]">Email</span>
                      <span className="text-[#666666]">{user.email}</span>
                    </div>
                    <div className="flex items-baseline gap-2">
                      <span className="font-medium text-[#333333]">Member since</span>
                      <span className="text-[#666666]">
                        {user.createdAt
                          ? new Date(user.createdAt).toLocaleDateString()
                          : "Unknown"}
                      </span>
                    </div>
                    <div className="flex items-baseline gap-2">
                      <span className="font-medium text-[#333333]">Timezone</span>
                      <span className="text-[#666666]">
                        {selectedTimezone || user.timezone || "UTC±0"}
                      </span>
                    </div>
                  </div>
                </article>
              </TabsContent>

              <TabsContent value="memory" className="space-y-6">
                <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                  <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                    Memory
                  </h2>
                  <p className="mt-1 text-[14px] text-[#666666]">
                    What the system knows about you, built from your onboarding and activity.
                  </p>
                  <div className="mt-6">
                    {profile?.profileMarkdown ? (
                      <>
                        <pre className="whitespace-pre-wrap font-mono text-[12px] leading-[1.7] tracking-[0.6px] text-[#333333]">
                          {profile.profileMarkdown}
                        </pre>
                        <div className="mt-6 flex justify-end">
                          <button
                            onClick={handleClearMemory}
                            onBlur={() => setConfirmClear(false)}
                            disabled={isClearingMemory}
                            className="inline-flex items-center gap-2 rounded-full border px-6 py-2 text-[15px] font-medium transition-colors"
                            style={{
                              color: confirmClear ? "#ffffff" : "#d45656",
                              borderColor: confirmClear ? "#d45656" : "rgba(212,86,86,0.3)",
                              backgroundColor: confirmClear ? "#d45656" : "transparent",
                            }}
                          >
                            {isClearingMemory ? (
                              <Loader2 className="h-4 w-4 animate-spin" />
                            ) : (
                              <Trash2 className="h-4 w-4" />
                            )}
                            {confirmClear ? "Confirm Clear" : "Clear Memory"}
                          </button>
                        </div>
                      </>
                    ) : (
                      <p className="text-[14px] text-[#666666]">
                        No profile data yet. Complete onboarding on the Personal page to get started.
                      </p>
                    )}
                  </div>
                </article>
              </TabsContent>
            </Tabs>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}

export default function SettingsPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center bg-background">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand-accent"></div>
        </div>
      }
    >
      <SettingsContent />
    </Suspense>
  );
}
