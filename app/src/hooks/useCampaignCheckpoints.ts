import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";

export interface CampaignCheckpoint {
  id: string;
  campaignId: string;
  orgId: string;
  name: string;
  dayOfWeek:
    | "MONDAY"
    | "TUESDAY"
    | "WEDNESDAY"
    | "THURSDAY"
    | "FRIDAY"
    | "SATURDAY"
    | "SUNDAY";
  timeOfDay: string; // HH:mm:ss format
  emailTemplateId?: string;
  status: "pending" | "active" | "paused" | "completed";
  createdAt: string;
  updatedAt: string;
}

export interface CampaignCheckpointLead {
  id: string;
  checkpointId: string;
  leadId: string;
  orgId: string;
  status: "pending" | "sent" | "delivered" | "failed";
  scheduledAt: string;
  sentAt?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CheckpointStats {
  checkpointId: string;
  totalLeads: number;
  pendingLeads: number;
  sentLeads: number;
  deliveredLeads: number;
  failedLeads: number;
  successRate: number;
  failureRate: number;
}

export interface EmailTemplate {
  id: string;
  orgId: string;
  name: string;
  platform: string;
  category?: string;
  contentJson: string;
  createdAt: string;
  updatedAt: string;
}

export function useCampaignCheckpoints(campaignId?: string) {
  const [checkpoints, setCheckpoints] = useState<CampaignCheckpoint[]>([]);
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const API_URL =
    process.env.NEXT_PUBLIC_API_URL || "https://api.outreach-ly.com";

  const fetchCheckpoints = async () => {
    if (!user || !campaignId) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints`,
        {
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
        }
      );

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error("Authentication required. Please log in again.");
        } else if (response.status === 403) {
          throw new Error(
            "Access denied. You do not have permission to view checkpoints."
          );
        } else {
          throw new Error(
            `Failed to fetch checkpoints: ${response.status} ${response.statusText}`
          );
        }
      }

      const data = await response.json();
      setCheckpoints(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error("Error fetching checkpoints:", err);
      setError(
        err instanceof Error ? err.message : "Failed to fetch checkpoints"
      );
    } finally {
      setLoading(false);
    }
  };

  const fetchTemplates = async () => {
    if (!user) return;

    try {
      const response = await fetch(`${API_URL}/api/campaigns/templates`, {
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      });

      if (response.ok) {
        const data = await response.json();
        setTemplates(Array.isArray(data) ? data : []);
      }
    } catch (err) {
      console.error("Error fetching templates:", err);
    }
  };

  const createCheckpoint = async (checkpoint: {
    name: string;
    dayOfWeek: CampaignCheckpoint["dayOfWeek"];
    timeOfDay: string;
    emailTemplateId?: string;
    leadIds?: string[];
  }) => {
    if (!campaignId) throw new Error("Campaign ID is required");

    try {
      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints`,
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(checkpoint),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to create checkpoint: ${response.statusText}`);
      }

      const newCheckpoint = await response.json();
      setCheckpoints((prev) => [newCheckpoint, ...prev]);
      return newCheckpoint;
    } catch (err) {
      console.error("Error creating checkpoint:", err);
      throw err;
    }
  };

  const updateCheckpoint = async (
    checkpointId: string,
    updates: Partial<CampaignCheckpoint>
  ) => {
    if (!campaignId) throw new Error("Campaign ID is required");

    try {
      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints/${checkpointId}`,
        {
          method: "PUT",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(updates),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to update checkpoint: ${response.statusText}`);
      }

      const updatedCheckpoint = await response.json();
      setCheckpoints((prev) =>
        prev.map((checkpoint) =>
          checkpoint.id === checkpointId ? updatedCheckpoint : checkpoint
        )
      );
      return updatedCheckpoint;
    } catch (err) {
      console.error("Error updating checkpoint:", err);
      throw err;
    }
  };

  const deleteCheckpoint = async (checkpointId: string) => {
    if (!campaignId) throw new Error("Campaign ID is required");

    try {
      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints/${checkpointId}`,
        {
          method: "DELETE",
          credentials: "include",
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to delete checkpoint: ${response.statusText}`);
      }

      setCheckpoints((prev) =>
        prev.filter((checkpoint) => checkpoint.id !== checkpointId)
      );
    } catch (err) {
      console.error("Error deleting checkpoint:", err);
      throw err;
    }
  };

  const activateCheckpoint = async (checkpointId: string) => {
    if (!campaignId) throw new Error("Campaign ID is required");

    try {
      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints/${checkpointId}/activate`,
        {
          method: "POST",
          credentials: "include",
        }
      );

      if (!response.ok) {
        throw new Error(
          `Failed to activate checkpoint: ${response.statusText}`
        );
      }

      const activatedCheckpoint = await response.json();
      setCheckpoints((prev) =>
        prev.map((checkpoint) =>
          checkpoint.id === checkpointId ? activatedCheckpoint : checkpoint
        )
      );
      return activatedCheckpoint;
    } catch (err) {
      console.error("Error activating checkpoint:", err);
      throw err;
    }
  };

  const pauseCheckpoint = async (checkpointId: string) => {
    if (!campaignId) throw new Error("Campaign ID is required");

    try {
      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints/${checkpointId}/pause`,
        {
          method: "POST",
          credentials: "include",
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to pause checkpoint: ${response.statusText}`);
      }

      const pausedCheckpoint = await response.json();
      setCheckpoints((prev) =>
        prev.map((checkpoint) =>
          checkpoint.id === checkpointId ? pausedCheckpoint : checkpoint
        )
      );
      return pausedCheckpoint;
    } catch (err) {
      console.error("Error pausing checkpoint:", err);
      throw err;
    }
  };

  const getCheckpointStats = async (
    checkpointId: string
  ): Promise<CheckpointStats> => {
    if (!campaignId) throw new Error("Campaign ID is required");

    try {
      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints/${checkpointId}/stats`,
        {
          credentials: "include",
        }
      );

      if (!response.ok) {
        throw new Error(
          `Failed to fetch checkpoint stats: ${response.statusText}`
        );
      }

      return await response.json();
    } catch (err) {
      console.error("Error fetching checkpoint stats:", err);
      throw err;
    }
  };

  useEffect(() => {
    fetchCheckpoints();
    fetchTemplates();
  }, [user, campaignId]);

  return {
    checkpoints,
    templates,
    loading,
    error,
    refetch: fetchCheckpoints,
    createCheckpoint,
    updateCheckpoint,
    deleteCheckpoint,
    activateCheckpoint,
    pauseCheckpoint,
    getCheckpointStats,
  };
}
