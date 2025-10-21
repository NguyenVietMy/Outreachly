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
  Zap,
  AlertCircle,
  CheckCircle,
  Loader2,
  Save,
  Trash2,
  Eye,
  EyeOff,
  Clock,
} from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";

const API_URL =
  process.env.NEXT_PUBLIC_API_URL || "https://api.outreach-ly.com";

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

export default function SettingsPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);

  // Email providers state
  const [emailProviders, setEmailProviders] = useState<EmailProvider[]>([]);
  const [loadingProviders, setLoadingProviders] = useState(true);

  // Resend configuration state
  const [resendConfig, setResendConfig] = useState({
    apiKey: "",
    fromEmail: "",
    fromName: "",
    domain: "",
  });
  const [loadingResendConfig, setLoadingResendConfig] = useState(false);

  // Timezone state
  const [availableTimezones, setAvailableTimezones] = useState<string[]>([]);
  const [selectedTimezone, setSelectedTimezone] = useState<string>("");
  const [timezoneOffset, setTimezoneOffset] = useState<string>("");
  const [isUpdatingTimezone, setIsUpdatingTimezone] = useState(false);
  const [timezoneError, setTimezoneError] = useState<string>("");
  const [timezoneSuccess, setTimezoneSuccess] = useState(false);
  const [hasResendConfig, setHasResendConfig] = useState(false);
  const [resendConfigModified, setResendConfigModified] = useState(false);
  const [isDomainVerified, setIsDomainVerified] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [isVerificationSent, setIsVerificationSent] = useState(false);
  const [isVerifyingDomain, setIsVerifyingDomain] = useState(false);
  const [isVerifyingCode, setIsVerifyingCode] = useState(false);
  const [verificationError, setVerificationError] = useState("");
  const [verificationSuccess, setVerificationSuccess] = useState("");

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/auth");
    }
  }, [user, authLoading, router]);

  // Load email providers on component mount
  useEffect(() => {
    if (user && !authLoading) {
      loadEmailProviders();
      loadResendConfig();
    }
  }, [user, authLoading]);

  // Load timezone data on component mount
  useEffect(() => {
    if (user) {
      loadAvailableTimezones();
      loadCurrentTimezone();
    }
  }, [user]);

  const loadEmailProviders = async () => {
    try {
      setLoadingProviders(true);
      const response = await fetch(API_URL + "/api/settings/email-providers", {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
      });

      if (response.ok) {
        const providers = await response.json();

        // Filter out Mock and SES providers
        const filteredProviders = providers.filter(
          (provider: any) =>
            provider.id !== "mock" &&
            provider.id !== "aws-ses" &&
            provider.name !== "Mock" &&
            provider.name !== "AWS SES"
        );

        // Ensure each provider has a config object with default values
        const providersWithDefaults = filteredProviders.map(
          (provider: any) => ({
            ...provider,
            config: provider.config || {
              apiKey: "",
              fromEmail: "",
              fromName: "",
            },
          })
        );

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

  const switchEmailProvider = async (
    providerId: string,
    config: EmailProvider["config"]
  ) => {
    try {
      const response = await fetch(
        API_URL + "/api/settings/email-providers/" + providerId + "/switch",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify(config),
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
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

  // Resend configuration functions
  const loadResendConfig = async () => {
    try {
      setLoadingResendConfig(true);
      const response = await fetch(API_URL + "/api/user/resend/config", {
        credentials: "include",
      });

      if (response.ok) {
        const config = await response.json();
        setResendConfig({
          apiKey: "", // Don't load API key for security
          fromEmail: config.fromEmail || "",
          fromName: config.fromName || "",
          domain: config.domain || "",
        });
        setHasResendConfig(true);
        setIsDomainVerified(config.isDomainVerified || false);
      } else if (response.status === 404) {
        setHasResendConfig(false);
        setIsDomainVerified(false);
      }
    } catch (error) {
      console.error("Failed to load Resend config:", error);
    } finally {
      setLoadingResendConfig(false);
    }
  };

  const saveResendConfig = async () => {
    try {
      // Validate inputs
      if (!resendConfig.apiKey || !resendConfig.fromEmail) {
        toast({
          title: "Validation Error",
          description: "API Key and From Email are required",
          variant: "destructive",
        });
        return;
      }

      const response = await fetch(API_URL + "/api/user/resend/config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(resendConfig),
      });

      if (response.ok) {
        setVerificationSuccess("Saved successfully");
        setHasResendConfig(true);
        setResendConfigModified(false);
        // Reload config
        await loadResendConfig();
      } else {
        const error = await response.text();
        toast({
          title: "Error",
          description: `Failed to save Resend configuration: ${error}`,
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to save Resend configuration",
        variant: "destructive",
      });
    }
  };

  const deleteResendConfig = async () => {
    try {
      setIsLoading(true);

      const response = await fetch(API_URL + "/api/user/resend/config", {
        method: "DELETE",
        credentials: "include",
      });

      if (response.ok) {
        setVerificationSuccess("Configuration deleted successfully");
        setResendConfig({
          apiKey: "",
          fromEmail: "",
          fromName: "",
          domain: "",
        });
        setHasResendConfig(false);
        setIsDomainVerified(false);
        setIsVerificationSent(false);
        setVerificationCode("");
      } else {
        toast({
          title: "Error",
          description: "Failed to delete Resend configuration",
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to delete Resend configuration",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const sendDomainVerification = async () => {
    try {
      setIsVerifyingDomain(true);
      setVerificationError(""); // Clear previous errors
      setVerificationSuccess(""); // Clear previous success messages

      if (!resendConfig.apiKey || !resendConfig.fromEmail) {
        setVerificationError(
          "API Key and From Email are required to send verification"
        );
        return;
      }

      const response = await fetch(API_URL + "/api/user/resend/verify-domain", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(resendConfig),
      });

      const result = await response.json();

      if (result.success) {
        toast({
          title: "Verification Email Sent",
          description: result.message,
        });
        setIsVerificationSent(true);
        setVerificationError(""); // Clear any previous errors
      } else {
        setVerificationError(result.message);
      }
    } catch (error) {
      console.error("Error sending verification:", error);
      setVerificationError(
        "Failed to send verification email. Please check your connection and try again."
      );
    } finally {
      setIsVerifyingDomain(false);
    }
  };

  const verifyDomainCode = async () => {
    try {
      setIsVerifyingCode(true);

      if (!verificationCode.trim()) {
        toast({
          title: "Validation Error",
          description: "Please enter the verification code",
          variant: "destructive",
        });
        return;
      }

      const response = await fetch(API_URL + "/api/user/resend/verify-code", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ verificationCode: verificationCode.trim() }),
      });

      const result = await response.json();

      if (result.success) {
        setVerificationSuccess(result.message);
        setIsDomainVerified(true);
        setIsVerificationSent(false);
        setVerificationCode("");
        setVerificationError(""); // Clear any previous errors
      } else {
        toast({
          title: "Verification Failed",
          description: result.message,
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to verify code",
        variant: "destructive",
      });
    } finally {
      setIsVerifyingCode(false);
    }
  };

  if (authLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  // Timezone functions
  const loadAvailableTimezones = () => {
    // Standard UTC offsets
    const timezones = [
      "UTC−12",
      "UTC−11",
      "UTC−10",
      "UTC−9",
      "UTC−8",
      "UTC−7",
      "UTC−6",
      "UTC−5",
      "UTC−4",
      "UTC−3",
      "UTC−2",
      "UTC−1",
      "UTC±0",
      "UTC+1",
      "UTC+2",
      "UTC+3",
      "UTC+4",
      "UTC+5",
      "UTC+6",
      "UTC+7",
      "UTC+8",
      "UTC+9",
      "UTC+10",
      "UTC+11",
      "UTC+12",
      "UTC+13",
      "UTC+14",
    ];
    setAvailableTimezones(timezones);
  };

  const loadCurrentTimezone = async () => {
    try {
      const response = await fetch(`${API_URL}/api/settings/timezone`, {
        credentials: "include",
      });

      if (response.ok) {
        const data = await response.json();
        setSelectedTimezone(data.timezone);
        setTimezoneOffset(data.timezoneOffset);
      }
    } catch (error) {
      console.error("Failed to load current timezone:", error);
    }
  };

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

      if (response.ok) {
        const data = await response.json();
        setTimezoneOffset(data.timezoneOffset);
        setTimezoneSuccess(true);
        toast({
          title: "Timezone Updated",
          description: `Timezone updated to ${selectedTimezone}`,
        });

        // Clear success message after 3 seconds
        setTimeout(() => setTimezoneSuccess(false), 3000);
      } else {
        const error = await response.json();
        setTimezoneError(error.error || "Failed to update timezone");
      }
    } catch (error) {
      console.error("Failed to update timezone:", error);
      setTimezoneError("Failed to update timezone");
    } finally {
      setIsUpdatingTimezone(false);
    }
  };

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
              <div className="flex items-center gap-3 mb-2">
                <Settings className="h-8 w-8 text-blue-600" />
                <h1 className="text-3xl font-bold text-gray-900">Settings</h1>
              </div>
              <p className="text-gray-600">
                Manage your account preferences and email configuration
              </p>
            </div>

            <Tabs defaultValue="email" className="space-y-6">
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="email" className="flex items-center gap-2">
                  <Mail className="h-4 w-4" />
                  Email
                </TabsTrigger>
                <TabsTrigger
                  value="timezone"
                  className="flex items-center gap-2"
                >
                  <Clock className="h-4 w-4" />
                  Timezone
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
                              <Switch
                                checked={provider.isActive}
                                onCheckedChange={(checked) => {
                                  if (checked) {
                                    // Enable this provider and disable all others
                                    setEmailProviders((prev) => {
                                      const updated = prev.map((p) => ({
                                        ...p,
                                        isActive: p.id === provider.id,
                                      }));
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
                                  }
                                }}
                              />
                            </div>
                          </div>

                          {provider.isActive && (
                            <div className="space-y-4">
                              {provider.id === "resend" ? (
                                // Enhanced Resend Configuration
                                <>
                                  {hasResendConfig && (
                                    <Alert className="border-green-200 bg-green-50">
                                      <CheckCircle className="h-4 w-4 text-green-600" />
                                      <AlertDescription className="text-green-800">
                                        <div className="font-medium mb-1">
                                          Resend account connected
                                        </div>
                                        <p className="text-sm">
                                          You're using your own Resend account
                                          to send emails
                                        </p>
                                      </AlertDescription>
                                    </Alert>
                                  )}

                                  <div className="space-y-2">
                                    <Label htmlFor="resend-api-key">
                                      API Key{" "}
                                      <span className="text-red-500">*</span>
                                    </Label>
                                    <div className="flex gap-2">
                                      <Input
                                        id="resend-api-key"
                                        type={showApiKey ? "text" : "password"}
                                        value={resendConfig.apiKey}
                                        onChange={(e) => {
                                          setResendConfig((prev) => ({
                                            ...prev,
                                            apiKey: e.target.value,
                                          }));
                                          setResendConfigModified(true);
                                          setVerificationError(""); // Clear error when user types
                                          setVerificationSuccess(""); // Clear success message when user types
                                        }}
                                        placeholder="re_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
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
                                    <p className="text-xs text-gray-500">
                                      Get your API key from{" "}
                                      <a
                                        href="https://resend.com/api-keys"
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-blue-600 hover:underline"
                                      >
                                        Resend Dashboard
                                      </a>
                                    </p>
                                  </div>

                                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                      <Label htmlFor="resend-from-email">
                                        From Email{" "}
                                        <span className="text-red-500">*</span>
                                      </Label>
                                      <Input
                                        id="resend-from-email"
                                        type="email"
                                        value={resendConfig.fromEmail}
                                        onChange={(e) => {
                                          setResendConfig((prev) => ({
                                            ...prev,
                                            fromEmail: e.target.value,
                                          }));
                                          setResendConfigModified(true);
                                          setVerificationError(""); // Clear error when user types
                                          setVerificationSuccess(""); // Clear success message when user types
                                        }}
                                        placeholder="noreply@yourdomain.com"
                                      />
                                    </div>

                                    <div className="space-y-2">
                                      <Label htmlFor="resend-from-name">
                                        From Name
                                      </Label>
                                      <Input
                                        id="resend-from-name"
                                        value={resendConfig.fromName}
                                        onChange={(e) => {
                                          setResendConfig((prev) => ({
                                            ...prev,
                                            fromName: e.target.value,
                                          }));
                                          setResendConfigModified(true);
                                          setVerificationError(""); // Clear error when user types
                                          setVerificationSuccess(""); // Clear success message when user types
                                        }}
                                        placeholder="Your Company"
                                      />
                                    </div>
                                  </div>

                                  <div className="space-y-2">
                                    <Label htmlFor="resend-domain">
                                      Domain
                                    </Label>
                                    <Input
                                      id="resend-domain"
                                      value={resendConfig.domain}
                                      onChange={(e) => {
                                        setResendConfig((prev) => ({
                                          ...prev,
                                          domain: e.target.value,
                                        }));
                                        setResendConfigModified(true);
                                        setVerificationError(""); // Clear error when user types
                                      }}
                                      placeholder="yourdomain.com"
                                    />
                                    <p className="text-xs text-gray-500">
                                      Make sure to verify your domain in Resend
                                      first
                                    </p>
                                  </div>

                                  {/* Domain Verification Section */}
                                  {!isDomainVerified && (
                                    <div className="space-y-4 pt-4">
                                      {!isVerificationSent ? (
                                        <div className="space-y-3">
                                          <Button
                                            onClick={sendDomainVerification}
                                            disabled={
                                              isVerifyingDomain ||
                                              !resendConfig.apiKey ||
                                              !resendConfig.fromEmail
                                            }
                                            className="w-full"
                                          >
                                            {isVerifyingDomain ? (
                                              <>
                                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                                Sending Verification...
                                              </>
                                            ) : (
                                              "Validate Domain"
                                            )}
                                          </Button>

                                          {/* Error Message Display */}
                                          {verificationError && (
                                            <div className="mt-3 p-3 bg-red-50 border border-red-200 rounded-md">
                                              <div className="flex items-center">
                                                <AlertCircle className="h-4 w-4 text-red-600 mr-2" />
                                                <p className="text-sm text-red-800 font-medium">
                                                  {verificationError}
                                                </p>
                                              </div>
                                            </div>
                                          )}

                                          <Alert className="border-yellow-200 bg-yellow-50">
                                            <AlertCircle className="h-4 w-4 text-yellow-600" />
                                            <AlertDescription className="text-yellow-800">
                                              <div className="font-medium mb-1">
                                                Domain Verification Required
                                              </div>
                                              <p className="text-sm">
                                                Before saving your
                                                configuration, we need to verify
                                                that you own the domain. Click
                                                "Validate Domain" to receive a
                                                verification code at your login
                                                email.
                                              </p>
                                            </AlertDescription>
                                          </Alert>
                                        </div>
                                      ) : (
                                        <div className="space-y-3">
                                          <div className="space-y-2">
                                            <Label htmlFor="verification-code">
                                              Verification Code
                                            </Label>
                                            <div className="flex gap-2">
                                              <Input
                                                id="verification-code"
                                                value={verificationCode}
                                                onChange={(e) =>
                                                  setVerificationCode(
                                                    e.target.value
                                                  )
                                                }
                                                placeholder="Enter 6-digit code"
                                                maxLength={6}
                                                className="text-center text-lg tracking-widest"
                                              />
                                              <Button
                                                onClick={verifyDomainCode}
                                                disabled={
                                                  isVerifyingCode ||
                                                  !verificationCode.trim()
                                                }
                                              >
                                                {isVerifyingCode ? (
                                                  <>
                                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                                    Verifying...
                                                  </>
                                                ) : (
                                                  "Verify"
                                                )}
                                              </Button>
                                            </div>
                                          </div>

                                          <Alert className="border-blue-200 bg-blue-50">
                                            <AlertCircle className="h-4 w-4 text-blue-600" />
                                            <AlertDescription className="text-blue-800">
                                              <div className="font-medium mb-1">
                                                Check Your Email
                                              </div>
                                              <p className="text-sm">
                                                We've sent a 6-digit
                                                verification code to your login
                                                email address. Please check your
                                                inbox and spam folder. If you
                                                don't see the email, verify that
                                                your API key and email address
                                                are correct.
                                              </p>
                                            </AlertDescription>
                                          </Alert>

                                          <Button
                                            variant="outline"
                                            onClick={() => {
                                              setIsVerificationSent(false);
                                              setVerificationCode("");
                                            }}
                                            className="w-full"
                                          >
                                            Resend Verification Code
                                          </Button>
                                        </div>
                                      )}
                                    </div>
                                  )}

                                  {/* Success Message Display */}
                                  {verificationSuccess && (
                                    <div className="mb-4 p-3 bg-green-50 border border-green-200 rounded-md">
                                      <div className="flex items-center">
                                        <CheckCircle className="h-4 w-4 text-green-600 mr-2" />
                                        <p className="text-sm text-green-800 font-medium">
                                          {verificationSuccess}
                                        </p>
                                      </div>
                                    </div>
                                  )}

                                  {/* Action Buttons - Only show after domain verification */}
                                  {isDomainVerified && (
                                    <div className="flex gap-2 pt-4">
                                      <Button
                                        onClick={saveResendConfig}
                                        disabled={isLoading}
                                      >
                                        <Save className="mr-2 h-4 w-4" />
                                        Save Configuration
                                      </Button>

                                      {hasResendConfig && (
                                        <AlertDialog>
                                          <AlertDialogTrigger asChild>
                                            <Button
                                              variant="destructive"
                                              disabled={isLoading}
                                            >
                                              <Trash2 className="mr-2 h-4 w-4" />
                                              Delete
                                            </Button>
                                          </AlertDialogTrigger>
                                          <AlertDialogContent>
                                            <AlertDialogHeader>
                                              <AlertDialogTitle>
                                                Delete Resend Configuration
                                              </AlertDialogTitle>
                                              <AlertDialogDescription>
                                                Are you sure you want to delete
                                                your Resend configuration? You
                                                will no longer be able to send
                                                emails using your own Resend
                                                account.
                                              </AlertDialogDescription>
                                            </AlertDialogHeader>
                                            <AlertDialogFooter>
                                              <AlertDialogCancel>
                                                Cancel
                                              </AlertDialogCancel>
                                              <AlertDialogAction
                                                onClick={deleteResendConfig}
                                                className="bg-red-600 hover:bg-red-700"
                                              >
                                                Yes, Delete Configuration
                                              </AlertDialogAction>
                                            </AlertDialogFooter>
                                          </AlertDialogContent>
                                        </AlertDialog>
                                      )}
                                    </div>
                                  )}

                                  <Alert className="border-blue-200 bg-blue-50">
                                    <AlertCircle className="h-4 w-4 text-blue-600" />
                                    <AlertDescription className="text-blue-800">
                                      <div className="font-medium mb-2">
                                        How to set up your Resend account:
                                      </div>
                                      <ol className="text-sm space-y-1 list-decimal list-inside">
                                        <li>Create an account at resend.com</li>
                                        <li>
                                          Verify your domain with DNS records
                                        </li>
                                        <li>Generate an API key</li>
                                        <li>
                                          Enter your details above and click
                                          Save
                                        </li>
                                      </ol>
                                    </AlertDescription>
                                  </Alert>
                                </>
                              ) : (
                                // Default provider configuration for non-Resend providers
                                <>
                                  {provider.type === "api" && (
                                    <>
                                      <div className="space-y-2">
                                        <Label>API Key</Label>
                                        <div className="flex gap-2">
                                          <Input
                                            type={
                                              showApiKey ? "text" : "password"
                                            }
                                            value={
                                              provider.config?.apiKey || ""
                                            }
                                            onChange={(e) => {
                                              updateEmailProvider(provider.id, {
                                                config: {
                                                  ...provider.config,
                                                  apiKey: e.target.value,
                                                },
                                              });
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
                                        }}
                                        placeholder="Your Company"
                                      />
                                    </div>
                                  </div>
                                </>
                              )}
                            </div>
                          )}
                        </div>
                      ))
                    )}
                  </CardContent>
                </Card>
              </TabsContent>

              {/* Timezone Settings */}
              <TabsContent value="timezone" className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Clock className="h-5 w-5" />
                      Timezone Settings
                    </CardTitle>
                    <CardDescription>
                      Set your timezone preference for displaying times
                      throughout the application. All times are stored in UTC
                      and converted for display.
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
                          Timezone updated successfully!
                        </AlertDescription>
                      </Alert>
                    )}
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
                            {user.createdAt
                              ? new Date(user.createdAt).toLocaleDateString()
                              : "Unknown"}
                            <span className="text-xs text-gray-400 ml-1">
                              (MM/DD/YY)
                            </span>
                          </p>
                        </div>
                      </div>
                    </div>
                  </CardContent>
                </Card>

                {/* Placeholder sections for future features */}
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
