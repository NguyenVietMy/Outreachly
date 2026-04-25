"use client";

import { useState } from "react";
import AuthGuard from "@/components/AuthGuard";
import DashboardLayout from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { usePersonal, KnowledgeArea, OnboardingData } from "@/hooks/usePersonal";
import {
  Bot,
  ChevronDown,
  ChevronUp,
  Loader2,
  Plus,
  Save,
  Sparkles,
  Trash2,
  X,
} from "lucide-react";
import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
} from "recharts";

const DEFAULT_AREAS = [
  "Algorithms",
  "Data Structures",
  "Operating Systems",
  "Computer Networks",
  "Databases",
  "Systems Design",
  "Web Development",
  "Math & Discrete",
  "Machine Learning",
  "Security",
];

const GOAL_CATEGORIES = [
  { value: "leetcode", label: "Leetcode" },
  { value: "project", label: "Project" },
  { value: "study", label: "Study" },
  { value: "school", label: "School" },
  { value: "internship", label: "Internship" },
  { value: "club", label: "Club" },
  { value: "other", label: "Other" },
];

const CATEGORY_COLORS: Record<string, string> = {
  leetcode: "bg-[#d4fae8] text-[#0fa76e]",
  project: "bg-[#e7eefb] text-[#3772cf]",
  study: "bg-[#f8ebd8] text-[#c37d0d]",
  school: "bg-[#f7e5e5] text-[#d45656]",
  internship: "bg-[#e8d8f8] text-[#7c3aed]",
  club: "bg-[#d8f0f8] text-[#0d7c9c]",
  other: "bg-[#f5f5f5] text-[#666666]",
};

