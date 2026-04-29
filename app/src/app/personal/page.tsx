"use client";

import { useEffect, useRef, useState } from "react";
import AuthGuard from "@/components/AuthGuard";
import DashboardLayout from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  usePersonal,
  KnowledgeArea,
  OnboardingData,
  AxisAssessmentPayload,
  SectionAssessment,
  ResumeScoreBreakdown,
  ResumeSubScore,
  AiTask,
} from "@/hooks/usePersonal";
import MilestoneAssessmentModal from "@/components/assessment/MilestoneAssessmentModal";
import {
  CS_FUNDAMENTALS_SECTIONS,
  SYSTEM_DESIGN_SECTIONS,
  SECTION_COUNTS,
} from "@/data/assessmentContent";
import {
  Bot,
  FileText,
  Loader2,
  RefreshCw,
  Sparkles,
  Trash2,
  Upload,
  GraduationCap,
  Briefcase,
  Target,
} from "lucide-react";
import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
} from "recharts";

// --- Level Logic ---

function computeLevel(graduationYear: number): string {
  const now = new Date();
  const month = now.getMonth(); // 0-indexed
  const year = now.getFullYear();
  const academicStartYear = month >= 7 ? year : year - 1; // Aug = new academic year
  const yearsUntilGrad = graduationYear - academicStartYear - 1;
  if (yearsUntilGrad >= 4) return "Freshman";
  if (yearsUntilGrad === 3) return "Freshman";
  if (yearsUntilGrad === 2) return "Sophomore";
  if (yearsUntilGrad === 1) return "Junior";
  if (yearsUntilGrad === 0) return "Senior";
  return "New Grad";
}

// --- Constants ---

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

function isNewFormatAssessment(
  data: AxisAssessmentPayload | Record<string, unknown> | null,
): data is AxisAssessmentPayload {
  return !!data && "sections" in data && "axisId" in data;
}

function getAssessmentProgress(
  answers: AxisAssessmentPayload | Record<string, unknown> | null,
  axis: "systemDesign" | "coreCs",
): { completed: number; total: number } | null {
  if (!isNewFormatAssessment(answers)) return null;
  return {
    completed: answers.sections.length,
    total: SECTION_COUNTS[axis],
  };
}

// --- Onboarding Modal ---

