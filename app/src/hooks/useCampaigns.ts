import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";

export interface Campaign {
  id: string;
  name: string;
  description?: string;
  status: "active" | "paused" | "completed" | "inactive";
  createdAt: string;
  updatedAt: string;
  orgId: string;
}

export function useCampaigns() {
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  const fetchCampaigns = async () => {
    if (!user) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const response = await fetch(`${API_URL}/api/campaigns`, {
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      });

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error("Authentication required. Please log in again.");
        } else if (response.status === 403) {
          throw new Error(
            "Access denied. You do not have permission to view campaigns."
          );
        } else {
          throw new Error(
            `Failed to fetch campaigns: ${response.status} ${response.statusText}`
          );
        }
      }

      const data = await response.json();
      setCampaigns(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error("Error fetching campaigns:", err);
      setError(
        err instanceof Error ? err.message : "Failed to fetch campaigns"
      );
    } finally {
      setLoading(false);
    }
  };

  const createCampaign = async (name: string, description?: string) => {
    try {
      const response = await fetch(`${API_URL}/api/campaigns`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ name, description }),
      });

      if (!response.ok) {
        throw new Error(`Failed to create campaign: ${response.statusText}`);
      }

      const newCampaign = await response.json();
      setCampaigns((prev) => [newCampaign, ...prev]);
      return newCampaign;
    } catch (err) {
      console.error("Error creating campaign:", err);
      throw err;
    }
  };

  const updateCampaign = async (id: string, updates: Partial<Campaign>) => {
    try {
      const response = await fetch(`${API_URL}/api/campaigns/${id}`, {
        method: "PUT",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(updates),
      });

      if (!response.ok) {
        throw new Error(`Failed to update campaign: ${response.statusText}`);
      }

      const updatedCampaign = await response.json();
      setCampaigns((prev) =>
        prev.map((campaign) =>
          campaign.id === id ? updatedCampaign : campaign
        )
      );
      return updatedCampaign;
    } catch (err) {
      console.error("Error updating campaign:", err);
      throw err;
    }
  };

  const deleteCampaign = async (id: string) => {
    try {
      const response = await fetch(`${API_URL}/api/campaigns/${id}`, {
        method: "DELETE",
        credentials: "include",
      });

      if (!response.ok) {
        throw new Error(`Failed to delete campaign: ${response.statusText}`);
      }

      setCampaigns((prev) => prev.filter((campaign) => campaign.id !== id));
    } catch (err) {
      console.error("Error deleting campaign:", err);
      throw err;
    }
  };

  useEffect(() => {
    fetchCampaigns();
  }, [user]);

  return {
    campaigns,
    loading,
    error,
    refetch: fetchCampaigns,
    createCampaign,
    updateCampaign,
    deleteCampaign,
  };
}
