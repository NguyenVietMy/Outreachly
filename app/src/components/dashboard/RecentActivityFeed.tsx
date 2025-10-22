"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
  RefreshCw,
  Target,
  Flag,
} from "lucide-react";
import { useActivityFeed } from "@/hooks/useActivityFeed";

interface ActivityItem {
  id: string;
  type:
    | "csv_import"
    | "campaign"
    | "domain"
    | "checkpoint"
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
  const {
    activities: activityFeedData,
    loading,
    error,
    refetch,
  } = useActivityFeed();

  // Convert activity feed data to activity items
  const convertActivityFeedToActivities = (feedData: any[]): ActivityItem[] => {
    return feedData.map((activity) => {
      let type: ActivityItem["type"] = activity.activityType;
      let metadata: any = { status: activity.status };

      // Add specific metadata based on activity type
      switch (activity.activityType) {
        case "csv_import":
          // Extract count from description if available
          const countMatch = activity.description.match(/(\d+)/);
          if (countMatch) {
            metadata.count = parseInt(countMatch[1]);
          }
          break;
        case "campaign":
          // Extract campaign name from title if available
          const campaignMatch = activity.title.match(/Campaign "([^"]+)"/);
          if (campaignMatch) {
            metadata.campaignName = campaignMatch[1];
          }
          break;
        case "checkpoint":
          // Extract checkpoint name from title if available
          const checkpointMatch = activity.title.match(/Checkpoint "([^"]+)"/);
          if (checkpointMatch) {
            metadata.campaignName = checkpointMatch[1];
          }
          break;
      }

      return {
        id: activity.id,
        type,
        title: activity.title,
        description: activity.description,
        timestamp: formatTimestamp(activity.createdAt),
        metadata,
      };
    });
  };

  // Format timestamp to relative time
  const formatTimestamp = (dateString: string): string => {
    try {
      const date = new Date(dateString);
      const now = new Date();
      const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

      if (diffInSeconds < 60) return "just now";
      if (diffInSeconds < 3600)
        return `${Math.floor(diffInSeconds / 60)} minutes ago`;
      if (diffInSeconds < 86400)
        return `${Math.floor(diffInSeconds / 3600)} hours ago`;
      if (diffInSeconds < 2592000)
        return `${Math.floor(diffInSeconds / 86400)} days ago`;
      if (diffInSeconds < 31536000)
        return `${Math.floor(diffInSeconds / 2592000)} months ago`;
      return `${Math.floor(diffInSeconds / 31536000)} years ago`;
    } catch {
      return "Unknown";
    }
  };

  // Use provided activities or convert activity feed data to activities
  const activityData =
    activities || convertActivityFeedToActivities(activityFeedData);

  const getActivityIcon = (type: string) => {
    switch (type) {
      case "csv_import":
        return <Upload className="h-4 w-4" />;
      case "campaign":
        return <Mail className="h-4 w-4" />;
      case "domain":
        return <Settings className="h-4 w-4" />;
      case "checkpoint":
        return <Flag className="h-4 w-4" />;
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
      case "csv_import":
        return "text-blue-600 bg-blue-100";
      case "campaign":
        return "text-green-600 bg-green-100";
      case "domain":
        return "text-purple-600 bg-purple-100";
      case "checkpoint":
        return "text-indigo-600 bg-indigo-100";
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
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg font-semibold">
            📬 Recent Activity Feed
          </CardTitle>
          <Button
            variant="outline"
            size="sm"
            onClick={refetch}
            disabled={loading}
            className="ml-2"
          >
            <RefreshCw
              className={`h-4 w-4 mr-2 ${loading ? "animate-spin" : ""}`}
            />
            Refresh
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {loading && (
          <div className="flex items-center justify-center py-8">
            <RefreshCw className="h-6 w-6 animate-spin mr-2" />
            <span>Loading recent activity...</span>
          </div>
        )}

        {error && (
          <div className="text-red-500 text-sm mb-4 p-3 bg-red-50 rounded-lg">
            {error}
          </div>
        )}

        {!loading && !error && (
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
        )}

        {!loading && !error && activityData.length === 0 && (
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
        {!loading && !error && activityData.length > 0 && (
          <div className="mt-6 pt-4 border-t border-gray-200">
            <button className="text-sm text-blue-600 hover:text-blue-800 font-medium">
              View all activity →
            </button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
