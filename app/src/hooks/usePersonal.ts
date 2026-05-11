import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { API_BASE_URL } from "@/lib/config";

export interface AxisScores {
  dsa: number;
  projects: number;
  systemDesign: number;
  coreCs: number;
  resume: number;
}

export interface SubskillAssessment {
  subskillId: string;
  tier: 0 | 1 | 2 | 3 | 4;
}

export interface SectionAssessment {
  sectionId: string;
  confidence: "low" | "medium" | "high";
  completedAt: string;
  subskills: SubskillAssessment[];
}

export interface AxisAssessmentPayload {
  axisId: string;
  completedAt: string | null;
  sections: SectionAssessment[];
}

export interface LeetCodeStats {
  username: string;
  easy: number;
  medium: number;
  hard: number;
  total: number;
  ranking: number;
  dsaScore: number;
}

export interface ResumeSubScore {
  score: number;
  max: number;
  rationale: string;
}

export interface ResumeScoreBreakdown {
  candidate_id?: string;
  scoring_date?: string;
  sections?: {
    technical_skills?: {
      primary_language?: ResumeSubScore;
      backend_frameworks?: ResumeSubScore;
      data_layer?: ResumeSubScore;
      infra_devops?: ResumeSubScore;
    };
    experience?: {
      years?: ResumeSubScore;
      progression?: ResumeSubScore;
      recency?: ResumeSubScore;
    };
    impact?: {
      quantified_outcomes?: ResumeSubScore;
      scope?: ResumeSubScore;
    };
    education?: {
      degree?: ResumeSubScore;
      certifications?: ResumeSubScore;
    };
    project_complexity?: ResumeSubScore;
    resume_quality?: {
      parsability?: ResumeSubScore;
      conciseness?: ResumeSubScore;
    };
  };
  total_score?: number;
  max_score?: number;
  decision?: "ADVANCE" | "HOLD" | "REJECT";
  flags?: string[];
  summary?: string;
}

export interface UserProfile {
  profileMarkdown: string;
  onboardingCompleted: boolean;
  leetcodeUsername: string | null;
  leetcodeStats: LeetCodeStats | null;
  resumeFilename: string | null;
  hasResume: boolean;
  systemDesignAnswers: AxisAssessmentPayload | Record<string, unknown>;
  coreCsAnswers: AxisAssessmentPayload | Record<string, unknown>;
  axisScores: AxisScores;
  resumeScoreBreakdown: ResumeScoreBreakdown | null;
  targetRole: "swe_intern" | "swe_job" | null;
  graduationYear: number | null;
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

export interface AiTask {
  id: string;
  axis: string;
  sectionId: string | null;
  title: string;
  description: string | null;
  completed: boolean;
  source: string;
  priority: number;
  orderIndex: number;
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

export interface RoadmapItem {
  id: string;
  title: string;
  description: string | null;
  phase: string | null;
  deadline: string | null;
  focusRank: number;
  aiRationale: string | null;
  status: "pending" | "in_progress" | "completed";
}

export interface OnboardingData {
  targetRole: string;
  graduationYear: number;
}

export function usePersonal() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [goals, setGoals] = useState<UserGoal[]>([]);
  const [heatmap, setHeatmap] = useState<Record<string, number>>({});
  const [tasks, setTasks] = useState<AiTask[]>([]);
  const [insights, setInsights] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<DailySuggestionsResponse | null>(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [loadingGoals, setLoadingGoals] = useState(true);
  const [loadingHeatmap, setLoadingHeatmap] = useState(true);
  const [loadingTasks, setLoadingTasks] = useState(true);
  const [loadingInsights, setLoadingInsights] = useState(false);
  const [loadingSuggestions, setLoadingSuggestions] = useState(false);
  const [loadingLeetCode, setLoadingLeetCode] = useState(false);
  const [loadingResume, setLoadingResume] = useState(false);
  const [loadingQuestionnaire, setLoadingQuestionnaire] = useState(false);
  const [scoringResume, setScoringResume] = useState(false);
  const [roadmap, setRoadmap] = useState<RoadmapItem[]>([]);
  const [loadingRoadmap, setLoadingRoadmap] = useState(true);
  const [generatingRoadmap, setGeneratingRoadmap] = useState(false);
  const [canGenerateRoadmap, setCanGenerateRoadmap] = useState(false);
  const { user } = useAuth();

  const fetchProfile = useCallback(async () => {
    if (!user) { setLoadingProfile(false); return; }
    try {
      setLoadingProfile(true);
      const res = await fetch(`${API_BASE_URL}/api/personal/profile`, { credentials: "include" });
      if (res.ok) setProfile(await res.json());
    } finally {
      setLoadingProfile(false);
    }
  }, [user]);

  const fetchGoals = useCallback(async () => {
    if (!user) { setLoadingGoals(false); return; }
    try {
      setLoadingGoals(true);
      const res = await fetch(`${API_BASE_URL}/api/personal/goals`, { credentials: "include" });
      if (res.ok) setGoals(await res.json());
    } finally {
      setLoadingGoals(false);
    }
  }, [user]);

  const fetchHeatmap = useCallback(async () => {
    if (!user) { setLoadingHeatmap(false); return; }
    try {
      setLoadingHeatmap(true);
      const res = await fetch(`${API_BASE_URL}/api/personal/heatmap?months=3`, { credentials: "include" });
      if (res.ok) setHeatmap(await res.json());
    } finally {
      setLoadingHeatmap(false);
    }
  }, [user]);

  const fetchTasks = useCallback(async () => {
    if (!user) { setLoadingTasks(false); return; }
    try {
      setLoadingTasks(true);
      const res = await fetch(`${API_BASE_URL}/api/personal/tasks`, { credentials: "include" });
      if (res.ok) setTasks(await res.json());
    } finally {
      setLoadingTasks(false);
    }
  }, [user]);

  const updateProfile = async (profileMarkdown: string) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/profile`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ profileMarkdown }),
    });
    if (res.ok) {
      const data = await res.json();
      setProfile(data);
      return data;
    }
    throw new Error("Failed to update profile");
  };

  const completeOnboarding = async (data: OnboardingData) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/onboarding`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (res.ok) {
      const profile = await res.json();
      setProfile(profile);
      await fetchGoals();
      fetchCanGenerateRoadmap();
      return profile;
    }
    throw new Error("Failed to complete onboarding");
  };

