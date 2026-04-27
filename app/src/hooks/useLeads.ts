import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";

export interface Lead {
  id: string;
  firstName: string;
  lastName: string;
  domain?: string;
  email: string;
  phone?: string;
  position?: string;
  positionRaw?: string;
  seniority?: string;
  department?: string;
  linkedinUrl?: string;
  twitter?: string;
  confidenceScore?: number;
  emailType?: "personal" | "generic" | "role" | "catch_all" | "unknown";
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

export function useLeads() {
  const [leads, setLeads] = useState<Lead[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const API_URL =
    process.env.NEXT_PUBLIC_API_URL || "https://api.outreach-ly.com";

  const fetchLeads = async () => {
    if (!user) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const response = await fetch(`${API_URL}/api/leads`, {
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
  }, [user]);

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

  // Create a lead
  const createLead = async (payload: {
    email: string;
    firstName: string;
    lastName?: string;
    domain?: string;
    phone?: string;
    linkedinUrl?: string;
    position?: string;
    positionRaw?: string;
    seniority?: string;
    department?: string;
    twitter?: string;
    emailType?: Lead["emailType"];
    verifiedStatus?: Lead["verifiedStatus"];
    customTextField?: string;
  }) => {
    const response = await fetch(`${API_URL}/api/leads`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data?.error || "Failed to create lead");
    }
    await fetchLeads();
    return data as Lead;
  };

  // Update a lead (email immutable)
  const updateLead = async (
    id: string,
    payload: Partial<
      Pick<
        Lead,
        | "firstName"
        | "lastName"
        | "position"
        | "positionRaw"
        | "seniority"
        | "department"
        | "phone"
        | "domain"
        | "linkedinUrl"
        | "twitter"
        | "customTextField"
        | "emailType"
      >
    >
  ) => {
    const response = await fetch(`${API_URL}/api/leads/${id}`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data?.error || "Failed to update lead");
    }
    await fetchLeads();
    return data as Lead;
  };

  // Delete lead mapping for org
  const deleteLead = async (id: string) => {
    const response = await fetch(`${API_URL}/api/leads/${id}`, {
      method: "DELETE",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(data?.error || "Failed to delete lead");
    }
    await fetchLeads();
    return true;
  };

  return {
    leads,
    loading,
    error,
    refetch: fetchLeads,
    enrichLeads,
    exportLeads,
    createLead,
    updateLead,
    deleteLead,
  };
}
