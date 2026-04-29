"use client";

import { useEffect, useState, Suspense } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Settings,
  Shield,
  AlertCircle,
  CheckCircle,
  Loader2,
  Save,
  Clock,
  Brain,
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
      await updateProfile("", profile?.knowledgeAreas ?? []);
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
        <div className="min-h-screen bg-background">
          <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
            <div className="mb-8">
              <div className="flex items-center gap-3 mb-2">
                <Settings className="h-8 w-8 text-blue-600" />
                <h1 className="text-3xl font-bold text-foreground">Settings</h1>
              </div>
              <p className="text-muted-foreground">
                Manage your account preferences.
              </p>
            </div>

            <Tabs value={activeTab} onValueChange={handleTabChange} className="space-y-6">
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="timezone" className="flex items-center gap-2">
                  <Clock className="h-4 w-4" />
                  Timezone
                </TabsTrigger>
                <TabsTrigger value="account" className="flex items-center gap-2">
                  <Shield className="h-4 w-4" />
                  Account
                </TabsTrigger>
                <TabsTrigger value="memory" className="flex items-center gap-2">
                  <Brain className="h-4 w-4" />
                  Memory
                </TabsTrigger>
              </TabsList>

              <TabsContent value="timezone" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Clock className="h-5 w-5" />
                      Timezone Settings
                    </CardTitle>
                    <CardDescription>
                      Set your timezone preference for times shown across the app.
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="timezone">Current Timezone</Label>
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
                          size="sm"
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
                        <p className="text-sm text-muted-foreground">
                          Offset: {timezoneOffset}
                        </p>
                      )}
                    </div>

                    {timezoneError && (
                      <Alert variant="destructive">
                        <AlertCircle className="h-4 w-4" />
                        <AlertDescription>{timezoneError}</AlertDescription>
                      </Alert>
                    )}

                    {timezoneSuccess && (
                      <Alert>
                        <CheckCircle className="h-4 w-4" />
                        <AlertDescription>
                          Timezone updated successfully.
                        </AlertDescription>
                      </Alert>
                    )}
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="account" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Shield className="h-5 w-5" />
                      Account Management
                    </CardTitle>
                    <CardDescription>
                      Basic account details for your current workspace membership.
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="p-4 border rounded-lg bg-secondary">
                      <h4 className="font-medium mb-2">Account Information</h4>
                      <div className="space-y-2 text-sm text-muted-foreground">
                        <p>
                          <span className="font-medium">Email:</span> {user.email}
                        </p>
                        <p>
                          <span className="font-medium">Member since:</span>{" "}
                          {user.createdAt
                            ? new Date(user.createdAt).toLocaleDateString()
                            : "Unknown"}
                        </p>
                        <p>
                          <span className="font-medium">Timezone:</span>{" "}
                          {selectedTimezone || user.timezone || "UTC±0"}
                        </p>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="memory" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Brain className="h-5 w-5" />
                      Memory
                    </CardTitle>
                    <CardDescription>
                      This is what the system knows about you, built from your onboarding and activity.
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    {profile?.profileMarkdown ? (
                      <>
                        <pre className="whitespace-pre-wrap font-mono text-sm leading-[1.7] text-foreground">
                          {profile.profileMarkdown}
                        </pre>
                        <div className="mt-6 flex justify-end">
                          <button
                            onClick={handleClearMemory}
                            onBlur={() => setConfirmClear(false)}
                            disabled={isClearingMemory}
                            className="inline-flex items-center gap-2 rounded-full border px-4 py-2 text-[15px] font-medium transition-colors"
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
                      <p className="text-muted-foreground">
                        No profile data yet. Complete onboarding on the Personal page to get started.
                      </p>
                    )}
                  </CardContent>
                </Card>
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