  const connectLeetCode = async (username: string) => {
    setLoadingLeetCode(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/leetcode/connect`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username }),
      });
      if (res.ok) {
        const data = await res.json();
        setProfile(data);
        fetchCanGenerateRoadmap();
        return data;
      }
      const err = await res.text();
      throw new Error(err || "LeetCode user not found");
    } finally {
      setLoadingLeetCode(false);
    }
  };

  const refreshLeetCode = async () => {
    setLoadingLeetCode(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/leetcode/refresh`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        setProfile(data);
        return data;
      }
      throw new Error("Failed to refresh LeetCode stats");
    } finally {
      setLoadingLeetCode(false);
    }
  };

  const uploadResume = async (file: File) => {
    setLoadingResume(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      const res = await fetch(`${API_BASE_URL}/api/personal/resume/upload`, {
        method: "POST",
        credentials: "include",
        body: formData,
      });
      if (res.ok) {
        const data = await res.json();
        setProfile(data);
        fetchCanGenerateRoadmap();
        return data;
      }
      throw new Error("Failed to upload resume");
    } finally {
      setLoadingResume(false);
    }
  };

  const deleteResume = async () => {
    const res = await fetch(`${API_BASE_URL}/api/personal/resume`, {
      method: "DELETE",
      credentials: "include",
    });
    if (res.ok) {
      const data = await res.json();
      setProfile(data);
      fetchCanGenerateRoadmap();
    }
  };