function OnboardingModal({
  onComplete,
}: {
  onComplete: (data: OnboardingData) => Promise<void>;
}) {
  const [step, setStep] = useState(0);
  const [areas, setAreas] = useState<KnowledgeArea[]>(
    DEFAULT_AREAS.map((a) => ({ area: a, level: 1 }))
  );
  const [goals, setGoals] = useState<
    { title: string; category: string; targetValue: number | null; unit: string }[]
  >([]);
  const [bio, setBio] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [goalDraft, setGoalDraft] = useState({ title: "", category: "leetcode", target: "", unit: "problems" });

  const updateLevel = (index: number, level: number) => {
    setAreas((prev) => prev.map((a, i) => (i === index ? { ...a, level } : a)));
  };

  const addGoal = () => {
    if (!goalDraft.title.trim()) return;
    setGoals((prev) => [
      ...prev,
      {
        title: goalDraft.title,
        category: goalDraft.category,
        targetValue: goalDraft.target ? parseInt(goalDraft.target) : null,
        unit: goalDraft.unit,
      },
    ]);
    setGoalDraft({ title: "", category: "leetcode", target: "", unit: "problems" });
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await onComplete({ knowledgeAreas: areas, goals, bio });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="mx-4 w-full max-w-[640px] rounded-[20px] bg-white p-8 shadow-xl">
        <div className="mb-6">
          <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
            Step {step + 1} of 3
          </p>
          <h2 className="mt-1 text-[24px] font-semibold leading-[1.3] tracking-[-0.24px] text-[#0d0d0d]">
            {step === 0 && "Rate your knowledge"}
            {step === 1 && "Set your goals"}
            {step === 2 && "Tell us about yourself"}
          </h2>
        </div>

        {step === 0 && (
          <div className="max-h-[400px] space-y-3 overflow-y-auto pr-2">
            {areas.map((area, i) => (
              <div key={area.area} className="flex items-center justify-between">
                <p className="text-[15px] text-[#333333]">{area.area}</p>
                <div className="flex gap-1">
                  {[1, 2, 3, 4, 5].map((level) => (
                    <button
                      key={level}
                      onClick={() => updateLevel(i, level)}
                      className={`h-8 w-8 rounded-lg text-[13px] font-medium transition-colors ${
                        area.level >= level
                          ? "bg-[#0d0d0d] text-white"
                          : "bg-[#f5f5f5] text-[#666666] hover:bg-[#e5e5e5]"
                      }`}
                    >
                      {level}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {step === 1 && (
          <div className="space-y-4">
            <div className="space-y-2">
              {goals.map((goal, i) => (
                <div
                  key={i}
                  className="flex items-center justify-between rounded-lg border border-[rgba(0,0,0,0.05)] bg-[#fafafa] px-3 py-2"
                >
                  <div>
                    <p className="text-[14px] font-medium text-[#0d0d0d]">{goal.title}</p>
                    <p className="text-[12px] text-[#666666]">
                      {goal.targetValue ? `Target: ${goal.targetValue} ${goal.unit}` : goal.category}
                    </p>
                  </div>
                  <button onClick={() => setGoals((prev) => prev.filter((_, idx) => idx !== i))}>
                    <X className="h-4 w-4 text-[#999999]" />
                  </button>
                </div>
              ))}
            </div>
            <div className="space-y-2 rounded-lg border border-[rgba(0,0,0,0.1)] p-3">
              <input
                type="text"
                placeholder="Goal title (e.g., Solve 200 Leetcode problems)"
                value={goalDraft.title}
                onChange={(e) => setGoalDraft((d) => ({ ...d, title: e.target.value }))}
                className="w-full rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none focus:border-[#0d0d0d]"
              />
              <div className="flex gap-2">
                <select
                  value={goalDraft.category}
                  onChange={(e) => setGoalDraft((d) => ({ ...d, category: e.target.value }))}
                  className="rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none"
                >
                  {GOAL_CATEGORIES.map((c) => (
                    <option key={c.value} value={c.value}>{c.label}</option>
                  ))}
                </select>
                <input
                  type="number"
                  placeholder="Target"
                  value={goalDraft.target}
                  onChange={(e) => setGoalDraft((d) => ({ ...d, target: e.target.value }))}
                  className="w-24 rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none"
                />
                <input
                  type="text"
                  placeholder="Unit"
                  value={goalDraft.unit}
                  onChange={(e) => setGoalDraft((d) => ({ ...d, unit: e.target.value }))}
                  className="w-24 rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none"
                />
                <Button onClick={addGoal} disabled={!goalDraft.title.trim()} className="h-auto rounded-lg bg-[#0d0d0d] px-3 py-2 text-white">
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        )}

        {step === 2 && (
          <textarea
            placeholder="Tell us about yourself — your year, target companies, what you're working on, strengths, areas you want to improve..."
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            rows={8}
            className="w-full rounded-lg border border-[rgba(0,0,0,0.1)] px-4 py-3 text-[15px] leading-[1.6] outline-none focus:border-[#0d0d0d]"
          />
        )}

        <div className="mt-6 flex justify-between">
          <Button
            onClick={() => setStep((s) => s - 1)}
            disabled={step === 0}
            variant="outline"
            className="h-auto rounded-full px-6 py-2 text-[15px]"
          >
            Back
          </Button>
          {step < 2 ? (
            <Button
              onClick={() => setStep((s) => s + 1)}
              className="h-auto rounded-full bg-[#0d0d0d] px-6 py-2 text-[15px] text-white"
            >
              Next
            </Button>
          ) : (
            <Button
              onClick={handleSubmit}
              disabled={submitting}
              className="h-auto rounded-full bg-primary px-6 py-2 text-[15px] text-primary-foreground"
            >
              {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Complete setup
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

function ActivityHeatmap({ data }: { data: Record<string, number> }) {
  const today = new Date();
  const days: { date: string; count: number }[] = [];
  for (let i = 89; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const key = d.toISOString().split("T")[0];
    days.push({ date: key, count: data[key] || 0 });
  }

  const maxCount = Math.max(...days.map((d) => d.count), 1);

  const getColor = (count: number) => {
    if (count === 0) return "#f5f5f5";
    const intensity = Math.min(count / maxCount, 1);
    if (intensity < 0.25) return "#d4fae8";
    if (intensity < 0.5) return "#7fe0b8";
    if (intensity < 0.75) return "#34c88a";
    return "#18a068";
  };

  const weeks: { date: string; count: number }[][] = [];
  for (let i = 0; i < days.length; i += 7) {
    weeks.push(days.slice(i, i + 7));
  }

  return (
    <div className="flex gap-1">
      {weeks.map((week, wi) => (
        <div key={wi} className="flex flex-col gap-1">
          {week.map((day) => (
            <div
              key={day.date}
              className="h-3 w-3 rounded-[2px]"
              style={{ backgroundColor: getColor(day.count) }}
              title={`${day.date}: ${day.count} events`}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

export default function PersonalPage() {
  const {
    profile,
    goals,
    heatmap,
    insights,
    loadingProfile,
    loadingGoals,
    loadingInsights,
    updateProfile,
    completeOnboarding,
    createGoal,
    updateGoal,
    deleteGoal,
    requestInsights,
  } = usePersonal();

  const [editingProfile, setEditingProfile] = useState(false);
  const [profileDraft, setProfileDraft] = useState("");
  const [knowledgeDraft, setKnowledgeDraft] = useState<KnowledgeArea[]>([]);
  const [showAddGoal, setShowAddGoal] = useState(false);
  const [newGoal, setNewGoal] = useState({ title: "", category: "leetcode", targetValue: "", unit: "items", deadline: "" });
  const [profileExpanded, setProfileExpanded] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);

  const startEditProfile = () => {
    if (profile) {
      setProfileDraft(profile.profileMarkdown);
      setKnowledgeDraft([...profile.knowledgeAreas]);
    }
    setEditingProfile(true);
  };

  const saveProfile = async () => {
    setSavingProfile(true);
    try {
      await updateProfile(profileDraft, knowledgeDraft);
      setEditingProfile(false);
    } finally {
      setSavingProfile(false);
    }
  };

  const handleAddGoal = async () => {
    await createGoal({
      title: newGoal.title,
      category: newGoal.category,
      targetValue: newGoal.targetValue ? parseInt(newGoal.targetValue) : null,
      currentValue: 0,
      unit: newGoal.unit,
      deadline: newGoal.deadline || null,
    });
    setNewGoal({ title: "", category: "leetcode", targetValue: "", unit: "items", deadline: "" });
    setShowAddGoal(false);
  };

  const radarData =
    profile?.knowledgeAreas.map((a) => ({
      area: a.area,
      level: a.level,
      fullMark: 5,
    })) || [];

  if (loadingProfile) {
    return (
      <AuthGuard>
        <DashboardLayout>
          <div className="flex min-h-[calc(100vh-64px)] items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-[#666666]" />
          </div>
        </DashboardLayout>
      </AuthGuard>
    );
  }

  return (
    <AuthGuard>
      <DashboardLayout>
        {profile && !profile.onboardingCompleted && (
          <OnboardingModal onComplete={completeOnboarding} />
        )}

        <div className="min-h-[calc(100vh-64px)] bg-[#ffffff] px-6 py-12 text-[#0d0d0d] md:px-8 md:py-16">
          <div className="mx-auto flex w-full max-w-[1200px] flex-col gap-10">
            {/* Header */}
            <section className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                  Personal
                </p>
                <h1 className="mt-2 font-mono text-[40px] font-semibold leading-[1.1] tracking-[-0.8px] text-[#0d0d0d]">
                  Your stats
                </h1>
                <p className="mt-3 max-w-2xl text-[18px] leading-[1.5] text-[#333333]">
                  Track your knowledge, set goals, and get AI-powered insights.
                </p>
              </div>
              <Button
                onClick={requestInsights}
                disabled={loadingInsights}
                className="h-auto rounded-full bg-primary px-4 py-2 text-[15px] font-medium text-primary-foreground hover:bg-primary/90"
              >
                {loadingInsights ? (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                ) : (
                  <Sparkles className="mr-2 h-4 w-4" />
                )}
                Get AI Insights
              </Button>
            </section>

            {/* Radar + Insights */}
            <section className="grid gap-6 xl:grid-cols-2">
              <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                    Knowledge areas
                  </h2>
                  {!editingProfile && (
                    <button
                      onClick={startEditProfile}
                      className="text-[14px] font-medium text-[#666666] hover:text-[#0d0d0d]"
                    >
                      Edit levels
                    </button>
                  )}
                </div>
                {radarData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={320}>
                    <RadarChart data={radarData} outerRadius="75%">
                      <PolarGrid stroke="#e5e5e5" />
                      <PolarAngleAxis
                        dataKey="area"
                        tick={{ fontSize: 11, fill: "#666666" }}
                      />
                      <PolarRadiusAxis
                        angle={90}
                        domain={[0, 5]}
                        tick={{ fontSize: 10, fill: "#999999" }}
                        tickCount={6}
                      />
                      <Radar
                        dataKey="level"
                        stroke="#18E299"
                        fill="#18E299"
                        fillOpacity={0.2}
                        strokeWidth={2}
                      />
                    </RadarChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="py-12 text-center text-[14px] text-[#666666]">
                    Complete onboarding to see your knowledge chart.
                  </p>
                )}

                {editingProfile && (
                  <div className="mt-4 space-y-2 border-t border-[rgba(0,0,0,0.05)] pt-4">
                    {knowledgeDraft.map((area, i) => (
                      <div key={area.area} className="flex items-center justify-between">
                        <p className="text-[14px] text-[#333333]">{area.area}</p>
                        <div className="flex gap-1">
                          {[1, 2, 3, 4, 5].map((level) => (
                            <button
                              key={level}
                              onClick={() =>
                                setKnowledgeDraft((prev) =>
                                  prev.map((a, idx) =>
                                    idx === i ? { ...a, level } : a
                                  )
                                )
                              }
                              className={`h-7 w-7 rounded-md text-[12px] font-medium transition-colors ${
                                area.level >= level
                                  ? "bg-[#0d0d0d] text-white"
                                  : "bg-[#f5f5f5] text-[#666666] hover:bg-[#e5e5e5]"
                              }`}
                            >
                              {level}
                            </button>
                          ))}
                        </div>
                      </div>
                    ))}
                    <div className="flex justify-end gap-2 pt-2">
                      <Button
                        variant="outline"
                        onClick={() => setEditingProfile(false)}
                        className="h-auto rounded-lg px-4 py-1.5 text-[13px]"
                      >
                        Cancel
                      </Button>
                      <Button
                        onClick={saveProfile}
                        disabled={savingProfile}
                        className="h-auto rounded-lg bg-[#0d0d0d] px-4 py-1.5 text-[13px] text-white"
                      >
                        {savingProfile && <Loader2 className="mr-2 h-3 w-3 animate-spin" />}
                        Save
                      </Button>
                    </div>
                  </div>
                )}
              </article>

              <article className="rounded-[24px] border border-[rgba(0,0,0,0.05)] bg-white p-8 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                <div className="mb-3 flex items-center gap-2 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                  <Bot className="h-4 w-4 text-[#18E299]" />
                  AI Insights
                </div>
                {loadingInsights ? (
                  <div className="space-y-2">
                    <div className="h-4 w-full animate-pulse rounded bg-[#f5f5f5]" />
                    <div className="h-4 w-3/4 animate-pulse rounded bg-[#f5f5f5]" />
                    <div className="h-4 w-5/6 animate-pulse rounded bg-[#f5f5f5]" />
                  </div>
                ) : insights ? (
                  <ol className="space-y-3">
                    {insights
                      .split("\n")
                      .filter((line) => line.trim().length > 0)
                      .map((line, i) => (
                        <li
                          key={i}
                          className="flex items-start gap-3 text-[15px] leading-[1.6] text-[#333333]"
                        >
                          <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[#d4fae8] font-mono text-[11px] font-semibold text-[#0fa76e]">
                            {i + 1}
                          </span>
                          <span>{line.replace(/^\d+\.\s*/, "")}</span>
                        </li>
                      ))}
                  </ol>
                ) : (
                  <p className="text-[16px] leading-[1.5] text-[#999999]">
                    Click &quot;Get AI Insights&quot; to receive personalized study and career advice.
                  </p>
                )}
              </article>
            </section>

            {/* Goals */}
            <section>
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                  Goals
                </h2>
                <Button
                  onClick={() => setShowAddGoal(true)}
                  className="h-auto rounded-full bg-[#0d0d0d] px-4 py-1.5 text-[13px] text-white"
                >
                  <Plus className="mr-1.5 h-3.5 w-3.5" />
                  Add goal
                </Button>
              </div>

              {showAddGoal && (
                <div className="mb-4 rounded-[16px] border border-[rgba(0,0,0,0.1)] bg-white p-5 shadow-sm">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <input
                      type="text"
                      placeholder="Goal title"
                      value={newGoal.title}
                      onChange={(e) => setNewGoal((g) => ({ ...g, title: e.target.value }))}
                      className="col-span-full rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none focus:border-[#0d0d0d]"
                    />
                    <select
                      value={newGoal.category}
                      onChange={(e) => setNewGoal((g) => ({ ...g, category: e.target.value }))}
                      className="rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none"
                    >
                      {GOAL_CATEGORIES.map((c) => (
                        <option key={c.value} value={c.value}>{c.label}</option>
                      ))}
                    </select>
                    <div className="flex gap-2">
                      <input
                        type="number"
                        placeholder="Target"
                        value={newGoal.targetValue}
                        onChange={(e) => setNewGoal((g) => ({ ...g, targetValue: e.target.value }))}
                        className="w-24 rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none"
                      />
                      <input
                        type="text"
                        placeholder="Unit"
                        value={newGoal.unit}
                        onChange={(e) => setNewGoal((g) => ({ ...g, unit: e.target.value }))}
                        className="flex-1 rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none"
                      />
                    </div>
                    <input
                      type="date"
                      value={newGoal.deadline}
                      onChange={(e) => setNewGoal((g) => ({ ...g, deadline: e.target.value }))}
                      className="rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none"
                    />
                  </div>
                  <div className="mt-3 flex justify-end gap-2">
                    <Button variant="outline" onClick={() => setShowAddGoal(false)} className="h-auto rounded-lg px-4 py-1.5 text-[13px]">
                      Cancel
                    </Button>
                    <Button onClick={handleAddGoal} disabled={!newGoal.title.trim()} className="h-auto rounded-lg bg-[#0d0d0d] px-4 py-1.5 text-[13px] text-white">
                      Add
                    </Button>
                  </div>
                </div>
              )}

              <div className="grid gap-3 md:grid-cols-2">
                {loadingGoals ? (
                  Array.from({ length: 4 }).map((_, i) => (
                    <div key={i} className="h-24 animate-pulse rounded-[16px] bg-[#f5f5f5]" />
                  ))
                ) : goals.length === 0 ? (
                  <p className="col-span-full py-8 text-center text-[14px] text-[#666666]">
                    No goals yet. Add one to start tracking your progress.
                  </p>
                ) : (
                  goals.map((goal) => {
                    const pct =
                      goal.targetValue && goal.targetValue > 0
                        ? Math.min((goal.currentValue / goal.targetValue) * 100, 100)
                        : 0;
                    const catColor = CATEGORY_COLORS[goal.category] || CATEGORY_COLORS.other;

                    return (
                      <article
                        key={goal.id}
                        className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]"
                      >
                        <div className="flex items-start justify-between">
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <h3 className="truncate text-[16px] font-medium text-[#0d0d0d]">
                                {goal.title}
                              </h3>
                              <span
                                className={`shrink-0 rounded-full px-2 py-0.5 font-mono text-[10px] font-semibold uppercase ${catColor}`}
                              >
                                {goal.category}
                              </span>
                            </div>
                            {goal.targetValue ? (
                              <div className="mt-3">
                                <div className="flex items-center justify-between text-[13px] text-[#666666]">
                                  <span>
                                    {goal.currentValue} / {goal.targetValue} {goal.unit}
                                  </span>
                                  <span>{Math.round(pct)}%</span>
                                </div>
                                <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-[#f5f5f5]">
                                  <div
                                    className="h-full rounded-full bg-[#18E299] transition-all duration-500"
                                    style={{ width: `${pct}%` }}
                                  />
                                </div>
                              </div>
                            ) : (
                              <p className="mt-2 text-[13px] text-[#666666]">No target set</p>
                            )}
                            {goal.deadline && (
                              <p className="mt-2 text-[12px] text-[#999999]">
                                Deadline: {goal.deadline}
                              </p>
                            )}
                          </div>
                          <div className="ml-3 flex shrink-0 gap-1">
                            {goal.targetValue && (
                              <button
                                onClick={() =>
                                  updateGoal(goal.id, {
                                    currentValue: goal.currentValue + 1,
                                  })
                                }
                                className="rounded-lg p-1.5 text-[#666666] hover:bg-[#f5f5f5] hover:text-[#0d0d0d]"
                                title="Increment"
                              >
                                <Plus className="h-4 w-4" />
                              </button>
                            )}
                            <button
                              onClick={() => deleteGoal(goal.id)}
                              className="rounded-lg p-1.5 text-[#666666] hover:bg-[#f5f5f5] hover:text-[#d45656]"
                              title="Delete"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
                        </div>
                      </article>
                    );
                  })
                )}
              </div>
            </section>

            {/* Heatmap */}
            <section className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
              <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                Activity (last 90 days)
              </h2>
              <div className="overflow-x-auto">
                <ActivityHeatmap data={heatmap} />
              </div>
              <div className="mt-3 flex items-center gap-2 text-[12px] text-[#999999]">
                <span>Less</span>
                {["#f5f5f5", "#d4fae8", "#7fe0b8", "#34c88a", "#18a068"].map((c) => (
                  <div
                    key={c}
                    className="h-3 w-3 rounded-[2px]"
                    style={{ backgroundColor: c }}
                  />
                ))}
                <span>More</span>
              </div>
            </section>

            {/* Profile markdown */}
            <section className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
              <button
                onClick={() => setProfileExpanded(!profileExpanded)}
                className="flex w-full items-center justify-between px-6 py-5"
              >
                <h2 className="text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                  Profile
                </h2>
                {profileExpanded ? (
                  <ChevronUp className="h-5 w-5 text-[#666666]" />
                ) : (
                  <ChevronDown className="h-5 w-5 text-[#666666]" />
                )}
              </button>
              {profileExpanded && (
                <div className="border-t border-[rgba(0,0,0,0.05)] px-6 pb-6 pt-4">
                  {editingProfile ? (
                    <>
                      <textarea
                        value={profileDraft}
                        onChange={(e) => setProfileDraft(e.target.value)}
                        rows={16}
                        className="w-full rounded-lg border border-[rgba(0,0,0,0.1)] px-4 py-3 font-mono text-[14px] leading-[1.7] outline-none focus:border-[#0d0d0d]"
                      />
                      <div className="mt-3 flex justify-end gap-2">
                        <Button variant="outline" onClick={() => setEditingProfile(false)} className="h-auto rounded-lg px-4 py-1.5 text-[13px]">
                          Cancel
                        </Button>
                        <Button onClick={saveProfile} disabled={savingProfile} className="h-auto rounded-lg bg-[#0d0d0d] px-4 py-1.5 text-[13px] text-white">
                          {savingProfile && <Loader2 className="mr-2 h-3 w-3 animate-spin" />}
                          <Save className="mr-1.5 h-3.5 w-3.5" />
                          Save
                        </Button>
                      </div>
                    </>
                  ) : (
                    <>
                      <pre className="whitespace-pre-wrap font-mono text-[14px] leading-[1.7] text-[#333333]">
                        {profile?.profileMarkdown || "No profile yet. Complete onboarding to get started."}
                      </pre>
                      <button
                        onClick={startEditProfile}
                        className="mt-4 text-[14px] font-medium text-[#666666] hover:text-[#0d0d0d]"
                      >
                        Edit profile
                      </button>
                    </>
                  )}
                </div>
              )}
            </section>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
