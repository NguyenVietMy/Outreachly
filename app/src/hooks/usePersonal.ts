import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { API_BASE_URL } from "@/lib/config";

export interface KnowledgeArea {
  area: string;
  level: number;
}

export interface AxisScores {
  dsa: number;
  projects: number;
  systemDesign: number;
  coreCs: number;
  resume: number;
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

export interface UserProfile {
  profileMarkdown: string;
  knowledgeAreas: KnowledgeArea[];
  onboardingCompleted: boolean;
  leetcodeUsername: string | null;
  leetcodeStats: LeetCodeStats | null;
  resumeFilename: string | null;
  hasResume: boolean;
  systemDesignAnswers: Record<string, unknown>;
  coreCsAnswers: Record<string, unknown>;
  axisScores: AxisScores;
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

export interface OnboardingData {
  knowledgeAreas: KnowledgeArea[];
  goals: { title: string; category: string; targetValue: number | null; unit: string }[];
  bio: string;
}

export function usePersonal() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [goals, setGoals] = useState<UserGoal[]>([]);
  const [heatmap, setHeatmap] = useState<Record<string, number>>({});
  const [insights, setInsights] = useState<string | null>(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [loadingGoals, setLoadingGoals] = useState(true);
  const [loadingHeatmap, setLoadingHeatmap] = useState(true);
  const [loadingInsights, setLoadingInsights] = useState(false);
  const [loadingLeetCode, setLoadingLeetCode] = useState(false);
  const [loadingResume, setLoadingResume] = useState(false);
  const [loadingQuestionnaire, setLoadingQuestionnaire] = useState(false);
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

  const updateProfile = async (profileMarkdown: string, knowledgeAreas: KnowledgeArea[]) => {
    const res = await fetch(`${API_BASE_URL}/api/personal/profile`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ profileMarkdown, knowledgeAreas }),
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
    }
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

  useEffect(() => {
    fetchProfile();
    fetchGoals();
    fetchHeatmap();
  }, [fetchProfile, fetchGoals, fetchHeatmap]);

  return {
    profile,
    goals,
    heatmap,
    insights,
    loadingProfile,
    loadingGoals,
    loadingHeatmap,
    loadingInsights,
    loadingLeetCode,
    loadingResume,
    loadingQuestionnaire,
    updateProfile,
    completeOnboarding,
    updateCareer,
    connectLeetCode,
    refreshLeetCode,
    uploadResume,
    deleteResume,
    submitQuestionnaire,
    createGoal,
    updateGoal,
    deleteGoal,
    requestInsights,
    refetch: () => { fetchProfile(); fetchGoals(); fetchHeatmap(); },
  };
}
