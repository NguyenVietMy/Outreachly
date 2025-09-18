import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";

export interface CampaignInfo {
  id: string;
  name: string;
  description?: string;
  status: string;
  addedAt?: string;
}

export interface Lead {
  id: string;
  firstName: string;
  lastName: string;
  company: string;
  title: string;
  email: string;
  phone?: string;
  domain?: string;
  linkedinUrl?: string;
  country?: string;
  state?: string;
  city?: string;
  customTextField?: string;
  source?: string;
  verifiedStatus: "unknown" | "valid" | "risky" | "invalid";
  enrichedJson?: string;
  enrichmentHistory?: string;
  createdAt: string;
  updatedAt: string;
  orgId: string;
  listId?: string;
  campaigns?: CampaignInfo[];
}

export function useLeads(campaignId?: string) {
  const [leads, setLeads] = useState<Lead[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  const fetchLeads = async () => {
    if (!user) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const url = campaignId
        ? `${API_URL}/api/leads?campaignId=${campaignId}`
        : `${API_URL}/api/leads`;

      const response = await fetch(url, {
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
            "Access denied. You do not have permission to view leads."
          );
        } else {
          throw new Error(
            `Failed to fetch leads: ${response.status} ${response.statusText}`
          );
        }
      }

      const data = await response.json();
      setLeads(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error("Error fetching leads:", err);
      setError(err instanceof Error ? err.message : "Failed to fetch leads");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLeads();
  }, [user, campaignId]);

  // Enrich leads
  const enrichLeads = async (leadIds: string[]) => {
    try {
      const promises = leadIds.map(async (leadId) => {
        const response = await fetch(`${API_URL}/api/leads/${leadId}/enrich`, {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          throw new Error(
            `Failed to enrich lead ${leadId}: ${response.statusText}`
          );
        }

        return response.json();
      });

      const results = await Promise.all(promises);
      await fetchLeads(); // Refresh leads after enrichment
      return results;
    } catch (err) {
      console.error("Error enriching leads:", err);
      throw err;
    }
  };

  // Export leads to CSV
  const exportLeads = async (leadIds: string[]) => {
    try {
      const response = await fetch(`${API_URL}/api/leads/export`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ leadIds }),
      });

      if (!response.ok) {
        throw new Error(`Failed to export leads: ${response.statusText}`);
      }

      // Create download link
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `leads-export-${new Date().toISOString().split("T")[0]}.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);

      return true;
    } catch (err) {
      console.error("Error exporting leads:", err);
      throw err;
    }
  };

  // Assign leads to campaign
  const assignLeadsToCampaign = async (
    leadIds: string[],
    campaignId: string
  ) => {
    try {
      const response = await fetch(`${API_URL}/api/leads/bulk-campaign`, {
        method: "PUT",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ leadIds, campaignId }),
      });

      if (!response.ok) {
        throw new Error(
          `Failed to assign leads to campaign: ${response.statusText}`
        );
      }

      const result = await response.json();
      await fetchLeads(); // Refresh leads after assignment
      return result;
    } catch (err) {
      console.error("Error assigning leads to campaign:", err);
      throw err;
    }
  };

  return {
    leads,
    loading,
    error,
    refetch: fetchLeads,
    enrichLeads,
    exportLeads,
    assignLeadsToCampaign,
  };
}