function OnboardingModal({
  onComplete,
}: {
  onComplete: (data: OnboardingData) => Promise<void>;
}) {
  const [step, setStep] = useState(0);
  const [areas, setAreas] = useState<KnowledgeArea[]>(
    DEFAULT_AREAS.map((a) => ({ area: a, level: 1 })),
  );
  const [bio, setBio] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const updateLevel = (index: number, level: number) => {
    setAreas((prev) => prev.map((a, i) => (i === index ? { ...a, level } : a)));
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await onComplete({ knowledgeAreas: areas, goals: [], bio });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="mx-4 w-full max-w-[640px] rounded-[20px] bg-white p-8 shadow-xl">
        <div className="mb-6">
          <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
            Step {step + 1} of 2
          </p>
          <h2 className="mt-1 text-[24px] font-semibold leading-[1.3] tracking-[-0.24px] text-[#0d0d0d]">
            {step === 0 && "Rate your knowledge"}
            {step === 1 && "Tell us about yourself"}
          </h2>
        </div>

        {step === 0 && (
          <div className="max-h-[400px] space-y-3 overflow-y-auto pr-2">
            {areas.map((area, i) => (
              <div
                key={area.area}
                className="flex items-center justify-between"
              >
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
          {step < 1 ? (
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

// --- Axis Card ---

function AxisCard({
  label,
  score,
  description,
  connected,
  children,
}: {
  label: string;
  score: number;
  description: string;
  connected: boolean;
  children?: React.ReactNode;
}) {
  const getScoreColor = (s: number) => {
    if (s >= 70) return "text-[#0fa76e]";
    if (s >= 40) return "text-[#c37d0d]";
    if (s > 0) return "text-[#d45656]";
    return "text-[#999]";
  };

  return (
    <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
      <div className="flex items-start justify-between">
        <div>
          <h3 className="text-[16px] font-semibold text-[#0d0d0d]">{label}</h3>
          <p className="mt-0.5 text-[13px] text-[#666]">{description}</p>
        </div>
        <div className="text-right">
          <p
            className={`text-[28px] font-bold leading-none ${getScoreColor(score)}`}
          >
            {connected ? score : "—"}
          </p>
          {connected && <p className="mt-0.5 text-[11px] text-[#999]">/ 100</p>}
        </div>
      </div>
      {children && (
        <div className="mt-4 border-t border-[rgba(0,0,0,0.05)] pt-4">
          {children}
        </div>
      )}
    </article>
  );
}

// --- Resume Breakdown ---

function ScoreBar({ label, sub }: { label: string; sub?: ResumeSubScore }) {
  if (!sub) return null;
  const pct = sub.max > 0 ? (sub.score / sub.max) * 100 : 0;
  const color = pct >= 70 ? "#0fa76e" : pct >= 40 ? "#c37d0d" : "#d45656";
  return (
    <div className="group relative">
      <div className="flex items-center justify-between text-[12px]">
        <span className="text-[#555]">{label}</span>
        <span className="font-medium" style={{ color }}>
          {sub.score}/{sub.max}
        </span>
      </div>
      <div className="mt-1 h-1.5 w-full rounded-full bg-[#f0f0f0]">
        <div
          className="h-full rounded-full transition-all"
          style={{ width: `${pct}%`, backgroundColor: color }}
        />
      </div>
      {sub.rationale && (
        <div className="pointer-events-none absolute bottom-full left-0 z-10 mb-2 hidden w-64 rounded-lg border border-[rgba(0,0,0,0.08)] bg-white p-2.5 text-[11px] leading-[1.5] text-[#555] shadow-lg group-hover:block">
          {sub.rationale}
        </div>
      )}
    </div>
  );
}

function ResumeBreakdownView({
  breakdown,
}: {
  breakdown: ResumeScoreBreakdown | null;
}) {
  if (!breakdown || !breakdown.sections) {
    return (
      <p className="text-[12px] text-[#999]">
        Upload a resume to get AI-powered scoring.
      </p>
    );
  }

  const s = breakdown.sections;
  const decisionColor =
    breakdown.decision === "ADVANCE"
      ? "bg-[#d4fae8] text-[#0fa76e]"
      : breakdown.decision === "HOLD"
        ? "bg-[#f8ebd8] text-[#c37d0d]"
        : "bg-[#f7e5e5] text-[#d45656]";

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span
            className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${decisionColor}`}
          >
            {breakdown.decision}
          </span>
          <span className="text-[12px] text-[#999]">
            {breakdown.total_score}/{breakdown.max_score}
          </span>
        </div>
        {breakdown.flags && breakdown.flags.length > 0 && (
          <div className="flex gap-1">
            {breakdown.flags.map((f) => (
              <span
                key={f}
                className="rounded bg-[#f5f5f5] px-1.5 py-0.5 text-[10px] text-[#666]"
              >
                {f}
              </span>
            ))}
          </div>
        )}
      </div>

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">
          Technical Skills
        </p>
        <ScoreBar
          label="Languages"
          sub={s.technical_skills?.primary_language}
        />
        <ScoreBar
          label="Frameworks"
          sub={s.technical_skills?.backend_frameworks}
        />
        <ScoreBar label="Data Layer" sub={s.technical_skills?.data_layer} />
        <ScoreBar label="Infra/DevOps" sub={s.technical_skills?.infra_devops} />
      </div>

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">
          Experience
        </p>
        <ScoreBar label="Years" sub={s.experience?.years} />
        <ScoreBar label="Progression" sub={s.experience?.progression} />
        <ScoreBar label="Recency" sub={s.experience?.recency} />
      </div>

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">
          Impact
        </p>
        <ScoreBar
          label="Quantified Outcomes"
          sub={s.impact?.quantified_outcomes}
        />
        <ScoreBar label="Scope" sub={s.impact?.scope} />
      </div>

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">
          Education
        </p>
        <ScoreBar label="Degree" sub={s.education?.degree} />
        <ScoreBar label="Certifications" sub={s.education?.certifications} />
      </div>

      <ScoreBar label="Project Complexity" sub={s.project_complexity} />

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">
          Resume Quality
        </p>
        <ScoreBar label="Parsability" sub={s.resume_quality?.parsability} />
        <ScoreBar label="Conciseness" sub={s.resume_quality?.conciseness} />
      </div>

      {breakdown.summary && (
        <p className="border-t border-[rgba(0,0,0,0.05)] pt-2 text-[12px] leading-[1.5] text-[#555]">
          {breakdown.summary}
        </p>
      )}
    </div>
  );
}

// --- Main Page ---

export default function PersonalPage() {
  const {
    profile,
    tasks,
    insights,
    loadingProfile,
    loadingInsights,
    loadingLeetCode,
    loadingResume,
    scoringResume,
    completeOnboarding,
    updateCareer,
    connectLeetCode,
    refreshLeetCode,
    uploadResume,
    deleteResume,
    scoreResume,
    submitQuestionnaire,
    toggleTask,
    requestInsights,
  } = usePersonal();

  const [lcUsername, setLcUsername] = useState("");
  const [lcError, setLcError] = useState("");
  const [showSDAssessment, setShowSDAssessment] = useState(false);
  const [showCSAssessment, setShowCSAssessment] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const leftScrollRef = useRef<HTMLDivElement>(null);
  const rightScrollRef = useRef<HTMLDivElement>(null);
  const [showLeftFade, setShowLeftFade] = useState(true);
  const [showRightFade, setShowRightFade] = useState(true);

  // Career editing
  const [editingCareer, setEditingCareer] = useState(false);
  const [careerDraft, setCareerDraft] = useState({
    targetRole: "swe_intern",
    graduationYear: new Date().getFullYear() + 2,
  });
  const [savingCareer, setSavingCareer] = useState(false);

  const handleSaveCareer = async () => {
    setSavingCareer(true);
    try {
      await updateCareer(careerDraft.targetRole, careerDraft.graduationYear);
      setEditingCareer(false);
    } finally {
      setSavingCareer(false);
    }
  };

  const handleConnectLC = async () => {
    if (!lcUsername.trim()) return;
    setLcError("");
    try {
      await connectLeetCode(lcUsername.trim());
      setLcUsername("");
    } catch (e) {
      setLcError(e instanceof Error ? e.message : "Failed to connect");
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      await uploadResume(file);
    } catch {
      // handled by hook
    }
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const axes = profile?.axisScores;
  const radarData = axes
    ? [
        { axis: "DSA", score: axes.dsa, fullMark: 100 },
        { axis: "Projects", score: axes.projects, fullMark: 100 },
        { axis: "Sys Design", score: axes.systemDesign, fullMark: 100 },
        { axis: "Core CS", score: axes.coreCs, fullMark: 100 },
        { axis: "Resume", score: axes.resume, fullMark: 100 },
      ]
    : [];

  const level = profile?.graduationYear
    ? computeLevel(profile.graduationYear)
    : null;

  const updateFadeState = (
    el: HTMLDivElement | null,
    setVisible: (visible: boolean) => void,
  ) => {
    if (!el) return;
    setVisible(el.scrollTop + el.clientHeight < el.scrollHeight - 6);
  };

  useEffect(() => {
    const syncFadeState = () => {
      updateFadeState(leftScrollRef.current, setShowLeftFade);
      updateFadeState(rightScrollRef.current, setShowRightFade);
    };

    syncFadeState();
    window.addEventListener("resize", syncFadeState);
    return () => window.removeEventListener("resize", syncFadeState);
  }, [profile, tasks, insights, loadingInsights]);

  if (loadingProfile) {
    return (
      <AuthGuard>
        <DashboardLayout>
          <div className="flex min-h-full items-center justify-center">
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
        {showSDAssessment && (
          <MilestoneAssessmentModal
            axisId="systemDesign"
            title="System Design Assessment"
            sections={SYSTEM_DESIGN_SECTIONS}
            existingAssessment={profile?.systemDesignAnswers ?? null}
            onSectionComplete={(section: SectionAssessment) =>
              submitQuestionnaire(
                "systemDesign",
                section as unknown as Record<string, unknown>,
              )
            }
            onComplete={() => {}}
            onClose={() => setShowSDAssessment(false)}
          />
        )}
        {showCSAssessment && (
          <MilestoneAssessmentModal
            axisId="coreCs"
            title="CS Fundamentals Assessment"
            sections={CS_FUNDAMENTALS_SECTIONS}
            existingAssessment={profile?.coreCsAnswers ?? null}
            onSectionComplete={(section: SectionAssessment) =>
              submitQuestionnaire(
                "coreCs",
                section as unknown as Record<string, unknown>,
              )
            }
            onComplete={() => {}}
            onClose={() => setShowCSAssessment(false)}
          />
        )}

        <div className="min-h-full bg-transparent px-6 pt-10 pb-6 text-[#0d0d0d] md:px-8 md:pt-12 md:pb-8 lg:h-full lg:pb-3">
          <div className="flex w-full flex-col gap-10 lg:h-full">
            {/* Header */}
            <section>
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
                    SWE Readiness
                  </p>
                  <h1 className="mt-2 font-mono text-[40px] font-semibold leading-[1.1] tracking-[-0.8px] text-[#0d0d0d]">
                    Personal
                  </h1>
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
              </div>
            </section>

            <section className="grid gap-6 lg:min-h-0 lg:flex-1 lg:grid-cols-[minmax(0,1.75fr)_minmax(320px,1fr)] lg:items-stretch">
              <div className="flex min-h-0 flex-col">
                <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                  Your Stats
                </h2>
                <div className="relative min-h-0 lg:flex-1">
                  <div
                    ref={leftScrollRef}
                    onScroll={(e) =>
                      updateFadeState(
                        e.currentTarget,
                        setShowLeftFade,
                      )
                    }
                    className="min-h-0 space-y-10 lg:h-full lg:overflow-y-auto lg:[scrollbar-width:none] lg:[-ms-overflow-style:none] lg:[&::-webkit-scrollbar]:hidden"
                  >
                    {/* 5-Axis Radar + AI Insights */}
                    <section>
                      <div className="grid gap-6 lg:grid-cols-2">
                        <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                            <div className="flex items-center gap-2">
                              {editingCareer ? (
                                <div className="flex flex-wrap items-center justify-end gap-2">
                                  <div className="flex items-center gap-2 rounded-full border border-[rgba(0,0,0,0.08)] bg-[#fafafa] p-1 shadow-[inset_0_1px_0_rgba(255,255,255,0.7)]">
                                    <Select
                                      value={careerDraft.targetRole}
                                      onValueChange={(value) =>
                                        setCareerDraft((d) => ({
                                          ...d,
                                          targetRole: value,
                                        }))
                                      }
                                    >
                                      <SelectTrigger className="h-9 w-[148px] rounded-full border-0 bg-white px-3 text-[13px] font-medium text-[#333] shadow-none ring-0 focus:ring-0">
                                        <div className="flex items-center gap-2">
                                          {careerDraft.targetRole ===
                                          "swe_job" ? (
                                            <Briefcase className="h-3.5 w-3.5 text-[#3772cf]" />
                                          ) : (
                                            <Target className="h-3.5 w-3.5 text-[#0fa76e]" />
                                          )}
                                          <SelectValue />
                                        </div>
                                      </SelectTrigger>
                                      <SelectContent className="rounded-2xl border-[rgba(0,0,0,0.08)] bg-white p-1.5 shadow-[0_16px_40px_rgba(0,0,0,0.08)]">
                                        <SelectItem
                                          value="swe_intern"
                                          className="rounded-xl px-3 py-2 text-[13px] font-medium text-[#333]"
                                        >
                                          SWE Intern
                                        </SelectItem>
                                        <SelectItem
                                          value="swe_job"
                                          className="rounded-xl px-3 py-2 text-[13px] font-medium text-[#333]"
                                        >
                                          SWE Job
                                        </SelectItem>
                                      </SelectContent>
                                    </Select>
                                    <div className="flex items-center gap-2 rounded-full bg-white px-3 py-2">
                                      <GraduationCap className="h-3.5 w-3.5 text-[#666]" />
                                      <Input
                                        type="number"
                                        value={careerDraft.graduationYear}
                                        onChange={(e) =>
                                          setCareerDraft((d) => ({
                                            ...d,
                                            graduationYear:
                                              parseInt(e.target.value) || 2028,
                                          }))
                                        }
                                        className="h-auto w-[58px] rounded-none border-0 bg-transparent px-0 py-0 text-[13px] font-medium text-[#333] shadow-none focus-visible:ring-0"
                                        min={2024}
                                        max={2032}
                                      />
                                    </div>
                                  </div>
                                  <div className="flex items-center gap-2">
                                    <Button
                                      onClick={handleSaveCareer}
                                      disabled={savingCareer}
                                      className="h-9 rounded-full bg-[#0d0d0d] px-4 text-[12px] font-medium text-white"
                                    >
                                      {savingCareer && (
                                        <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                                      )}
                                      Save
                                    </Button>
                                    <button
                                      onClick={() => setEditingCareer(false)}
                                      className="text-[12px] font-medium text-[#888] hover:text-[#0d0d0d]"
                                    >
                                      Cancel
                                    </button>
                                  </div>
                                </div>
                              ) : profile?.targetRole ? (
                                <button
                                  onClick={() => {
                                    setCareerDraft({
                                      targetRole:
                                        profile.targetRole || "swe_intern",
                                      graduationYear:
                                        profile.graduationYear ||
                                        new Date().getFullYear() + 2,
                                    });
                                    setEditingCareer(true);
                                  }}
                                  className="flex items-center gap-2 rounded-full border border-[rgba(0,0,0,0.08)] bg-[#fafafa] px-3 py-1.5 text-[13px] font-medium text-[#333] transition-colors hover:border-[rgba(0,0,0,0.15)] hover:bg-[#f0f0f0]"
                                >
                                  {profile.targetRole === "swe_job" ? (
                                    <Briefcase className="h-3.5 w-3.5 text-[#3772cf]" />
                                  ) : (
                                    <Target className="h-3.5 w-3.5 text-[#0fa76e]" />
                                  )}
                                  {profile.targetRole === "swe_job"
                                    ? "SWE Job"
                                    : "SWE Intern"}
                                  {profile.graduationYear && (
                                    <>
                                      <span className="text-[#ccc]">
                                        &middot;
                                      </span>
                                      <span className="text-[#666]">
                                        {profile.graduationYear}
                                      </span>
                                    </>
                                  )}
                                  {level && (
                                    <>
                                      <span className="text-[#ccc]">
                                        &middot;
                                      </span>
                                      <span
                                        className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                                          level === "Freshman"
                                            ? "bg-[#e8d8f8] text-[#7c3aed]"
                                            : level === "Sophomore"
                                              ? "bg-[#d4fae8] text-[#0fa76e]"
                                              : level === "Junior"
                                                ? "bg-[#f8ebd8] text-[#c37d0d]"
                                                : level === "Senior"
                                                  ? "bg-[#e7eefb] text-[#3772cf]"
                                                  : "bg-[#f5f5f5] text-[#666]"
                                        }`}
                                      >
                                        {level}
                                      </span>
                                    </>
                                  )}
                                </button>
                              ) : (
                                <Select
                                  onValueChange={(role) => {
                                    setCareerDraft({
                                      targetRole: role,
                                      graduationYear:
                                        new Date().getFullYear() + 2,
                                    });
                                    setEditingCareer(true);
                                  }}
                                >
                                  <SelectTrigger className="h-10 w-[168px] rounded-full border-[rgba(0,0,0,0.08)] bg-[#fafafa] px-4 text-[13px] font-medium text-[#666] shadow-none transition-colors hover:border-[rgba(0,0,0,0.15)] hover:bg-[#f5f5f5] focus:ring-0">
                                    <div className="flex items-center gap-2">
                                      <Target className="h-3.5 w-3.5 text-[#999]" />
                                      <SelectValue placeholder="Select Goal" />
                                    </div>
                                  </SelectTrigger>
                                  <SelectContent className="rounded-2xl border-[rgba(0,0,0,0.08)] bg-white p-1.5 shadow-[0_16px_40px_rgba(0,0,0,0.08)]">
                                    <SelectItem
                                      value="swe_intern"
                                      className="rounded-xl px-3 py-2 text-[13px] font-medium text-[#333]"
                                    >
                                      SWE Intern
                                    </SelectItem>
                                    <SelectItem
                                      value="swe_job"
                                      className="rounded-xl px-3 py-2 text-[13px] font-medium text-[#333]"
                                    >
                                      SWE Job
                                    </SelectItem>
                                  </SelectContent>
                                </Select>
                              )}
                            </div>
                          </div>
                          {radarData.length > 0 ? (
                            <div className="[&_.recharts-surface:focus]:outline-none [&_svg:focus]:outline-none">
                              <ResponsiveContainer width="100%" height={320}>
                                <RadarChart data={radarData} outerRadius="75%">
                                  <PolarGrid stroke="#e5e5e5" />
                                  <PolarAngleAxis
                                    dataKey="axis"
                                    tick={{ fontSize: 12, fill: "#333" }}
                                  />
                                  <PolarRadiusAxis
                                    angle={90}
                                    domain={[0, 100]}
                                    tick={{ fontSize: 10, fill: "#999" }}
                                    tickCount={5}
                                  />
                                  <Radar
                                    dataKey="score"
                                    stroke="#18E299"
                                    fill="#18E299"
                                    fillOpacity={0.2}
                                    strokeWidth={2}
                                  />
                                </RadarChart>
                              </ResponsiveContainer>
                            </div>
                          ) : (
                            <p className="py-12 text-center text-[14px] text-[#666666]">
                              Complete assessments to see your radar chart.
                            </p>
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
                              Click &quot;Get AI Insights&quot; for personalized
                              SWE career advice based on your scores.
                            </p>
                          )}
                        </article>
                      </div>
                    </section>

                    {/* Assessment Axes */}
                    <section>
                      <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                        Assessment Axes
                      </h2>
                      <div className="grid gap-4 md:grid-cols-2">
                        {/* DSA */}
                        <AxisCard
                          label="DSA"
                          score={axes?.dsa ?? 0}
                          description="LeetCode solved count, difficulty mix"
                          connected={!!profile?.leetcodeUsername}
                        >
                          {profile?.leetcodeUsername ? (
                            <div>
                              <div className="flex items-center justify-between">
                                <p className="text-[13px] text-[#666]">
                                  Connected as{" "}
                                  <span className="font-medium text-[#0d0d0d]">
                                    {profile.leetcodeUsername}
                                  </span>
                                </p>
                                <button
                                  onClick={refreshLeetCode}
                                  disabled={loadingLeetCode}
                                  className="text-[#666] hover:text-[#0d0d0d]"
                                >
                                  <RefreshCw
                                    className={`h-4 w-4 ${loadingLeetCode ? "animate-spin" : ""}`}
                                  />
                                </button>
                              </div>
                              {profile.leetcodeStats && (
                                <div className="mt-3 grid grid-cols-4 gap-2">
                                  <div className="rounded-lg bg-[#f5f5f5] p-2 text-center">
                                    <p className="text-[18px] font-bold text-[#0d0d0d]">
                                      {profile.leetcodeStats.total}
                                    </p>
                                    <p className="text-[10px] text-[#999]">
                                      Total
                                    </p>
                                  </div>
                                  <div className="rounded-lg bg-[#d4fae8] p-2 text-center">
                                    <p className="text-[18px] font-bold text-[#0fa76e]">
                                      {profile.leetcodeStats.easy}
                                    </p>
                                    <p className="text-[10px] text-[#0fa76e]">
                                      Easy
                                    </p>
                                  </div>
                                  <div className="rounded-lg bg-[#f8ebd8] p-2 text-center">
                                    <p className="text-[18px] font-bold text-[#c37d0d]">
                                      {profile.leetcodeStats.medium}
                                    </p>
                                    <p className="text-[10px] text-[#c37d0d]">
                                      Med
                                    </p>
                                  </div>
                                  <div className="rounded-lg bg-[#f7e5e5] p-2 text-center">
                                    <p className="text-[18px] font-bold text-[#d45656]">
                                      {profile.leetcodeStats.hard}
                                    </p>
                                    <p className="text-[10px] text-[#d45656]">
                                      Hard
                                    </p>
                                  </div>
                                </div>
                              )}
                            </div>
                          ) : (
                            <div>
                              <div className="flex gap-2">
                                <input
                                  type="text"
                                  placeholder="LeetCode username"
                                  value={lcUsername}
                                  onChange={(e) =>
                                    setLcUsername(e.target.value)
                                  }
                                  onKeyDown={(e) =>
                                    e.key === "Enter" && handleConnectLC()
                                  }
                                  className="flex-1 rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none focus:border-[#0d0d0d]"
                                />
                                <Button
                                  onClick={handleConnectLC}
                                  disabled={
                                    loadingLeetCode || !lcUsername.trim()
                                  }
                                  className="h-auto rounded-lg bg-[#0d0d0d] px-4 py-2 text-[13px] text-white"
                                >
                                  {loadingLeetCode ? (
                                    <Loader2 className="h-4 w-4 animate-spin" />
                                  ) : (
                                    "Connect"
                                  )}
                                </Button>
                              </div>
                              {lcError && (
                                <p className="mt-2 text-[13px] text-[#d45656]">
                                  {lcError}
                                </p>
                              )}
                            </div>
                          )}
                        </AxisCard>

                        {/* Projects */}
                        <AxisCard
                          label="Projects"
                          score={axes?.projects ?? 0}
                          description="From resume — deployed status, tech depth, impact"
                          connected={(axes?.projects ?? 0) > 0}
                        >
                          <p className="text-[13px] text-[#999]">
                            Upload your resume below to auto-extract project
                            data. LLM scoring coming soon.
                          </p>
                        </AxisCard>

                        {/* System Design */}
                        <AxisCard
                          label="System Design"
                          score={axes?.systemDesign ?? 0}
                          description="APIs, scaling, caching, cloud, tradeoffs"
                          connected={(axes?.systemDesign ?? 0) > 0}
                        >
                          {(() => {
                            const progress = getAssessmentProgress(
                              profile?.systemDesignAnswers ?? null,
                              "systemDesign",
                            );
                            return (
                              <>
                                {progress && (
                                  <p className="mb-2 text-[12px] text-[#999]">
                                    {progress.completed} of {progress.total}{" "}
                                    sections complete
                                  </p>
                                )}
                                <Button
                                  onClick={() => setShowSDAssessment(true)}
                                  variant="outline"
                                  className="h-auto w-full rounded-lg px-4 py-2 text-[13px]"
                                >
                                  {progress && progress.completed > 0
                                    ? progress.completed >= progress.total
                                      ? "Retake assessment"
                                      : "Continue assessment"
                                    : (axes?.systemDesign ?? 0) > 0
                                      ? "Retake assessment"
                                      : "Take assessment"}
                                </Button>
                              </>
                            );
                          })()}
                        </AxisCard>

                        {/* Core CS */}
                        <AxisCard
                          label="Core CS"
                          score={axes?.coreCs ?? 0}
                          description="OS, networking, concurrency, DB internals"
                          connected={(axes?.coreCs ?? 0) > 0}
                        >
                          {(() => {
                            const progress = getAssessmentProgress(
                              profile?.coreCsAnswers ?? null,
                              "coreCs",
                            );
                            return (
                              <>
                                {progress && (
                                  <p className="mb-2 text-[12px] text-[#999]">
                                    {progress.completed} of {progress.total}{" "}
                                    sections complete
                                  </p>
                                )}
                                <Button
                                  onClick={() => setShowCSAssessment(true)}
                                  variant="outline"
                                  className="h-auto w-full rounded-lg px-4 py-2 text-[13px]"
                                >
                                  {progress && progress.completed > 0
                                    ? progress.completed >= progress.total
                                      ? "Retake assessment"
                                      : "Continue assessment"
                                    : (axes?.coreCs ?? 0) > 0
                                      ? "Retake assessment"
                                      : "Take assessment"}
                                </Button>
                              </>
                            );
                          })()}
                        </AxisCard>

                        {/* Resume */}
                        <AxisCard
                          label="Resume"
                          score={axes?.resume ?? 0}
                          description="Formatting, bullet quality, metrics, ATS readability"
                          connected={!!profile?.hasResume}
                        >
                          {profile?.hasResume ? (
                            <div className="space-y-3">
                              <div className="flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                  <FileText className="h-4 w-4 text-[#666]" />
                                  <p className="text-[13px] text-[#333]">
                                    {profile.resumeFilename}
                                  </p>
                                </div>
                                <div className="flex gap-1">
                                  <button
                                    onClick={scoreResume}
                                    disabled={scoringResume}
                                    className="rounded-lg p-1.5 text-[#666] hover:bg-[#f5f5f5] hover:text-[#0d0d0d]"
                                    title="Re-score"
                                  >
                                    {scoringResume ? (
                                      <Loader2 className="h-4 w-4 animate-spin" />
                                    ) : (
                                      <RefreshCw className="h-4 w-4" />
                                    )}
                                  </button>
                                  <button
                                    onClick={() =>
                                      fileInputRef.current?.click()
                                    }
                                    className="rounded-lg p-1.5 text-[#666] hover:bg-[#f5f5f5] hover:text-[#0d0d0d]"
                                    title="Replace"
                                  >
                                    <Upload className="h-4 w-4" />
                                  </button>
                                  <button
                                    onClick={deleteResume}
                                    className="rounded-lg p-1.5 text-[#666] hover:bg-[#f5f5f5] hover:text-[#d45656]"
                                    title="Delete"
                                  >
                                    <Trash2 className="h-4 w-4" />
                                  </button>
                                </div>
                              </div>
                              <ResumeBreakdownView
                                breakdown={profile.resumeScoreBreakdown}
                              />
                            </div>
                          ) : (
                            <Button
                              onClick={() => fileInputRef.current?.click()}
                              disabled={loadingResume}
                              variant="outline"
                              className="h-auto w-full rounded-lg px-4 py-2 text-[13px]"
                            >
                              {loadingResume ? (
                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                              ) : (
                                <Upload className="mr-2 h-4 w-4" />
                              )}
                              Upload resume (PDF)
                            </Button>
                          )}
                        </AxisCard>
                      </div>
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept=".pdf"
                        onChange={handleFileUpload}
                        className="hidden"
                      />
                    </section>
                  </div>
                  <div
                    className={`pointer-events-none absolute inset-x-0 bottom-0 hidden h-24 bg-gradient-to-t from-background via-background to-transparent transition-opacity duration-200 lg:block ${showLeftFade ? "opacity-100" : "opacity-0"}`}
                  />
                </div>
              </div>

              <div className="flex min-h-0 flex-col">
                <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                  Study Plan
                </h2>
                <div className="relative min-h-0 lg:flex-1">
                  <div
                    ref={rightScrollRef}
                    onScroll={(e) =>
                      updateFadeState(
                        e.currentTarget,
                        setShowRightFade,
                      )
                    }
                    className="min-h-0 space-y-3 lg:h-full lg:overflow-y-auto lg:[scrollbar-width:none] lg:[-ms-overflow-style:none] lg:[&::-webkit-scrollbar]:hidden"
                  >
                    {tasks.length > 0 ? (
                      Object.entries(
                        tasks.reduce<Record<string, AiTask[]>>((acc, task) => {
                          const key = task.axis;
                          if (!acc[key]) acc[key] = [];
                          acc[key].push(task);
                          return acc;
                        }, {}),
                      ).map(([axis, axisTasks]) => (
                        <article
                          key={axis}
                          className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]"
                        >
                          <h3 className="mb-3 text-[15px] font-semibold capitalize text-[#0d0d0d]">
                            {axis === "coreCs"
                              ? "Core CS"
                              : axis === "systemDesign"
                                ? "System Design"
                                : axis.toUpperCase()}
                          </h3>
                          <div className="space-y-2">
                            {axisTasks.map((task) => (
                              <label
                                key={task.id}
                                className="flex cursor-pointer items-start gap-3 rounded-lg px-2 py-1.5 transition-colors hover:bg-[#fafafa]"
                              >
                                <input
                                  type="checkbox"
                                  checked={task.completed}
                                  onChange={() => toggleTask(task.id)}
                                  className="mt-0.5 h-4 w-4 shrink-0 rounded border-[#ccc] accent-[#0fa76e]"
                                />
                                <div className="min-w-0">
                                  <p
                                    className={`text-[14px] leading-[1.5] ${task.completed ? "text-[#999] line-through" : "text-[#333]"}`}
                                  >
                                    {task.title}
                                  </p>
                                  {task.description && (
                                    <p className="mt-0.5 text-[12px] leading-[1.5] text-[#999]">
                                      {task.description}
                                    </p>
                                  )}
                                </div>
                                {task.priority === 0 && !task.completed && (
                                  <span className="mt-0.5 shrink-0 rounded-full bg-[#f7e5e5] px-2 py-0.5 text-[10px] font-semibold text-[#d45656]">
                                    High
                                  </span>
                                )}
                              </label>
                            ))}
                          </div>
                        </article>
                      ))
                    ) : (
                      <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                        <p className="text-[14px] leading-[1.6] text-[#666]">
                          Your study plan will appear here once tasks are
                          generated from your assessments and resume signals.
                        </p>
                      </article>
                    )}
                  </div>
                  <div
                    className={`pointer-events-none absolute inset-x-0 bottom-0 hidden h-24 bg-gradient-to-t from-background via-background to-transparent transition-opacity duration-200 lg:block ${showRightFade ? "opacity-100" : "opacity-0"}`}
                  />
                </div>
              </div>
            </section>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
