import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";

export interface CampaignStats {
  campaignId: string;
  totalLeads: number;
  emailsSent: number;
  emailsDelivered: number;
  emailsFailed: number;
}

export interface CampaignWithStats {
  id: string;
  name: string;
  description?: string;
  status: "active" | "paused" | "completed" | "inactive";
  createdAt: string;
  updatedAt: string;
  orgId: string;
  stats?: CampaignStats;
}

export function useCampaignStats() {
  const [campaignsWithStats, setCampaignsWithStats] = useState<
    CampaignWithStats[]
  >([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const API_URL =
    process.env.NEXT_PUBLIC_API_URL || "https://api.outreach-ly.com";

  const fetchCampaignsWithStats = async () => {
    if (!user) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      // First, fetch campaigns
      const campaignsResponse = await fetch(`${API_URL}/api/campaigns`, {
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      });

      if (!campaignsResponse.ok) {
        throw new Error(
          `Failed to fetch campaigns: ${campaignsResponse.statusText}`
        );
      }

      const campaigns: CampaignWithStats[] = await campaignsResponse.json();

      // Then fetch stats for each campaign
      const campaignsWithStatsPromises = campaigns.map(async (campaign) => {
        try {
          const statsResponse = await fetch(
            `${API_URL}/api/campaigns/${campaign.id}/stats`,
            {
              credentials: "include",
              headers: {
                "Content-Type": "application/json",
              },
            }
          );

          if (statsResponse.ok) {
            const stats: CampaignStats = await statsResponse.json();
            return { ...campaign, stats };
          } else {
            // If stats fail, return campaign without stats
            return campaign;
          }
        } catch (error) {
          console.warn(
            `Failed to fetch stats for campaign ${campaign.id}:`,
            error
          );
          return campaign;
        }
      });

      const campaignsWithStats = await Promise.all(campaignsWithStatsPromises);
      setCampaignsWithStats(campaignsWithStats);
    } catch (err) {
      console.error("Error fetching campaigns with stats:", err);
      setError(
        err instanceof Error ? err.message : "Failed to fetch campaigns"
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCampaignsWithStats();
  }, [user]);

  return {
    campaignsWithStats,
    loading,
    error,
    refetch: fetchCampaignsWithStats,
  };
}
