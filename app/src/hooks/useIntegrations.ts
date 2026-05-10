import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { API_BASE_URL } from "@/lib/config";

export interface Integration {
  provider: string;
  status: "connected" | "disconnected";
  supportsSync: boolean;
  accountLabel: string | null;
  accountValue: string | null;
  eventsLabel: string | null;
  eventsValue: string | null;
  lastActivityLabel: string | null;
  scopeSummary: string | null;
  selectedResourceIds: string[];
  activitySparkline: number[];
  webhookStatus: string;
  webhookError: string | null;
}

export interface IntegrationResourceOption {
  id: string;
  label: string;
  type: string;
}

export function useIntegrations() {
  const [integrations, setIntegrations] = useState<Integration[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const fetchIntegrations = useCallback(async () => {
    if (!user) {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      const res = await fetch(`${API_BASE_URL}/api/integrations`, {
        credentials: "include",
      });
      if (!res.ok) {
        if (res.status === 401 || res.status === 403) {
          setError("Not authorized");
          return;
        }
        throw new Error("Failed to fetch integrations");
      }
      const data = await res.json();
      setIntegrations(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch integrations");
    } finally {
      setLoading(false);
    }
  }, [user]);

  const connectOAuth = async (provider: string) => {
    const res = await fetch(
      `${API_BASE_URL}/api/integrations/${provider}/auth-url`,
      { credentials: "include" }
    );
    if (!res.ok) throw new Error("Failed to get auth URL");
    const data = await res.json();
    window.location.href = data.url;
  };

  const connect = async (provider: string): Promise<boolean> => {
    const res = await fetch(
      `${API_BASE_URL}/api/integrations/${provider}/connect`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      }
    );
    if (res.status === 404) {
      return false;
    }
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || "Failed to connect");
    }
    await fetchIntegrations();
    return true;
  };

  const getResources = async (provider: string) => {
    const res = await fetch(
      `${API_BASE_URL}/api/integrations/${provider}/resources`,
      { credentials: "include" }
    );
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || "Failed to load resources");
    }
    return (await res.json()) as IntegrationResourceOption[];
  };

  const updateScope = async (provider: string, selectedResourceIds: string[]) => {
    const res = await fetch(
      `${API_BASE_URL}/api/integrations/${provider}/scope`,
      {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ selectedResourceIds }),
      }
    );
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || "Failed to update scope");
    }
    await fetchIntegrations();
    return (await res.json()) as Integration;
  };

  const disconnect = async (provider: string) => {
    await fetch(`${API_BASE_URL}/api/integrations/${provider}`, {
      method: "DELETE",
      credentials: "include",
    });
    await fetchIntegrations();
  };

  const sync = async (provider: string) => {
    const res = await fetch(
      `${API_BASE_URL}/api/integrations/${provider}/sync`,
      {
        method: "POST",
        credentials: "include",
      }
    );
    if (!res.ok) throw new Error("Sync failed");
    const data = await res.json();
    await fetchIntegrations();
    return data as { newEvents: number; totalFetched: number };
  };

  useEffect(() => {
    fetchIntegrations();
  }, [fetchIntegrations]);

  return {
    integrations,
    loading,
    error,
    refetch: fetchIntegrations,
    connectOAuth,
    connect,
    getResources,
    updateScope,
    disconnect,
    sync,
  };
}
