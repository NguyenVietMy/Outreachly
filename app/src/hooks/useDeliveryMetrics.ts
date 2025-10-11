import { useState, useEffect, useCallback } from "react";
import { API_BASE_URL } from "@/lib/config";

export interface DeliveryStats {
  totalSent: number;
  totalDelivered: number;
  totalFailed: number;
  deliveryRate: number;
}

export interface TrendData {
  date: string;
  delivered: number;
  failed: number;
  totalSent: number;
  deliveryRate: number;
}

export interface KPIData {
  leadsImported: {
    thisWeek: number;
    total: number;
  };
  activeCampaigns: {
    running: number;
    paused: number;
  };
  engagementRate: {
    deliveryRate: number;
    replyRate: number;
  };
  deliverabilityHealth: {
    bounceRate: number;
    complaintRate: number;
  };
  quotaUsage: {
    emailsSentToday: number;
    dailyCap: number;
    planUsagePercent: number;
  };
}

export interface DateInfo {
  utcDate: string;
  systemDate: string;
  timezone: string;
}

export function useDeliveryMetrics() {
  const [userStats, setUserStats] = useState<DeliveryStats | null>(null);
  const [campaignStats, setCampaignStats] = useState<DeliveryStats | null>(
    null
  );
  const [trendData, setTrendData] = useState<TrendData[]>([]);
  const [kpiData, setKpiData] = useState<KPIData | null>(null);
  const [dateInfo, setDateInfo] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchUserStats = useCallback(async () => {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/metrics/delivery-rate/user`
      );
      if (!response.ok) throw new Error("Failed to fetch user stats");
      const data = await response.json();
      setUserStats(data);
    } catch (err) {
      console.error("Error fetching user stats:", err);
      setError(err instanceof Error ? err.message : "Unknown error");
    }
  }, []);

  const fetchCampaignStats = useCallback(async (campaignId: string) => {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/metrics/delivery-rate/campaign/${campaignId}`
      );
      if (!response.ok) throw new Error("Failed to fetch campaign stats");
      const data = await response.json();
      setCampaignStats(data);
    } catch (err) {
      console.error("Error fetching campaign stats:", err);
      setError(err instanceof Error ? err.message : "Unknown error");
    }
  }, []);

  const fetchTrendData = useCallback(async (period: string) => {
    try {
      let url = `${API_BASE_URL}/api/metrics/delivery-rate/trends`;

      // Handle different period types
      if (period === "monthly") {
        url = `${API_BASE_URL}/api/metrics/delivery-rate/trends/monthly`;
      } else {
        // Convert period string to days
        const days = parsePeriodToDays(period);
        url += `?days=${days}`;
      }

      const response = await fetch(url);
      if (!response.ok) throw new Error("Failed to fetch trend data");
      const data = await response.json();
      setTrendData(data);
    } catch (err) {
      console.error("Error fetching trend data:", err);
      setError(err instanceof Error ? err.message : "Unknown error");
      // Fallback to empty array if API fails
      setTrendData([]);
    }
  }, []);

  // Helper function to convert period strings to days
  const parsePeriodToDays = (period: string): number => {
    switch (period.toLowerCase()) {
      case "7d":
      case "7":
        return 7;
      case "30d":
      case "30":
        return 30;
      case "90d":
      case "90":
        return 90;
      default:
        return 7; // Default to 7 days
    }
  };

  const fetchDateInfo = useCallback(async () => {
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/metrics/delivery-rate/date-info`
      );
      if (!response.ok) throw new Error("Failed to fetch date info");
      const data = await response.text();
      setDateInfo(data);
    } catch (err) {
      console.error("Error fetching date info:", err);
      setDateInfo("Error fetching date info");
    }
  }, []);

  const loadAllData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      await fetchUserStats();
      await fetchTrendData("7d");
      await fetchDateInfo();
      // Note: We'll need to get campaignId from context/auth for campaign-specific stats
    } catch (err) {
      console.error("Error loading delivery metrics:", err);
      setError(err instanceof Error ? err.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  }, [fetchUserStats, fetchTrendData, fetchDateInfo]);

  const generateKPIData = (stats: DeliveryStats | null): KPIData => {
    if (!stats) {
      return {
        leadsImported: { thisWeek: 0, total: 0 },
        activeCampaigns: { running: 0, paused: 0 },
        engagementRate: { deliveryRate: 0, replyRate: 0 },
        deliverabilityHealth: { bounceRate: 0, complaintRate: 0 },
        quotaUsage: { emailsSentToday: 0, dailyCap: 2000, planUsagePercent: 0 },
      };
    }

    return {
      leadsImported: {
        thisWeek: Math.round(stats.totalSent * 0.1), // Assume 10% of total sent this week
        total: stats.totalSent,
      },
      activeCampaigns: {
        running: Math.max(1, Math.floor(stats.totalSent / 1000)), // Estimate based on volume
        paused: 0,
      },
      engagementRate: {
        deliveryRate: stats.deliveryRate,
        replyRate: 0, // We'll implement reply tracking later
      },
      deliverabilityHealth: {
        bounceRate:
          stats.totalFailed > 0
            ? (stats.totalFailed / stats.totalSent) * 100
            : 0,
        complaintRate: 0, // We'll implement complaint tracking later
      },
      quotaUsage: {
        emailsSentToday: Math.round(stats.totalSent * 0.05), // Assume 5% sent today
        dailyCap: 2000,
        planUsagePercent: Math.min(
          100,
          ((stats.totalSent * 0.05) / 2000) * 100
        ),
      },
    };
  };

  useEffect(() => {
    loadAllData();
  }, []);

  useEffect(() => {
    if (userStats) {
      setKpiData(generateKPIData(userStats));
    }
  }, [userStats]);

  return {
    userStats,
    campaignStats,
    trendData,
    kpiData,
    dateInfo,
    loading,
    error,
    refetch: loadAllData,
    fetchTrendData,
  };
}
