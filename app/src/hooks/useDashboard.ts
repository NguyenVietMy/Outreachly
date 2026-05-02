import { useState, useEffect, useCallback, useRef } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { API_BASE_URL } from "@/lib/config";

export interface DashboardMetrics {
  githubCommits: number;
  obsidianNotes: number;
  slackMessages: number;
  linearTickets: number;
  greeting: string;
  dateLabel: string;
  eventTypeBreakdown: Record<string, Record<string, number>>;
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

export interface TrendData {
  [date: string]: Record<string, number>;
}

export interface Integration {
  provider: string;
  status: "connected" | "disconnected";
  supportsSync: boolean;
  lastSyncedAt: string | null;
  consecutiveFailures: number;
  autoSyncEnabled: boolean;
}

export interface AxisScores {
  dsa: number;
  projects: number;
  systemDesign: number;
  coreCs: number;
  resume: number;
}

export interface ResumeScoreBreakdown {
  total_score?: number;
  max_score?: number;
  decision?: "ADVANCE" | "HOLD" | "REJECT";
  flags?: string[];
  summary?: string;
}

export interface UserProfile {
  axisScores: AxisScores;
  resumeScoreBreakdown: ResumeScoreBreakdown | null;
  onboardingCompleted: boolean;
}

export interface UserGoal {
  id: string;
  title: string;
  category: string;
  targetValue: number | null;
  currentValue: number;
  unit: string;
  deadline: string | null;
  status: string;
}

export interface DailySuggestionTask {
  title: string;
  description: string;
  priority: 0 | 1 | 2;
  category: "project" | "learning" | "career" | "review";
  repoContext: string | null;
  rationale: string;
}

export interface DailySuggestionsResponse {
  date: string;
  generatedAt: string;
  tasks: DailySuggestionTask[];
}

export function useDashboard() {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [activity, setActivity] = useState<ActivityItem[]>([]);
  const [activityBySource, setActivityBySource] = useState<ActivityBySource>({});
  const [trendData, setTrendData] = useState<TrendData>({});
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [goals, setGoals] = useState<UserGoal[]>([]);
  const [integrations, setIntegrations] = useState<Integration[]>([]);
  const [digest, setDigest] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<DailySuggestionsResponse | null>(null);
  const [loadingMetrics, setLoadingMetrics] = useState(true);
  const [loadingActivity, setLoadingActivity] = useState(true);
  const [loadingDigest, setLoadingDigest] = useState(false);
  const [loadingSuggestions, setLoadingSuggestions] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();
  const activityRef = useRef<ActivityItem[]>([]);
  const autoDigestFired = useRef(false);

  const fetchAll = useCallback(async () => {
    if (!user) {
      setLoadingMetrics(false);
      setLoadingActivity(false);
      return;
    }
    try {
      setLoadingMetrics(true);
      setLoadingActivity(true);

      const [metricsRes, activityRes, sourceRes, trendRes, profileRes, goalsRes, integrationsRes, suggestionsRes] =
        await Promise.all([
          fetch(`${API_BASE_URL}/api/dashboard/metrics`, { credentials: "include" }),
          fetch(`${API_BASE_URL}/api/dashboard/activity?limit=8`, { credentials: "include" }),
          fetch(`${API_BASE_URL}/api/dashboard/activity-by-source`, { credentials: "include" }),
          fetch(`${API_BASE_URL}/api/dashboard/trend?days=14`, { credentials: "include" }),
          fetch(`${API_BASE_URL}/api/personal/profile`, { credentials: "include" }),
          fetch(`${API_BASE_URL}/api/personal/goals`, { credentials: "include" }),
          fetch(`${API_BASE_URL}/api/integrations`, { credentials: "include" }),
          fetch(`${API_BASE_URL}/api/personal/suggestions/today`, { credentials: "include" }),
        ]);

      if (metricsRes.ok) setMetrics(await metricsRes.json());
      if (activityRes.ok) setActivity(await activityRes.json());
      if (sourceRes.ok) setActivityBySource(await sourceRes.json());
      if (trendRes.ok) setTrendData(await trendRes.json());
      if (profileRes.ok) setProfile(await profileRes.json());
      if (goalsRes.ok) setGoals(await goalsRes.json());
      if (integrationsRes.ok) setIntegrations(await integrationsRes.json());
      if (suggestionsRes.ok) setSuggestions(await suggestionsRes.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch dashboard data");
    } finally {
      setLoadingMetrics(false);
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
      const latest = activityRef.current[0]?.timestamp;
      if (latest) {
        localStorage.setItem("pulse_digest_activity_ts", latest);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate digest");
    } finally {
      setLoadingDigest(false);
    }
  };

  const regenerateSuggestions = async () => {
    setLoadingSuggestions(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/suggestions/regenerate`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        setSuggestions(data);
        return data;
      }
      if (res.status === 429) {
        const err = await res.json();
        throw new Error(err.nextAllowedAt
          ? `Try again after ${new Date(err.nextAllowedAt).toLocaleTimeString()}`
          : "Too many requests");
      }
      throw new Error("Failed to regenerate suggestions");
    } finally {
      setLoadingSuggestions(false);
    }
  };

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  useEffect(() => {
    activityRef.current = activity;
    if (activity.length === 0 || loadingDigest || autoDigestFired.current) return;
    const lastTs = localStorage.getItem("pulse_digest_activity_ts");
    const newCount = lastTs
      ? activity.filter((a) => a.timestamp > lastTs).length
      : activity.length;
    if (newCount >= 3) {
      autoDigestFired.current = true;
      requestDigest();
    }
  }, [activity, loadingDigest]);

  return {
    metrics,
    activity,
    activityBySource,
    trendData,
    profile,
    goals,
    integrations,
    digest,
    suggestions,
    loadingMetrics,
    loadingActivity,
    loadingDigest,
    loadingSuggestions,
    error,
    requestDigest,
    regenerateSuggestions,
    refetch: fetchAll,
  };
}
