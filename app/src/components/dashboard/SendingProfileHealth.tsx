"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import {
  Shield,
  CheckCircle,
  XCircle,
  AlertTriangle,
  TrendingUp,
  Settings,
  ExternalLink,
  Info,
} from "lucide-react";

interface DNSRecord {
  type: "SPF" | "DKIM" | "DMARC";
  status: "valid" | "invalid" | "missing";
  value?: string;
  lastChecked: string;
}

interface WarmupProgress {
  currentVolume: number;
  targetVolume: number;
  dailyCap: number;
  hourlyCap: number;
  phase: "ramping" | "maintaining" | "complete";
}

interface SendingProfile {
  id: string;
  domain: string;
  dnsRecords: DNSRecord[];
  warmupProgress: WarmupProgress;
  dailyUsage: number;
  hourlyUsage: number;
  reputation: "excellent" | "good" | "fair" | "poor";
  lastActivity: string;
}

interface SendingProfileHealthProps {
  profiles?: SendingProfile[];
  onConfigureDomain?: (profileId: string) => void;
  onViewDetails?: (profileId: string) => void;
}

export default function SendingProfileHealth({
  profiles,
  onConfigureDomain,
  onViewDetails,
}: SendingProfileHealthProps) {
  // Mock data for demonstration
  const mockProfiles: SendingProfile[] = [
    {
      id: "1",
      domain: "outreach-ly.com",
      dnsRecords: [
        {
          type: "SPF",
          status: "valid",
          value: "v=spf1 include:_spf.outreach-ly.com ~all",
          lastChecked: "2 minutes ago",
        },
        {
          type: "DKIM",
          status: "valid",
          value: "k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC...",
          lastChecked: "2 minutes ago",
        },
        {
          type: "DMARC",
          status: "valid",
          value: "v=DMARC1; p=quarantine; rua=mailto:dmarc@outreach-ly.com",
          lastChecked: "2 minutes ago",
        },
      ],
      warmupProgress: {
        currentVolume: 125,
        targetVolume: 500,
        dailyCap: 200,
        hourlyCap: 25,
        phase: "ramping",
      },
      dailyUsage: 125,
      hourlyUsage: 8,
      reputation: "excellent",
      lastActivity: "30 minutes ago",
    },
    {
      id: "2",
      domain: "newsletter.outreach-ly.com",
      dnsRecords: [
        {
          type: "SPF",
          status: "valid",
          value: "v=spf1 include:_spf.outreach-ly.com ~all",
          lastChecked: "1 hour ago",
        },
        {
          type: "DKIM",
          status: "invalid",
          value: "Invalid key format",
          lastChecked: "1 hour ago",
        },
        { type: "DMARC", status: "missing", lastChecked: "1 hour ago" },
      ],
      warmupProgress: {
        currentVolume: 50,
        targetVolume: 200,
        dailyCap: 100,
        hourlyCap: 15,
        phase: "ramping",
      },
      dailyUsage: 50,
      hourlyUsage: 5,
      reputation: "fair",
      lastActivity: "2 hours ago",
    },
  ];

  const sendingProfiles = profiles || mockProfiles;

  const getDNSStatusIcon = (status: string) => {
    switch (status) {
      case "valid":
        return <CheckCircle className="h-4 w-4 text-green-600" />;
      case "invalid":
        return <XCircle className="h-4 w-4 text-red-600" />;
      case "missing":
        return <AlertTriangle className="h-4 w-4 text-yellow-600" />;
      default:
        return <XCircle className="h-4 w-4 text-gray-400" />;
    }
  };

  const getReputationBadge = (reputation: string) => {
    const variants = {
      excellent: {
        variant: "default" as const,
        className: "bg-green-100 text-green-800",
      },
      good: {
        variant: "secondary" as const,
        className: "bg-blue-100 text-blue-800",
      },
      fair: {
        variant: "outline" as const,
        className: "bg-yellow-100 text-yellow-800",
      },
      poor: {
        variant: "destructive" as const,
        className: "bg-red-100 text-red-800",
      },
    };

    const config =
      variants[reputation as keyof typeof variants] || variants.fair;

    return (
      <Badge variant={config.variant} className={config.className}>
        {reputation.charAt(0).toUpperCase() + reputation.slice(1)}
      </Badge>
    );
  };

  const getWarmupPhaseColor = (phase: string) => {
    switch (phase) {
      case "ramping":
        return "text-blue-600";
      case "maintaining":
        return "text-green-600";
      case "complete":
        return "text-purple-600";
      default:
        return "text-gray-600";
    }
  };

  const calculateWarmupProgress = (current: number, target: number) => {
    return Math.min((current / target) * 100, 100);
  };

  const getUsageColor = (usage: number, cap: number) => {
    const percentage = (usage / cap) * 100;
    if (percentage >= 90) return "text-red-600";
    if (percentage >= 75) return "text-yellow-600";
    return "text-green-600";
  };

  return (
    <Card className="mb-8">
      <CardHeader>
        <CardTitle className="text-lg font-semibold flex items-center space-x-2">
          <Shield className="h-5 w-5" />
          <span>🔐 Sending Profile Health</span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          {sendingProfiles.map((profile) => {
            const warmupProgress = calculateWarmupProgress(
              profile.warmupProgress.currentVolume,
              profile.warmupProgress.targetVolume
            );
            const dailyUsagePercent =
              (profile.dailyUsage / profile.warmupProgress.dailyCap) * 100;
            const hourlyUsagePercent =
              (profile.hourlyUsage / profile.warmupProgress.hourlyCap) * 100;

            return (
              <div
                key={profile.id}
                className="border border-gray-200 rounded-lg p-4"
              >
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center space-x-3">
                    <h3 className="font-medium text-gray-900">
                      {profile.domain}
                    </h3>
                    {getReputationBadge(profile.reputation)}
                  </div>
                  <div className="flex space-x-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onConfigureDomain?.(profile.id)}
                    >
                      <Settings className="h-4 w-4 mr-1" />
                      Configure
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onViewDetails?.(profile.id)}
                    >
                      <ExternalLink className="h-4 w-4 mr-1" />
                      Details
                    </Button>
                  </div>
                </div>

                {/* DNS Status */}
                <div className="mb-4">
                  <h4 className="text-sm font-medium text-gray-700 mb-2">
                    DNS Configuration
                  </h4>
                  <div className="grid grid-cols-3 gap-4">
                    {profile.dnsRecords.map((record) => (
                      <div
                        key={record.type}
                        className="flex items-center space-x-2"
                      >
                        {getDNSStatusIcon(record.status)}
                        <span className="text-sm font-medium">
                          {record.type}
                        </span>
                        <span
                          className={`text-xs ${
                            record.status === "valid"
                              ? "text-green-600"
                              : record.status === "invalid"
                                ? "text-red-600"
                                : "text-yellow-600"
                          }`}
                        >
                          {record.status}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Warmup Progress */}
                <div className="mb-4">
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="text-sm font-medium text-gray-700">
                      Warmup Progress
                    </h4>
                    <span
                      className={`text-sm font-medium ${getWarmupPhaseColor(profile.warmupProgress.phase)}`}
                    >
                      {profile.warmupProgress.phase.charAt(0).toUpperCase() +
                        profile.warmupProgress.phase.slice(1)}
                    </span>
                  </div>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-gray-600">
                        {profile.warmupProgress.currentVolume} /{" "}
                        {profile.warmupProgress.targetVolume} emails
                      </span>
                      <span className="font-medium">
                        {warmupProgress.toFixed(1)}%
                      </span>
                    </div>
                    <Progress value={warmupProgress} className="h-2" />
                  </div>
                </div>

                {/* Usage Caps */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-sm text-gray-600">Daily Usage</span>
                      <span
                        className={`text-sm font-medium ${getUsageColor(profile.dailyUsage, profile.warmupProgress.dailyCap)}`}
                      >
                        {profile.dailyUsage} / {profile.warmupProgress.dailyCap}
                      </span>
                    </div>
                    <Progress value={dailyUsagePercent} className="h-2" />
                  </div>
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-sm text-gray-600">
                        Hourly Usage
                      </span>
                      <span
                        className={`text-sm font-medium ${getUsageColor(profile.hourlyUsage, profile.warmupProgress.hourlyCap)}`}
                      >
                        {profile.hourlyUsage} /{" "}
                        {profile.warmupProgress.hourlyCap}
                      </span>
                    </div>
                    <Progress value={hourlyUsagePercent} className="h-2" />
                  </div>
                </div>

                <div className="mt-3 text-xs text-gray-500">
                  Last activity: {profile.lastActivity}
                </div>
              </div>
            );
          })}
        </div>

        {sendingProfiles.length === 0 && (
          <div className="text-center py-8">
            <div className="text-gray-400 mb-2">
              <Shield className="h-12 w-12 mx-auto" />
            </div>
            <h3 className="font-medium text-gray-900 mb-1">
              No sending profiles configured
            </h3>
            <p className="text-sm text-gray-500 mb-4">
              Set up your domain to start sending emails with better
              deliverability
            </p>
            <Button onClick={() => onConfigureDomain?.("new")}>
              <Settings className="h-4 w-4 mr-2" />
              Configure Domain
            </Button>
          </div>
        )}

        {/* DNS Help */}
        <div className="mt-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <div className="flex items-start space-x-2">
            <Info className="h-5 w-5 text-blue-600 mt-0.5" />
            <div>
              <h4 className="text-sm font-medium text-blue-800 mb-1">
                Why is DNS configuration important?
              </h4>
              <p className="text-xs text-blue-600">
                SPF, DKIM, and DMARC records help email providers verify that
                your emails are legitimate, improving deliverability and
                reducing the chance of your emails being marked as spam.
              </p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

