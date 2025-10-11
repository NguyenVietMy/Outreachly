"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Users,
  Mail,
  TrendingUp,
  Shield,
  Zap,
  AlertTriangle,
  CheckCircle,
} from "lucide-react";
import { useDeliveryMetrics, KPIData } from "@/hooks/useDeliveryMetrics";

interface HeroKPIsProps {
  data?: KPIData;
}

export default function HeroKPIs({ data }: HeroKPIsProps) {
  const { kpiData: realKpiData, loading, error } = useDeliveryMetrics();

  // Use real data if available, otherwise fall back to provided data or mock data
  const kpiData = realKpiData ||
    data || {
      leadsImported: {
        thisWeek: 0,
        total: 0,
      },
      activeCampaigns: {
        running: 0,
        paused: 0,
      },
      engagementRate: {
        deliveryRate: 0,
        replyRate: 0,
      },
      deliverabilityHealth: {
        bounceRate: 0,
        complaintRate: 0,
      },
      quotaUsage: {
        emailsSentToday: 0,
        dailyCap: 2000,
        planUsagePercent: 0,
      },
    };

  const getDeliverabilityStatus = (
    bounceRate: number,
    complaintRate: number
  ) => {
    if (bounceRate > 1 || complaintRate > 0.1) {
      return {
        status: "warning",
        color: "text-yellow-600",
        icon: AlertTriangle,
      };
    }
    return { status: "good", color: "text-green-600", icon: CheckCircle };
  };

  const deliverabilityStatus = getDeliverabilityStatus(
    kpiData.deliverabilityHealth.bounceRate,
    kpiData.deliverabilityHealth.complaintRate
  );

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
      {/* Leads Imported */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-gray-600">
            Leads Imported
          </CardTitle>
          <Users className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold text-blue-600">
            {kpiData.leadsImported.thisWeek.toLocaleString()}
          </div>
          <p className="text-xs text-muted-foreground">
            This week / {kpiData.leadsImported.total.toLocaleString()} total
          </p>
        </CardContent>
      </Card>

      {/* Active Campaigns */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-gray-600">
            Active Campaigns
          </CardTitle>
          <Mail className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold text-green-600">
            {kpiData.activeCampaigns.running}
          </div>
          <p className="text-xs text-muted-foreground">
            Running / {kpiData.activeCampaigns.paused} paused
          </p>
        </CardContent>
      </Card>

      {/* Engagement Rate */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-gray-600">
            Engagement Rate
          </CardTitle>
          <TrendingUp className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold text-purple-600">
            {kpiData.engagementRate.deliveryRate.toFixed(1)}%
          </div>
          <p className="text-xs text-muted-foreground">
            Delivered / {kpiData.engagementRate.replyRate}% replies
          </p>
        </CardContent>
      </Card>

      {/* Deliverability Health */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-gray-600">
            Deliverability
          </CardTitle>
          <Shield className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="flex items-center space-x-2">
            <div className={`text-2xl font-bold ${deliverabilityStatus.color}`}>
              {kpiData.deliverabilityHealth.bounceRate}%
            </div>
            <deliverabilityStatus.icon
              className={`h-4 w-4 ${deliverabilityStatus.color}`}
            />
          </div>
          <p className="text-xs text-muted-foreground">
            Bounces / {kpiData.deliverabilityHealth.complaintRate}% complaints
          </p>
        </CardContent>
      </Card>

      {/* Quota Usage */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-medium text-gray-600">
            Quota Usage
          </CardTitle>
          <Zap className="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold text-orange-600">
            {kpiData.quotaUsage.emailsSentToday.toLocaleString()}
          </div>
          <div className="flex items-center justify-between mt-1">
            <p className="text-xs text-muted-foreground">
              / {kpiData.quotaUsage.dailyCap.toLocaleString()} today
            </p>
            <Badge variant="secondary" className="text-xs">
              {kpiData.quotaUsage.planUsagePercent}%
            </Badge>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
