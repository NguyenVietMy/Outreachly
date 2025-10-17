"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Play,
  Pause,
  CheckCircle,
  Clock,
  TrendingUp,
  AlertTriangle,
  ExternalLink,
} from "lucide-react";
import { useCampaignStats, CampaignWithStats } from "@/hooks/useCampaignStats";

interface CampaignSnapshotProps {
  onViewDetails?: (campaignId: string) => void;
}

export default function CampaignSnapshot({
  onViewDetails,
}: CampaignSnapshotProps) {
  const { campaignsWithStats, loading, error } = useCampaignStats();

  // Helper function to calculate metrics
  const calculateMetrics = (campaign: CampaignWithStats) => {
    const stats = campaign.stats;
    if (!stats) {
      return {
        sent: 0,
        delivered: 0,
        openRate: 0,
        replyRate: 0,
        bounceRate: 0,
      };
    }

    const sent = stats.emailsSent;
    const delivered = stats.emailsDelivered;
    const failed = stats.emailsFailed;

    // Calculate rates (using mock data for now since we don't have open/reply tracking yet)
    const openRate =
      sent > 0 ? Math.round(((delivered * 0.25) / sent) * 100 * 10) / 10 : 0; // Mock 25% open rate
    const replyRate =
      sent > 0 ? Math.round(((delivered * 0.04) / sent) * 100 * 10) / 10 : 0; // Mock 4% reply rate
    const bounceRate =
      sent > 0 ? Math.round((failed / sent) * 100 * 10) / 10 : 0;

    return {
      sent,
      delivered,
      openRate,
      replyRate,
      bounceRate,
    };
  };

  // Helper function to get last activity
  const getLastActivity = (campaign: CampaignWithStats) => {
    const updatedAt = new Date(campaign.updatedAt);
    const now = new Date();
    const diffInHours = Math.floor(
      (now.getTime() - updatedAt.getTime()) / (1000 * 60 * 60)
    );

    if (diffInHours < 1) return "Just now";
    if (diffInHours < 24) return `${diffInHours} hours ago`;
    const diffInDays = Math.floor(diffInHours / 24);
    return `${diffInDays} days ago`;
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "active":
        return <Play className="h-4 w-4 text-green-600" />;
      case "paused":
        return <Pause className="h-4 w-4 text-yellow-600" />;
      case "completed":
        return <CheckCircle className="h-4 w-4 text-blue-600" />;
      case "inactive":
        return <Clock className="h-4 w-4 text-gray-600" />;
      default:
        return <Clock className="h-4 w-4 text-gray-600" />;
    }
  };

  const getStatusBadge = (status: string) => {
    const variants = {
      active: {
        variant: "default" as const,
        className: "bg-green-100 text-green-800",
      },
      paused: {
        variant: "secondary" as const,
        className: "bg-yellow-100 text-yellow-800",
      },
      completed: {
        variant: "outline" as const,
        className: "bg-blue-100 text-blue-800",
      },
      inactive: {
        variant: "secondary" as const,
        className: "bg-gray-100 text-gray-800",
      },
    };

    const config =
      variants[status as keyof typeof variants] || variants.inactive;

    return (
      <Badge variant={config.variant} className={config.className}>
        {status.charAt(0).toUpperCase() + status.slice(1)}
      </Badge>
    );
  };

  const getBounceStatus = (bounceRate: number) => {
    if (bounceRate > 1) {
      return { color: "text-red-600", icon: AlertTriangle };
    }
    return { color: "text-green-600", icon: TrendingUp };
  };

  if (loading) {
    return (
      <Card className="mb-8">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-lg font-semibold">
              📊 Campaign Snapshot
            </CardTitle>
            <Button variant="outline" size="sm" disabled>
              View All Campaigns
              <ExternalLink className="h-4 w-4 ml-1" />
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {[...Array(3)].map((_, i) => (
              <div
                key={i}
                className="flex items-center justify-between p-4 border border-gray-200 rounded-lg"
              >
                <div className="flex items-center space-x-4 flex-1 min-w-0">
                  <Skeleton className="h-4 w-4" />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center space-x-2 mb-1">
                      <Skeleton className="h-5 w-32" />
                      <Skeleton className="h-5 w-16" />
                    </div>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                      <Skeleton className="h-4 w-20" />
                      <Skeleton className="h-4 w-16" />
                      <Skeleton className="h-4 w-16" />
                      <Skeleton className="h-4 w-16" />
                    </div>
                  </div>
                </div>
                <div className="flex-shrink-0 text-right">
                  <Skeleton className="h-3 w-16 mb-1" />
                  <Skeleton className="h-4 w-20" />
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="mb-8">
        <CardHeader>
          <CardTitle className="text-lg font-semibold">
            📊 Campaign Snapshot
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-center py-8">
            <div className="text-red-400 mb-2">
              <AlertTriangle className="h-12 w-12 mx-auto" />
            </div>
            <h3 className="font-medium text-gray-900 mb-1">
              Error loading campaigns
            </h3>
            <p className="text-sm text-gray-500 mb-4">{error}</p>
            <Button onClick={() => window.location.reload()}>Retry</Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="mb-8">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg font-semibold">
            📊 Campaign Snapshot
          </CardTitle>
          <Button variant="outline" size="sm">
            View All Campaigns
            <ExternalLink className="h-4 w-4 ml-1" />
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {campaignsWithStats.slice(0, 5).map((campaign) => {
            const metrics = calculateMetrics(campaign);
            const bounceStatus = getBounceStatus(metrics.bounceRate);
            const BounceIcon = bounceStatus.icon;

            return (
              <div
                key={campaign.id}
                className="flex items-center justify-between p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer"
                onClick={() => onViewDetails?.(campaign.id)}
              >
                <div className="flex items-center space-x-4 flex-1 min-w-0">
                  <div className="flex-shrink-0">
                    {getStatusIcon(campaign.status)}
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center space-x-2 mb-1">
                      <h3 className="font-medium text-gray-900 truncate">
                        {campaign.name}
                      </h3>
                      {getStatusBadge(campaign.status)}
                    </div>

                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm text-gray-600">
                      <div>
                        <span className="font-medium">
                          {metrics.sent.toLocaleString()}
                        </span>
                        <span className="text-gray-500"> sent</span>
                      </div>
                      <div>
                        <span className="font-medium">{metrics.openRate}%</span>
                        <span className="text-gray-500"> opens</span>
                      </div>
                      <div>
                        <span className="font-medium">
                          {metrics.replyRate}%
                        </span>
                        <span className="text-gray-500"> replies</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        <BounceIcon
                          className={`h-3 w-3 ${bounceStatus.color}`}
                        />
                        <span className={`font-medium ${bounceStatus.color}`}>
                          {metrics.bounceRate}%
                        </span>
                        <span className="text-gray-500"> bounces</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="flex-shrink-0 text-right">
                  <p className="text-xs text-gray-500 mb-1">Last activity</p>
                  <p className="text-sm font-medium text-gray-900">
                    {getLastActivity(campaign)}
                  </p>
                </div>
              </div>
            );
          })}
        </div>

        {campaignsWithStats.length === 0 && (
          <div className="text-center py-8">
            <div className="text-gray-400 mb-2">
              <Play className="h-12 w-12 mx-auto" />
            </div>
            <h3 className="font-medium text-gray-900 mb-1">No campaigns yet</h3>
            <p className="text-sm text-gray-500 mb-4">
              Create your first campaign to start reaching out to leads
            </p>
            <Button>Create Campaign</Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