  const scoreResume = async () => {
    setScoringResume(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/resume/score`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        setProfile(data);
        return data;
      }
      throw new Error("Failed to score resume");
    } finally {
      setScoringResume(false);
    }
  };

  const toggleTask = async (taskId: string) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/tasks/${taskId}/toggle`, {
      method: "PUT",
      credentials: "include",
    });
    if (res.ok) {
      const updated: AiTask = await res.json();
      setTasks((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
      return updated;
    }
    throw new Error("Failed to toggle task");
  };

  const submitQuestionnaire = async (axis: "systemDesign" | "coreCs", answers: Record<string, unknown>) => {
    setLoadingQuestionnaire(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/questionnaire/${axis}`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(answers),
      });
      if (res.ok) {
        const data = await res.json();
        setProfile(data);
        fetchTasks();
        fetchCanGenerateRoadmap();
        return data;
      }
      throw new Error("Failed to submit questionnaire");
    } finally {
      setLoadingQuestionnaire(false);
    }
  };

  const updateCareer = async (targetRole: string, graduationYear: number) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/career`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ targetRole, graduationYear }),
    });
    if (res.ok) {
      const data = await res.json();
      setProfile(data);
      fetchCanGenerateRoadmap();
      return data;
    }
    throw new Error("Failed to update career info");
  };

  const createGoal = async (goal: Omit<UserGoal, "id" | "status">) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/goals`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(goal),
    });
    if (res.ok) {
      await fetchGoals();
      return await res.json();
    }
    throw new Error("Failed to create goal");
  };

  const updateGoal = async (id: string, updates: Partial<UserGoal>) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/goals/${id}`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(updates),
    });
    if (res.ok) {
      await fetchGoals();
      return await res.json();
    }
    throw new Error("Failed to update goal");
  };

  const deleteGoal = async (id: string) => {
    await fetch(`${API_BASE_URL}/api/personal/goals/${id}`, {
      method: "DELETE",
      credentials: "include",
    });
    await fetchGoals();
  };

  const fetchSuggestions = useCallback(async () => {
    if (!user) return;
    setLoadingSuggestions(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/suggestions/today`, {
        credentials: "include",
      });
      if (res.ok) {
        setSuggestions(await res.json());
      }
    } finally {
      setLoadingSuggestions(false);
    }
  }, [user]);

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

  const requestInsights = async () => {
    setLoadingInsights(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/insights`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        setInsights(data.insights);
      }
    } finally {
      setLoadingInsights(false);
    }
  };

  const fetchRoadmap = useCallback(async () => {
    if (!user) { setLoadingRoadmap(false); return; }
    try {
      setLoadingRoadmap(true);
      const res = await fetch(`${API_BASE_URL}/api/personal/roadmap`, { credentials: "include" });
      if (res.ok) setRoadmap(await res.json());
    } finally {
      setLoadingRoadmap(false);
    }
  }, [user]);

  const fetchCanGenerateRoadmap = useCallback(async () => {
    if (!user) return;
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/roadmap/can-generate`, { credentials: "include" });
      if (res.ok) {
        const data = await res.json();
        setCanGenerateRoadmap(data.canGenerate);
      }
    } catch {}
  }, [user]);

  const generateRoadmap = async () => {
    setGeneratingRoadmap(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/roadmap/generate`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        setRoadmap(data);
        return data;
      }
      throw new Error("Failed to generate roadmap");
    } finally {
      setGeneratingRoadmap(false);
    }
  };

  const reorderRoadmap = async (orderedIds: string[]) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/roadmap/reorder`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ orderedIds }),
    });
    if (res.ok) {
      setRoadmap(await res.json());
    }
  };

  const updateRoadmapItemStatus = async (id: string, status: string) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/roadmap/${id}/status`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    });
    if (res.ok) {
      const updated: RoadmapItem = await res.json();
      setRoadmap((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    }
  };

  useEffect(() => {
    fetchProfile();
    fetchGoals();
    fetchHeatmap();
    fetchTasks();
    fetchSuggestions();
    fetchRoadmap();
    fetchCanGenerateRoadmap();
  }, [fetchProfile, fetchGoals, fetchHeatmap, fetchTasks, fetchSuggestions, fetchRoadmap, fetchCanGenerateRoadmap]);

  return {
    profile,
    goals,
    heatmap,
    tasks,
    insights,
    roadmap,
    loadingProfile,
    loadingGoals,
    loadingHeatmap,
    loadingTasks,
    loadingInsights,
    loadingSuggestions,
    loadingRoadmap,
    generatingRoadmap,
    canGenerateRoadmap,
    suggestions,
    loadingLeetCode,
    loadingResume,
    scoringResume,
    loadingQuestionnaire,
    updateProfile,
    completeOnboarding,
    updateCareer,
    connectLeetCode,
    refreshLeetCode,
    uploadResume,
    deleteResume,
    scoreResume,
    submitQuestionnaire,
    toggleTask,
    createGoal,
    updateGoal,
    deleteGoal,
    requestInsights,
    fetchSuggestions,
    regenerateSuggestions,
    generateRoadmap,
    reorderRoadmap,
    updateRoadmapItemStatus,
    refetch: () => { fetchProfile(); fetchGoals(); fetchHeatmap(); fetchTasks(); fetchSuggestions(); fetchRoadmap(); fetchCanGenerateRoadmap(); },
  };
}
