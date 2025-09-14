import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";

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

  return {
    leads,
    loading,
    error,
    refetch: fetchLeads,
  };
}
