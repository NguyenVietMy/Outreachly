"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Play,
  Pause,
  CheckCircle,
  Clock,
  TrendingUp,
  AlertTriangle,
  ExternalLink,
} from "lucide-react";

interface Campaign {
  id: string;
  name: string;
  status: "running" | "paused" | "completed";
  sent: number;
  delivered: number;
  openRate: number;
  replyRate: number;
  bounceRate: number;
  lastActivity: string;
  createdAt: string;
}

interface CampaignSnapshotProps {
  campaigns?: Campaign[];
  onViewDetails?: (campaignId: string) => void;
}

export default function CampaignSnapshot({
  campaigns,
  onViewDetails,
}: CampaignSnapshotProps) {
  // Mock data for demonstration
  const mockCampaigns: Campaign[] = [
    {
      id: "1",
      name: "Q4 Enterprise Outreach",
      status: "running",
      sent: 1250,
      delivered: 1234,
      openRate: 28.5,
      replyRate: 4.2,
      bounceRate: 0.8,
      lastActivity: "2 hours ago",
      createdAt: "2024-01-15",
    },
    {
      id: "2",
      name: "Product Demo Follow-up",
      status: "paused",
      sent: 850,
      delivered: 842,
      openRate: 22.1,
      replyRate: 3.8,
      bounceRate: 0.9,
      lastActivity: "1 day ago",
      createdAt: "2024-01-10",
    },
    {
      id: "3",
      name: "Holiday Campaign",
      status: "completed",
      sent: 2100,
      delivered: 2085,
      openRate: 31.2,
      replyRate: 5.1,
      bounceRate: 0.7,
      lastActivity: "3 days ago",
      createdAt: "2024-01-05",
    },
    {
      id: "4",
      name: "Beta Launch Announcement",
      status: "running",
      sent: 750,
      delivered: 745,
      openRate: 26.8,
      replyRate: 3.5,
      bounceRate: 0.6,
      lastActivity: "30 minutes ago",
      createdAt: "2024-01-20",
    },
    {
      id: "5",
      name: "Customer Onboarding",
      status: "paused",
      sent: 420,
      delivered: 418,
      openRate: 19.5,
      replyRate: 2.9,
      bounceRate: 0.5,
      lastActivity: "2 days ago",
      createdAt: "2024-01-12",
    },
  ];

  const campaignData = campaigns || mockCampaigns.slice(0, 5);

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "running":
        return <Play className="h-4 w-4 text-green-600" />;
      case "paused":
        return <Pause className="h-4 w-4 text-yellow-600" />;
      case "completed":
        return <CheckCircle className="h-4 w-4 text-blue-600" />;
      default:
        return <Clock className="h-4 w-4 text-gray-600" />;
    }
  };

  const getStatusBadge = (status: string) => {
    const variants = {
      running: {
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
    };

    const config =
      variants[status as keyof typeof variants] || variants.completed;

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
          {campaignData.map((campaign) => {
            const bounceStatus = getBounceStatus(campaign.bounceRate);
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
                          {campaign.sent.toLocaleString()}
                        </span>
                        <span className="text-gray-500"> sent</span>
                      </div>
                      <div>
                        <span className="font-medium">
                          {campaign.openRate}%
                        </span>
                        <span className="text-gray-500"> opens</span>
                      </div>
                      <div>
                        <span className="font-medium">
                          {campaign.replyRate}%
                        </span>
                        <span className="text-gray-500"> replies</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        <BounceIcon
                          className={`h-3 w-3 ${bounceStatus.color}`}
                        />
                        <span className={`font-medium ${bounceStatus.color}`}>
                          {campaign.bounceRate}%
                        </span>
                        <span className="text-gray-500"> bounces</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="flex-shrink-0 text-right">
                  <p className="text-xs text-gray-500 mb-1">Last activity</p>
                  <p className="text-sm font-medium text-gray-900">
                    {campaign.lastActivity}
                  </p>
                </div>
              </div>
            );
          })}
        </div>

        {campaignData.length === 0 && (
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

