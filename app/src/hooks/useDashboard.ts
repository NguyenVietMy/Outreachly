import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { API_BASE_URL } from "@/lib/config";

export interface DashboardMetrics {
  githubCommits: number;
  obsidianNotes: number;
  slackMessages: number;
  linearTickets: number;
  greeting: string;
  dateLabel: string;
}

export interface ActivityItem {
  id: string;
  title: string;
  provider: string;
  eventType: string;
  timeAgo: string;
  timestamp: string;
}

export interface ActivityBySource {
  [provider: string]: number;
}

export function useDashboard() {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [activity, setActivity] = useState<ActivityItem[]>([]);
  const [activityBySource, setActivityBySource] = useState<ActivityBySource>({});
  const [digest, setDigest] = useState<string | null>(null);
  const [loadingMetrics, setLoadingMetrics] = useState(true);
  const [loadingActivity, setLoadingActivity] = useState(true);
  const [loadingDigest, setLoadingDigest] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const fetchMetrics = useCallback(async () => {
    if (!user) {
      setLoadingMetrics(false);
      return;
    }
    try {
      setLoadingMetrics(true);
      const res = await fetch(`${API_BASE_URL}/api/dashboard/metrics`, {
        credentials: "include",
      });
      if (!res.ok) throw new Error("Failed to fetch metrics");
      const data = await res.json();
      setMetrics(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch metrics");
    } finally {
      setLoadingMetrics(false);
    }
  }, [user]);

  const fetchActivity = useCallback(async () => {
    if (!user) {
      setLoadingActivity(false);
      return;
    }
    try {
      setLoadingActivity(true);
      const [activityRes, sourceRes] = await Promise.all([
        fetch(`${API_BASE_URL}/api/dashboard/activity?limit=10`, {
          credentials: "include",
        }),
        fetch(`${API_BASE_URL}/api/dashboard/activity-by-source`, {
          credentials: "include",
        }),
      ]);

      if (activityRes.ok) {
        setActivity(await activityRes.json());
      }
      if (sourceRes.ok) {
        setActivityBySource(await sourceRes.json());
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch activity");
    } finally {
      setLoadingActivity(false);
    }
  }, [user]);

  const requestDigest = async () => {
    setLoadingDigest(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/dashboard/digest`, {
        method: "POST",
        credentials: "include",
      });
      if (!res.ok) throw new Error("Failed to generate digest");
      const data = await res.json();
      setDigest(data.digest);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate digest");
    } finally {
      setLoadingDigest(false);
    }
  };

  const refetch = useCallback(async () => {
    await Promise.all([fetchMetrics(), fetchActivity()]);
  }, [fetchMetrics, fetchActivity]);

  useEffect(() => {
    fetchMetrics();
    fetchActivity();
  }, [fetchMetrics, fetchActivity]);

  return {
    metrics,
    activity,
    activityBySource,
    digest,
    loadingMetrics,
    loadingActivity,
    loadingDigest,
    error,
    requestDigest,
    refetch,
  };
}
