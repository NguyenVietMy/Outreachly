"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Upload,
  Mail,
  AlertTriangle,
  MessageSquare,
  Users,
  Settings,
  CheckCircle,
  Clock,
  TrendingUp,
  TrendingDown,
} from "lucide-react";

interface ActivityItem {
  id: string;
  type:
    | "import"
    | "campaign"
    | "bounce"
    | "reply"
    | "verification"
    | "configuration"
    | "warning";
  title: string;
  description: string;
  timestamp: string;
  metadata?: {
    count?: number;
    rate?: number;
    status?: string;
    campaignName?: string;
    leadName?: string;
    companyName?: string;
  };
}

interface RecentActivityFeedProps {
  activities?: ActivityItem[];
  onViewDetails?: (activityId: string) => void;
}

export default function RecentActivityFeed({
  activities,
  onViewDetails,
}: RecentActivityFeedProps) {
  // Mock data for demonstration
  const mockActivities: ActivityItem[] = [
    {
      id: "1",
      type: "import",
      title: "CSV import completed",
      description:
        "Successfully processed 9,532 leads from Enterprise Prospects Q4.csv",
      timestamp: "2 minutes ago",
      metadata: { count: 9532 },
    },
    {
      id: "2",
      type: "campaign",
      title: "Campaign sent",
      description: "Q4 Enterprise Outreach campaign delivered 1,200 emails",
      timestamp: "15 minutes ago",
      metadata: { count: 1200, campaignName: "Q4 Enterprise Outreach" },
    },
    {
      id: "3",
      type: "bounce",
      title: "Bounce rate alert",
      description:
        "Bounce rate hit 1.2%, risky addresses automatically suppressed",
      timestamp: "1 hour ago",
      metadata: { rate: 1.2, status: "warning" },
    },
    {
      id: "4",
      type: "reply",
      title: "New reply received",
      description: "Sarah Johnson from ACME Inc. replied to your outreach",
      timestamp: "2 hours ago",
      metadata: { leadName: "Sarah Johnson", companyName: "ACME Inc." },
    },
    {
      id: "5",
      type: "verification",
      title: "Lead verification completed",
      description:
        "Verified 2,380 email addresses from Enterprise Prospects Q4 list",
      timestamp: "3 hours ago",
      metadata: { count: 2380 },
    },
    {
      id: "6",
      type: "configuration",
      title: "Domain configured",
      description:
        "Successfully set up SPF, DKIM, and DMARC records for outreach-ly.com",
      timestamp: "5 hours ago",
      metadata: { status: "success" },
    },
    {
      id: "7",
      type: "campaign",
      title: "Campaign paused",
      description:
        "Product Demo Follow-up campaign paused due to high bounce rate",
      timestamp: "1 day ago",
      metadata: { campaignName: "Product Demo Follow-up", status: "paused" },
    },
    {
      id: "8",
      type: "import",
      title: "CSV import failed",
      description:
        "Failed to import Startup Founders.csv - duplicate email addresses detected",
      timestamp: "2 days ago",
      metadata: { status: "error" },
    },
  ];

  const activityData = activities || mockActivities;

  const getActivityIcon = (type: string) => {
    switch (type) {
      case "import":
        return <Upload className="h-4 w-4" />;
      case "campaign":
        return <Mail className="h-4 w-4" />;
      case "bounce":
        return <AlertTriangle className="h-4 w-4" />;
      case "reply":
        return <MessageSquare className="h-4 w-4" />;
      case "verification":
        return <Users className="h-4 w-4" />;
      case "configuration":
        return <Settings className="h-4 w-4" />;
      case "warning":
        return <AlertTriangle className="h-4 w-4" />;
      default:
        return <Clock className="h-4 w-4" />;
    }
  };

  const getActivityColor = (type: string) => {
    switch (type) {
      case "import":
        return "text-blue-600 bg-blue-100";
      case "campaign":
        return "text-green-600 bg-green-100";
      case "bounce":
        return "text-red-600 bg-red-100";
      case "reply":
        return "text-purple-600 bg-purple-100";
      case "verification":
        return "text-orange-600 bg-orange-100";
      case "configuration":
        return "text-gray-600 bg-gray-100";
      case "warning":
        return "text-yellow-600 bg-yellow-100";
      default:
        return "text-gray-600 bg-gray-100";
    }
  };

  const getStatusBadge = (metadata?: any) => {
    if (!metadata?.status) return null;

    const variants = {
      success: {
        variant: "default" as const,
        className: "bg-green-100 text-green-800",
      },
      warning: {
        variant: "secondary" as const,
        className: "bg-yellow-100 text-yellow-800",
      },
      error: {
        variant: "destructive" as const,
        className: "bg-red-100 text-red-800",
      },
      paused: {
        variant: "outline" as const,
        className: "bg-gray-100 text-gray-800",
      },
    };

    const config =
      variants[metadata.status as keyof typeof variants] || variants.success;

    return (
      <Badge variant={config.variant} className={config.className}>
        {metadata.status.charAt(0).toUpperCase() + metadata.status.slice(1)}
      </Badge>
    );
  };

  const getTrendIcon = (type: string, metadata?: any) => {
    if (type === "bounce" && metadata?.rate && metadata.rate > 1) {
      return <TrendingUp className="h-3 w-3 text-red-600" />;
    }
    if (type === "reply") {
      return <TrendingUp className="h-3 w-3 text-green-600" />;
    }
    return null;
  };

  return (
    <Card className="mb-8">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">
          📬 Recent Activity Feed
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {activityData.map((activity, index) => {
            const isLast = index === activityData.length - 1;

            return (
              <div key={activity.id} className="relative">
                {/* Timeline line */}
                {!isLast && (
                  <div className="absolute left-4 top-8 w-px h-8 bg-gray-200"></div>
                )}

                <div
                  className="flex items-start space-x-3 cursor-pointer hover:bg-gray-50 p-2 rounded-lg transition-colors"
                  onClick={() => onViewDetails?.(activity.id)}
                >
                  {/* Icon */}
                  <div
                    className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${getActivityColor(activity.type)}`}
                  >
                    {getActivityIcon(activity.type)}
                  </div>

                  {/* Content */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-1">
                      <h3 className="text-sm font-medium text-gray-900">
                        {activity.title}
                      </h3>
                      <div className="flex items-center space-x-2">
                        {getStatusBadge(activity.metadata)}
                        <span className="text-xs text-gray-500">
                          {activity.timestamp}
                        </span>
                      </div>
                    </div>

                    <p className="text-sm text-gray-600 mb-2">
                      {activity.description}
                    </p>

                    {/* Metadata */}
                    {activity.metadata && (
                      <div className="flex items-center space-x-4 text-xs text-gray-500">
                        {activity.metadata.count && (
                          <span className="flex items-center space-x-1">
                            <span className="font-medium">
                              {activity.metadata.count.toLocaleString()}
                            </span>
                            <span>items</span>
                          </span>
                        )}
                        {activity.metadata.rate && (
                          <span className="flex items-center space-x-1">
                            {getTrendIcon(activity.type, activity.metadata)}
                            <span className="font-medium">
                              {activity.metadata.rate}%
                            </span>
                            <span>rate</span>
                          </span>
                        )}
                        {activity.metadata.campaignName && (
                          <span className="font-medium text-blue-600">
                            {activity.metadata.campaignName}
                          </span>
                        )}
                        {activity.metadata.leadName &&
                          activity.metadata.companyName && (
                            <span>
                              {activity.metadata.leadName} (
                              {activity.metadata.companyName})
                            </span>
                          )}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {activityData.length === 0 && (
          <div className="text-center py-8">
            <div className="text-gray-400 mb-2">
              <Clock className="h-12 w-12 mx-auto" />
            </div>
            <h3 className="font-medium text-gray-900 mb-1">
              No recent activity
            </h3>
            <p className="text-sm text-gray-500">
              Activity will appear here as you use Outreachly
            </p>
          </div>
        )}

        {/* View All Activities */}
        <div className="mt-6 pt-4 border-t border-gray-200">
          <button className="text-sm text-blue-600 hover:text-blue-800 font-medium">
            View all activity →
          </button>
        </div>
      </CardContent>
    </Card>
  );
}

