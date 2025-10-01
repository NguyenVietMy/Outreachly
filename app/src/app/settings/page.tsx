"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
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
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  Settings,
  Mail,
  Shield,
  Bell,
  Palette,
  Database,
  Zap,
  AlertTriangle,
  CheckCircle,
  Loader2,
  Save,
  Trash2,
  Eye,
  EyeOff,
} from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";

interface EmailProvider {
  id: string;
  name: string;
  type: "smtp" | "api";
  isActive: boolean;
  config: {
    host?: string;
    port?: number;
    username?: string;
    password?: string;
    apiKey?: string;
    fromEmail?: string;
    fromName?: string;
  };
}

interface UserSettings {
  emailNotifications: boolean;
  marketingEmails: boolean;
  theme: "light" | "dark" | "system";
  timezone: string;
  language: string;
  autoSave: boolean;
  showPreview: boolean;
}

export default function SettingsPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  // Email providers state
  const [emailProviders, setEmailProviders] = useState<EmailProvider[]>([]);
  const [loadingProviders, setLoadingProviders] = useState(true);

  // User settings state
  const [settings, setSettings] = useState<UserSettings>({
    emailNotifications: true,
    marketingEmails: false,
    theme: "system",
    timezone: "UTC",
    language: "en",
    autoSave: true,
    showPreview: true,
  });

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/auth");
    }
  }, [user, authLoading, router]);

  // Load settings and email providers on component mount
  useEffect(() => {
    if (user && !authLoading) {
      loadSettings();
      loadEmailProviders();
    }
  }, [user, authLoading]);

  const loadSettings = async () => {
    try {
      const response = await fetch("http://localhost:8080/api/settings", {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
      });
      if (response.ok) {
        const data = await response.json();
        // Update settings based on API response
        if (data.notificationSettings) {
          setSettings((prev) => ({
            ...prev,
            emailNotifications:
              data.notificationSettings.emailNotifications ??
              prev.emailNotifications,
            marketingEmails:
              data.notificationSettings.marketingEmails ?? prev.marketingEmails,
          }));
        }
      }
    } catch (error) {
      console.error("Failed to load settings:", error);
      toast({
        title: "Error",
        description: "Failed to load settings. Using defaults.",
        variant: "destructive",
      });
    }
  };

  const loadEmailProviders = async () => {
    try {
      setLoadingProviders(true);
      console.log("Loading email providers...");

      const response = await fetch(
        "http://localhost:8080/api/settings/email-providers",
        {
          method: "GET",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
        }
      );

      console.log("Response status:", response.status);

      if (response.ok) {
        const providers = await response.json();
        console.log("Loaded providers:", providers);

        // Ensure each provider has a config object with default values
        const providersWithDefaults = providers.map((provider: any) => ({
          ...provider,
          config: provider.config || {
            apiKey: "",
            fromEmail: "",
            fromName: "",
          },
        }));

        setEmailProviders(providersWithDefaults);
      } else {
        const errorText = await response.text();
        console.error("API Error:", response.status, errorText);

        if (response.status === 404) {
          throw new Error(
            "Backend API not found. Please make sure the backend is running and restarted."
          );
        }

        throw new Error(
          `Failed to load email providers: ${response.status} - ${errorText}`
        );
      }
    } catch (error) {
      console.error("Failed to load email providers:", error);
      toast({
        title: "Error",
        description: `Failed to load email providers: ${error instanceof Error ? error.message : "Unknown error"}`,
        variant: "destructive",
      });
    } finally {
      setLoadingProviders(false);
    }
  };

  const handleSaveSettings = async () => {
    setIsSaving(true);
    try {
      // Save notification settings
      const notificationSettings = {
        emailNotifications: settings.emailNotifications,
        marketingEmails: settings.marketingEmails,
      };

      const response = await fetch("http://localhost:8080/api/settings", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          notificationSettings: notificationSettings,
        }),
      });

      if (!response.ok) {
        throw new Error("Failed to save settings");
      }

      setHasUnsavedChanges(false);
      toast({
        title: "Settings Saved",
        description: "Your settings have been saved successfully.",
      });
    } catch (error) {
      console.error("Failed to save settings:", error);
      toast({
        title: "Error",
        description: `Failed to save settings: ${error instanceof Error ? error.message : "Unknown error"}`,
        variant: "destructive",
      });
    } finally {
      setIsSaving(false);
    }
  };

  const markAsChanged = () => {
    setHasUnsavedChanges(true);
  };

  const switchEmailProvider = async (
    providerId: string,
    config: EmailProvider["config"]
  ) => {
    try {
      console.log(`Switching to provider: ${providerId}`, config);
      setIsSaving(true);
      const response = await fetch(
        `http://localhost:8080/api/settings/email-providers/${providerId}/switch`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify(config),
        }
      );

      console.log("Switch response status:", response.status);

      if (!response.ok) {
        const errorText = await response.text();
        console.error("Switch error:", errorText);
        throw new Error("Failed to switch email provider");
      }

      // Reload providers to get updated status
      await loadEmailProviders();

      toast({
        title: "Email Provider Switched",
        description: `Successfully switched to ${providerId}.`,
      });
    } catch (error) {
      console.error("Failed to switch email provider:", error);
      toast({
        title: "Error",
        description: "Failed to switch email provider. Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsSaving(false);
    }
  };

  const testEmailProvider = async (
    providerId: string,
    config: EmailProvider["config"]
  ) => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/settings/email-providers/${providerId}/test`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify(config),
        }
      );

      const result = await response.json();

      if (result.isValid) {
        toast({
          title: "Configuration Valid",
          description: "Email provider configuration is working correctly.",
        });
      } else {
        toast({
          title: "Configuration Invalid",
          description: result.message || "Please check your configuration.",
          variant: "destructive",
        });
      }
    } catch (error) {
      console.error("Failed to test email provider:", error);
      toast({
        title: "Test Failed",
        description: "Failed to test email provider configuration.",
        variant: "destructive",
      });
    }
  };

  const handleDeactivateAccount = async () => {
    setIsLoading(true);
    try {
      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 2000));

      toast({
        title: "Account Deactivated",
        description: "Your account has been deactivated successfully.",
      });

      // Redirect to login
      router.push("/auth");
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to deactivate account. Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const updateEmailProvider = (
    providerId: string,
    updates: Partial<EmailProvider>
  ) => {
    setEmailProviders((prev) =>
      prev.map((provider) =>
        provider.id === providerId
          ? {
              ...provider,
              ...updates,
              config: updates.config
                ? { ...provider.config, ...updates.config }
                : provider.config,
            }
          : provider
      )
    );
  };

  if (authLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="min-h-screen bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-50">
          <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
            {/* Header */}
            <div className="mb-8">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-3">
                  <Settings className="h-8 w-8 text-blue-600" />
                  <h1 className="text-3xl font-bold text-gray-900">Settings</h1>
                </div>
                <Button
                  onClick={handleSaveSettings}
                  disabled={isSaving}
                  variant={hasUnsavedChanges ? "default" : "outline"}
                >
                  {isSaving ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Saving...
                    </>
                  ) : (
                    <>
                      <Save className="mr-2 h-4 w-4" />
                      {hasUnsavedChanges ? "Save Changes" : "Save All Settings"}
                    </>
                  )}
                </Button>
              </div>
              <p className="text-gray-600">
                Manage your account preferences and email configuration
                {hasUnsavedChanges && (
                  <span className="ml-2 text-orange-600 font-medium">
                    • You have unsaved changes
                  </span>
                )}
              </p>
            </div>

            <Tabs defaultValue="email" className="space-y-6">
              <TabsList className="grid w-full grid-cols-4">
                <TabsTrigger value="email" className="flex items-center gap-2">
                  <Mail className="h-4 w-4" />
                  Email
                </TabsTrigger>
                <TabsTrigger
                  value="notifications"
                  className="flex items-center gap-2"
                >
                  <Bell className="h-4 w-4" />
                  Notifications
                </TabsTrigger>
                <TabsTrigger
                  value="appearance"
                  className="flex items-center gap-2"
                >
                  <Palette className="h-4 w-4" />
                  Appearance
                </TabsTrigger>
                <TabsTrigger
                  value="account"
                  className="flex items-center gap-2"
                >
                  <Shield className="h-4 w-4" />
                  Account
                </TabsTrigger>
              </TabsList>

              {/* Email Settings */}
              <TabsContent value="email" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Mail className="h-5 w-5" />
                      Email Providers
                    </CardTitle>
                    <CardDescription>
                      Configure your email sending providers
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-6">
                    {loadingProviders ? (
                      <div className="flex items-center justify-center py-8">
                        <Loader2 className="h-6 w-6 animate-spin mr-2" />
                        <span>Loading email providers...</span>
                      </div>
                    ) : (
                      emailProviders.map((provider) => (
                        <div
                          key={provider.id}
                          className="border rounded-lg p-4"
                        >
                          <div className="flex items-center justify-between mb-4">
                            <div className="flex items-center gap-3">
                              <div
                                className={`w-3 h-3 rounded-full ${
                                  provider.isActive
                                    ? "bg-green-500"
                                    : "bg-gray-300"
                                }`}
                              />
                              <h3 className="font-medium">{provider.name}</h3>
                              {provider.isActive && (
                                <span className="text-xs bg-green-100 text-green-800 px-2 py-1 rounded">
                                  Active
                                </span>
                              )}
                            </div>
                            <div className="flex items-center gap-2">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() =>
                                  testEmailProvider(
                                    provider.id,
                                    provider.config
                                  )
                                }
                                disabled={
                                  !provider.config?.apiKey ||
                                  !provider.config?.fromEmail
                                }
                              >
                                Test
                              </Button>
                              <Switch
                                checked={provider.isActive}
                                onCheckedChange={(checked) => {
                                  console.log(
                                    `Switching ${provider.id} to ${checked}`
                                  );
                                  if (checked) {
                                    // Enable this provider and disable all others
                                    setEmailProviders((prev) => {
                                      const updated = prev.map((p) => ({
                                        ...p,
                                        isActive: p.id === provider.id,
                                      }));
                                      console.log(
                                        "Updated providers:",
                                        updated
                                      );
                                      return updated;
                                    });
                                    // Call the backend to switch
                                    switchEmailProvider(
                                      provider.id,
                                      provider.config
                                    );
                                  } else {
                                    // Just disable this provider locally
                                    updateEmailProvider(provider.id, {
                                      isActive: false,
                                    });
                                    markAsChanged();
                                  }
                                }}
                              />
                            </div>
                          </div>

                          {provider.isActive && (
                            <div className="space-y-4">
                              {provider.type === "api" && (
                                <>
                                  <div className="space-y-2">
                                    <Label>API Key</Label>
                                    <div className="flex gap-2">
                                      <Input
                                        type={showApiKey ? "text" : "password"}
                                        value={provider.config?.apiKey || ""}
                                        onChange={(e) => {
                                          updateEmailProvider(provider.id, {
                                            config: {
                                              ...provider.config,
                                              apiKey: e.target.value,
                                            },
                                          });
                                          markAsChanged();
                                        }}
                                        placeholder="Enter your API key"
                                      />
                                      <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() =>
                                          setShowApiKey(!showApiKey)
                                        }
                                      >
                                        {showApiKey ? (
                                          <EyeOff className="h-4 w-4" />
                                        ) : (
                                          <Eye className="h-4 w-4" />
                                        )}
                                      </Button>
                                    </div>
                                  </div>
                                </>
                              )}

                              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div className="space-y-2">
                                  <Label>From Email</Label>
                                  <Input
                                    value={provider.config?.fromEmail || ""}
                                    onChange={(e) => {
                                      updateEmailProvider(provider.id, {
                                        config: {
                                          ...provider.config,
                                          fromEmail: e.target.value,
                                        },
                                      });
                                      markAsChanged();
                                    }}
                                    placeholder="noreply@yourdomain.com"
                                  />
                                </div>
                                <div className="space-y-2">
                                  <Label>From Name</Label>
                                  <Input
                                    value={provider.config?.fromName || ""}
                                    onChange={(e) => {
                                      updateEmailProvider(provider.id, {
                                        config: {
                                          ...provider.config,
                                          fromName: e.target.value,
                                        },
                                      });
                                      markAsChanged();
                                    }}
                                    placeholder="Your Company"
                                  />
                                </div>
                              </div>
                            </div>
                          )}
                        </div>
                      ))
                    )}
                  </CardContent>
                </Card>
              </TabsContent>

              {/* Notifications Settings */}
              <TabsContent value="notifications" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Bell className="h-5 w-5" />
                      Notification Preferences
                    </CardTitle>
                    <CardDescription>
                      Choose what notifications you want to receive
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-6">
                    <div className="space-y-4">
                      <div className="flex items-center justify-between">
                        <div>
                          <Label className="text-base">
                            Email Notifications
                          </Label>
                          <p className="text-sm text-gray-500">
                            Receive notifications about email campaigns and
                            system updates
                          </p>
                        </div>
                        <Switch
                          checked={settings.emailNotifications}
                          onCheckedChange={(checked) => {
                            setSettings((prev) => ({
                              ...prev,
                              emailNotifications: checked,
                            }));
                            markAsChanged();
                          }}
                        />
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <Label className="text-base">Marketing Emails</Label>
                          <p className="text-sm text-gray-500">
                            Receive tips, updates, and promotional content
                          </p>
                        </div>
                        <Switch
                          checked={settings.marketingEmails}
                          onCheckedChange={(checked) => {
                            setSettings((prev) => ({
                              ...prev,
                              marketingEmails: checked,
                            }));
                            markAsChanged();
                          }}
                        />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>

              {/* Appearance Settings */}
              <TabsContent value="appearance" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Palette className="h-5 w-5" />
                      Appearance & Preferences
                    </CardTitle>
                    <CardDescription>Customize your experience</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-6">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      <div className="space-y-2">
                        <Label>Theme</Label>
                        <Select
                          value={settings.theme}
                          onValueChange={(
                            value: "light" | "dark" | "system"
                          ) => {
                            setSettings((prev) => ({ ...prev, theme: value }));
                            markAsChanged();
                          }}
                        >
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="light">Light</SelectItem>
                            <SelectItem value="dark">Dark</SelectItem>
                            <SelectItem value="system">System</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>

                      <div className="space-y-2">
                        <Label>Timezone</Label>
                        <Select
                          value={settings.timezone}
                          onValueChange={(value) => {
                            setSettings((prev) => ({
                              ...prev,
                              timezone: value,
                            }));
                            markAsChanged();
                          }}
                        >
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="UTC">UTC</SelectItem>
                            <SelectItem value="America/New_York">
                              Eastern Time
                            </SelectItem>
                            <SelectItem value="America/Chicago">
                              Central Time
                            </SelectItem>
                            <SelectItem value="America/Denver">
                              Mountain Time
                            </SelectItem>
                            <SelectItem value="America/Los_Angeles">
                              Pacific Time
                            </SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                    </div>

                    <div className="space-y-4">
                      <div className="flex items-center justify-between">
                        <div>
                          <Label className="text-base">Auto-save</Label>
                          <p className="text-sm text-gray-500">
                            Automatically save your work as you type
                          </p>
                        </div>
                        <Switch
                          checked={settings.autoSave}
                          onCheckedChange={(checked) => {
                            setSettings((prev) => ({
                              ...prev,
                              autoSave: checked,
                            }));
                            markAsChanged();
                          }}
                        />
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <Label className="text-base">Show Preview</Label>
                          <p className="text-sm text-gray-500">
                            Show email preview by default
                          </p>
                        </div>
                        <Switch
                          checked={settings.showPreview}
                          onCheckedChange={(checked) => {
                            setSettings((prev) => ({
                              ...prev,
                              showPreview: checked,
                            }));
                            markAsChanged();
                          }}
                        />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>

              {/* Account Settings */}
              <TabsContent value="account" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Shield className="h-5 w-5" />
                      Account Management
                    </CardTitle>
                    <CardDescription>
                      Manage your account security and data
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-6">
                    <div className="space-y-4">
                      <div className="p-4 border rounded-lg bg-gray-50">
                        <h4 className="font-medium mb-2">
                          Account Information
                        </h4>
                        <div className="space-y-2 text-sm text-gray-600">
                          <p>
                            <span className="font-medium">Email:</span>{" "}
                            {user.email}
                          </p>
                          <p>
                            <span className="font-medium">Member since:</span>{" "}
                            {new Date().toLocaleDateString()}
                          </p>
                        </div>
                      </div>

                      <Alert className="border-red-200 bg-red-50">
                        <AlertTriangle className="h-4 w-4 text-red-600" />
                        <AlertDescription className="text-red-800">
                          <div className="font-medium mb-2">Danger Zone</div>
                          <p className="text-sm">
                            Deactivating your account will permanently disable
                            access to all features and data. This action cannot
                            be undone.
                          </p>
                        </AlertDescription>
                      </Alert>

                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button
                            variant="destructive"
                            className="w-full md:w-auto"
                          >
                            <Trash2 className="mr-2 h-4 w-4" />
                            Deactivate Account
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>
                              Deactivate Account
                            </AlertDialogTitle>
                            <AlertDialogDescription>
                              Are you sure you want to deactivate your account?
                              This will:
                              <ul className="list-disc list-inside mt-2 space-y-1">
                                <li>Permanently disable your account</li>
                                <li>Stop all email campaigns</li>
                                <li>Delete all your data</li>
                                <li>Remove access to all features</li>
                              </ul>
                              <p className="mt-2 font-medium text-red-600">
                                This action cannot be undone.
                              </p>
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>Cancel</AlertDialogCancel>
                            <AlertDialogAction
                              onClick={handleDeactivateAccount}
                              disabled={isLoading}
                              className="bg-red-600 hover:bg-red-700"
                            >
                              {isLoading ? (
                                <>
                                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                  Deactivating...
                                </>
                              ) : (
                                "Yes, Deactivate Account"
                              )}
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    </div>
                  </CardContent>
                </Card>

                {/* Placeholder sections for future features */}
                <Card className="opacity-60">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Database className="h-5 w-5" />
                      Data Management
                    </CardTitle>
                    <CardDescription>
                      Coming soon - Manage your data and exports
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    <div className="text-center py-8 text-gray-500">
                      <Database className="h-12 w-12 mx-auto mb-4 text-gray-400" />
                      <p>
                        Data export, import, and management features coming soon
                      </p>
                    </div>
                  </CardContent>
                </Card>

                <Card className="opacity-60">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Zap className="h-5 w-5" />
                      Integrations
                    </CardTitle>
                    <CardDescription>
                      Coming soon - Connect with other tools
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    <div className="text-center py-8 text-gray-500">
                      <Zap className="h-12 w-12 mx-auto mb-4 text-gray-400" />
                      <p>
                        Third-party integrations and API management coming soon
                      </p>
                    </div>
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
