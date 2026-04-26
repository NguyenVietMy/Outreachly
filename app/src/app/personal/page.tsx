"use client";

import { useState, useRef } from "react";
import AuthGuard from "@/components/AuthGuard";
import DashboardLayout from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { usePersonal, KnowledgeArea, OnboardingData, AxisAssessmentPayload, SectionAssessment, ResumeScoreBreakdown, ResumeSubScore } from "@/hooks/usePersonal";
import MilestoneAssessmentModal from "@/components/assessment/MilestoneAssessmentModal";
import { CS_FUNDAMENTALS_SECTIONS, SYSTEM_DESIGN_SECTIONS, SECTION_COUNTS } from "@/data/assessmentContent";
import {
  Bot,
  ChevronDown,
  ChevronUp,
  FileText,
  Loader2,
  RefreshCw,
  Save,
  Sparkles,
  Trash2,
  Upload,
  X,
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

// --- Level + Guidance Logic ---

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

function getGuidance(level: string, targetRole: string, month: number): string[] {
  const isIntern = targetRole === "swe_intern";

  if (level === "Freshman") {
    if (month >= 0 && month <= 4) return [
      "Build your first project — a personal site or CLI tool counts.",
      "Start LeetCode Easy problems to build problem-solving habits.",
      "Look into freshman/sophomore programs: Google STEP, Meta University, Explore Microsoft.",
    ];
    return [
      "Summer = build time. Pick a project with real users or a tech you want to learn.",
      "Start applying to freshman-specific internship programs (apps open Aug-Oct).",
      "Aim for 50+ LC easy problems before fall semester.",
    ];
  }

  if (level === "Sophomore") {
    if (isIntern) {
      if (month >= 7 && month <= 10) return [
        "Internship apps are OPEN. Apply daily — big tech closes by October.",
        "Aim to solve 100+ LC problems (mix of easy + medium) before interviews.",
        "Your resume needs 1-2 projects with measurable impact. Polish it now.",
      ];
      if (month >= 0 && month <= 4) return [
        "Late recruiting season — focus on mid-size companies and startups.",
        "Keep grinding LC mediums. Target 150+ total by summer.",
        "Build or ship a project you can demo in interviews.",
      ];
      return [
        "Summer = grind time. Build your strongest project yet.",
        "Start LC mediums and study common patterns (sliding window, two pointers, BFS/DFS).",
        "Big tech apps open in August — have your resume ready.",
      ];
    }
    return [
      "Focus on building strong fundamentals and projects.",
      "It's early for full-time, but internships now will set you up.",
      "Start thinking about what kind of SWE role excites you (frontend, backend, infra, ML).",
    ];
  }

  if (level === "Junior") {
    if (isIntern) {
      if (month >= 7 && month <= 10) return [
        "CRITICAL: This is your key internship recruiting window. Apply to 50+ companies.",
        "You should be solving LC mediums consistently. Target 200+ total.",
        "Practice behavioral interviews — Amazon LPs, Google's Googleyness.",
        "Junior summer internships often convert to full-time offers.",
      ];
      if (month >= 0 && month <= 4) return [
        "Late season — keep applying, but also prepare for full-time recruiting in fall.",
        "If you land an internship, prepare to convert. If not, double down on projects.",
        "Study system design basics — you'll need it for full-time interviews.",
      ];
      return [
        "Summer prep is critical. If interning, aim to convert.",
        "If not interning, build a standout project and prep for fall full-time cycle.",
        "Start studying system design — it's tested at senior-level internships and new grad roles.",
      ];
    }
    // SWE job as junior
    if (month >= 7 && month <= 11) return [
      "New grad apps are opening. Start applying NOW — early is better.",
      "LC mediums + hards. Target 200+ problems with strong medium consistency.",
      "System design is tested in new grad rounds at many companies. Study it.",
      "Practice mock interviews — Pramp, interviewing.io, or with friends.",
    ];
    if (month >= 0 && month <= 4) return [
      "Spring recruiting = second wave. Keep applying.",
      "Refine your behavioral answers. Have 3-4 strong project stories.",
      "If you haven't started system design, start now.",
    ];
    return [
      "Summer = final prep window before peak new grad season in August.",
      "Solve 200+ LC problems. Focus on mediums and common patterns.",
      "Have 2-3 strong projects on your resume. At least one deployed.",
    ];
  }

  if (level === "Senior") {
    if (isIntern) return [
      "As a senior, consider pivoting to full-time roles instead.",
      "If you need an internship for visa/experience, apply to companies with flexible timelines.",
    ];
    if (month >= 7 && month <= 11) return [
      "Peak new grad season. Apply aggressively — aim for 10+ applications per week.",
      "System design is critical. Study: load balancing, caching, databases, message queues.",
      "You should be comfortable with LC mediums and solving some hards.",
      "Negotiate offers — don't accept the first one immediately.",
    ];
    if (month >= 0 && month <= 4) return [
      "Spring = last major recruiting push before graduation.",
      "Apply broadly — startups, mid-size, and late-posting big tech roles.",
      "Practice your pitch: why you, why this company, what you bring.",
    ];
    return [
      "Post-graduation: keep applying, network actively, attend career fairs.",
      "Consider contract roles or startup positions to get your foot in the door.",
    ];
  }

  // New Grad
  return [
    "Focus on applying to new grad roles. Cast a wide net.",
    "Network on LinkedIn — cold messages to recruiters do work.",
    "Keep your skills sharp with LC practice and side projects.",
    "Consider your first job as a stepping stone — growth matters more than prestige.",
  ];
}

// --- Constants ---

const DEFAULT_AREAS = [
  "Algorithms", "Data Structures", "Operating Systems", "Computer Networks",
  "Databases", "Systems Design", "Web Development", "Math & Discrete",
  "Machine Learning", "Security",
];

function isNewFormatAssessment(
  data: AxisAssessmentPayload | Record<string, unknown> | null
): data is AxisAssessmentPayload {
  return !!data && "sections" in data && "axisId" in data;
}

function getAssessmentProgress(
  answers: AxisAssessmentPayload | Record<string, unknown> | null,
  axis: "systemDesign" | "coreCs"
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
    DEFAULT_AREAS.map((a) => ({ area: a, level: 1 }))
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

// --- Activity Heatmap ---

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
          <p className={`text-[28px] font-bold leading-none ${getScoreColor(score)}`}>
            {connected ? score : "—"}
          </p>
          {connected && <p className="mt-0.5 text-[11px] text-[#999]">/ 100</p>}
        </div>
      </div>
      {children && <div className="mt-4 border-t border-[rgba(0,0,0,0.05)] pt-4">{children}</div>}
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
        <span className="font-medium" style={{ color }}>{sub.score}/{sub.max}</span>
      </div>
      <div className="mt-1 h-1.5 w-full rounded-full bg-[#f0f0f0]">
        <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, backgroundColor: color }} />
      </div>
      {sub.rationale && (
        <div className="pointer-events-none absolute bottom-full left-0 z-10 mb-2 hidden w-64 rounded-lg border border-[rgba(0,0,0,0.08)] bg-white p-2.5 text-[11px] leading-[1.5] text-[#555] shadow-lg group-hover:block">
          {sub.rationale}
        </div>
      )}
    </div>
  );
}

function ResumeBreakdownView({ breakdown }: { breakdown: ResumeScoreBreakdown | null }) {
  if (!breakdown || !breakdown.sections) {
    return <p className="text-[12px] text-[#999]">Upload a resume to get AI-powered scoring.</p>;
  }

  const s = breakdown.sections;
  const decisionColor =
    breakdown.decision === "ADVANCE" ? "bg-[#d4fae8] text-[#0fa76e]" :
    breakdown.decision === "HOLD" ? "bg-[#f8ebd8] text-[#c37d0d]" :
    "bg-[#f7e5e5] text-[#d45656]";

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${decisionColor}`}>
            {breakdown.decision}
          </span>
          <span className="text-[12px] text-[#999]">{breakdown.total_score}/{breakdown.max_score}</span>
        </div>
        {breakdown.flags && breakdown.flags.length > 0 && (
          <div className="flex gap-1">
            {breakdown.flags.map((f) => (
              <span key={f} className="rounded bg-[#f5f5f5] px-1.5 py-0.5 text-[10px] text-[#666]">{f}</span>
            ))}
          </div>
        )}
      </div>

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">Technical Skills</p>
        <ScoreBar label="Languages" sub={s.technical_skills?.primary_language} />
        <ScoreBar label="Frameworks" sub={s.technical_skills?.backend_frameworks} />
        <ScoreBar label="Data Layer" sub={s.technical_skills?.data_layer} />
        <ScoreBar label="Infra/DevOps" sub={s.technical_skills?.infra_devops} />
      </div>

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">Experience</p>
        <ScoreBar label="Years" sub={s.experience?.years} />
        <ScoreBar label="Progression" sub={s.experience?.progression} />
        <ScoreBar label="Recency" sub={s.experience?.recency} />
      </div>

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">Impact</p>
        <ScoreBar label="Quantified Outcomes" sub={s.impact?.quantified_outcomes} />
        <ScoreBar label="Scope" sub={s.impact?.scope} />
      </div>

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">Education</p>
        <ScoreBar label="Degree" sub={s.education?.degree} />
        <ScoreBar label="Certifications" sub={s.education?.certifications} />
      </div>

      <ScoreBar label="Project Complexity" sub={s.project_complexity} />

      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[#999]">Resume Quality</p>
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
    heatmap,
    insights,
    loadingProfile,
    loadingInsights,
    loadingLeetCode,
    loadingResume,
    scoringResume,
    updateProfile,
    completeOnboarding,
    updateCareer,
    connectLeetCode,
    refreshLeetCode,
    uploadResume,
    deleteResume,
    scoreResume,
    submitQuestionnaire,
    requestInsights,
  } = usePersonal();

  const [editingProfile, setEditingProfile] = useState(false);
  const [profileDraft, setProfileDraft] = useState("");
  const [knowledgeDraft, setKnowledgeDraft] = useState<KnowledgeArea[]>([]);
  const [profileExpanded, setProfileExpanded] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [lcUsername, setLcUsername] = useState("");
  const [lcError, setLcError] = useState("");
  const [showSDAssessment, setShowSDAssessment] = useState(false);
  const [showCSAssessment, setShowCSAssessment] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Career editing
  const [editingCareer, setEditingCareer] = useState(false);
  const [careerDraft, setCareerDraft] = useState({ targetRole: "swe_intern", graduationYear: new Date().getFullYear() + 2 });
  const [savingCareer, setSavingCareer] = useState(false);

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

  const level = profile?.graduationYear ? computeLevel(profile.graduationYear) : null;
  const guidance = level && profile?.targetRole
    ? getGuidance(level, profile.targetRole, new Date().getMonth())
    : null;

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
        {showSDAssessment && (
          <MilestoneAssessmentModal
            axisId="systemDesign"
            title="System Design Assessment"
            sections={SYSTEM_DESIGN_SECTIONS}
            existingAssessment={profile?.systemDesignAnswers ?? null}
            onSectionComplete={(section: SectionAssessment) => submitQuestionnaire("systemDesign", section as unknown as Record<string, unknown>)}
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
            onSectionComplete={(section: SectionAssessment) => submitQuestionnaire("coreCs", section as unknown as Record<string, unknown>)}
            onComplete={() => {}}
            onClose={() => setShowCSAssessment(false)}
          />
        )}

        <div className="min-h-[calc(100vh-64px)] bg-[#ffffff] px-6 py-12 text-[#0d0d0d] md:px-8 md:py-16">
          <div className="mx-auto flex w-full max-w-[1200px] flex-col gap-10">
            {/* Header + Career Target */}
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

              {/* Career target bar */}
              <div className="mt-5 flex flex-wrap items-center gap-3 rounded-[14px] border border-[rgba(0,0,0,0.05)] bg-[#fafafa] px-5 py-4">
                {editingCareer ? (
                  <>
                    <div className="flex items-center gap-2">
                      <Target className="h-4 w-4 text-[#666]" />
                      <select
                        value={careerDraft.targetRole}
                        onChange={(e) => setCareerDraft((d) => ({ ...d, targetRole: e.target.value }))}
                        className="rounded-lg border border-[rgba(0,0,0,0.1)] bg-white px-3 py-1.5 text-[14px] outline-none"
                      >
                        <option value="swe_intern">SWE Intern</option>
                        <option value="swe_job">SWE Full-Time</option>
                      </select>
                    </div>
                    <div className="flex items-center gap-2">
                      <GraduationCap className="h-4 w-4 text-[#666]" />
                      <input
                        type="number"
                        value={careerDraft.graduationYear}
                        onChange={(e) => setCareerDraft((d) => ({ ...d, graduationYear: parseInt(e.target.value) || 2028 }))}
                        className="w-20 rounded-lg border border-[rgba(0,0,0,0.1)] bg-white px-3 py-1.5 text-[14px] outline-none"
                        min={2024}
                        max={2032}
                      />
                    </div>
                    <div className="ml-auto flex gap-2">
                      <Button variant="outline" onClick={() => setEditingCareer(false)} className="h-auto rounded-lg px-3 py-1.5 text-[13px]">
                        Cancel
                      </Button>
                      <Button onClick={handleSaveCareer} disabled={savingCareer} className="h-auto rounded-lg bg-[#0d0d0d] px-3 py-1.5 text-[13px] text-white">
                        {savingCareer && <Loader2 className="mr-1.5 h-3 w-3 animate-spin" />}
                        Save
                      </Button>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="flex items-center gap-4">
                      <div className="flex items-center gap-2">
                        {profile?.targetRole === "swe_job" ? (
                          <Briefcase className="h-4 w-4 text-[#3772cf]" />
                        ) : (
                          <Target className="h-4 w-4 text-[#0fa76e]" />
                        )}
                        <span className="text-[15px] font-medium text-[#0d0d0d]">
                          {profile?.targetRole === "swe_job" ? "SWE Full-Time" : "SWE Intern"}
                        </span>
                      </div>
                      {profile?.graduationYear && (
                        <>
                          <span className="text-[#ccc]">|</span>
                          <div className="flex items-center gap-2">
                            <GraduationCap className="h-4 w-4 text-[#666]" />
                            <span className="text-[15px] text-[#333]">Class of {profile.graduationYear}</span>
                          </div>
                          <span className="text-[#ccc]">|</span>
                          <span className={`rounded-full px-2.5 py-0.5 text-[12px] font-semibold ${
                            level === "Freshman" ? "bg-[#e8d8f8] text-[#7c3aed]" :
                            level === "Sophomore" ? "bg-[#d4fae8] text-[#0fa76e]" :
                            level === "Junior" ? "bg-[#f8ebd8] text-[#c37d0d]" :
                            level === "Senior" ? "bg-[#e7eefb] text-[#3772cf]" :
                            "bg-[#f5f5f5] text-[#666]"
                          }`}>
                            {level}
                          </span>
                        </>
                      )}
                    </div>
                    <button
                      onClick={() => {
                        setCareerDraft({
                          targetRole: profile?.targetRole || "swe_intern",
                          graduationYear: profile?.graduationYear || new Date().getFullYear() + 2,
                        });
                        setEditingCareer(true);
                      }}
                      className="ml-auto text-[13px] font-medium text-[#666] hover:text-[#0d0d0d]"
                    >
                      Edit
                    </button>
                  </>
                )}
              </div>
            </section>

            {/* Guidance Banner */}
            {guidance && (
              <section className="rounded-[16px] border border-[#e7eefb] bg-gradient-to-r from-[#f8faff] to-[#f0f4ff] p-6">
                <div className="mb-3 flex items-center gap-2">
                  <Target className="h-4 w-4 text-[#3772cf]" />
                  <h3 className="text-[15px] font-semibold text-[#0d0d0d]">
                    What you should be doing right now
                  </h3>
                  <span className="text-[12px] text-[#666]">
                    ({level} &middot; {new Date().toLocaleDateString("en-US", { month: "long", year: "numeric" })})
                  </span>
                </div>
                <ul className="space-y-2">
                  {guidance.map((tip, i) => (
                    <li key={i} className="flex items-start gap-2.5 text-[14px] leading-[1.6] text-[#333]">
                      <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-[#3772cf]" />
                      {tip}
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {/* 5-Axis Radar + AI Insights */}
            <section className="grid gap-6 xl:grid-cols-2">
              <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-6 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
                <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                  SWE Readiness Radar
                </h2>
                {radarData.length > 0 ? (
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
                    Click &quot;Get AI Insights&quot; for personalized SWE career advice based on your scores.
                  </p>
                )}
              </article>
            </section>

            {/* 5 Axis Cards */}
            <section>
              <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                Assessment Axes
              </h2>
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
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
                          Connected as <span className="font-medium text-[#0d0d0d]">{profile.leetcodeUsername}</span>
                        </p>
                        <button
                          onClick={refreshLeetCode}
                          disabled={loadingLeetCode}
                          className="text-[#666] hover:text-[#0d0d0d]"
                        >
                          <RefreshCw className={`h-4 w-4 ${loadingLeetCode ? "animate-spin" : ""}`} />
                        </button>
                      </div>
                      {profile.leetcodeStats && (
                        <div className="mt-3 grid grid-cols-4 gap-2">
                          <div className="rounded-lg bg-[#f5f5f5] p-2 text-center">
                            <p className="text-[18px] font-bold text-[#0d0d0d]">{profile.leetcodeStats.total}</p>
                            <p className="text-[10px] text-[#999]">Total</p>
                          </div>
                          <div className="rounded-lg bg-[#d4fae8] p-2 text-center">
                            <p className="text-[18px] font-bold text-[#0fa76e]">{profile.leetcodeStats.easy}</p>
                            <p className="text-[10px] text-[#0fa76e]">Easy</p>
                          </div>
                          <div className="rounded-lg bg-[#f8ebd8] p-2 text-center">
                            <p className="text-[18px] font-bold text-[#c37d0d]">{profile.leetcodeStats.medium}</p>
                            <p className="text-[10px] text-[#c37d0d]">Med</p>
                          </div>
                          <div className="rounded-lg bg-[#f7e5e5] p-2 text-center">
                            <p className="text-[18px] font-bold text-[#d45656]">{profile.leetcodeStats.hard}</p>
                            <p className="text-[10px] text-[#d45656]">Hard</p>
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
                          onChange={(e) => setLcUsername(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && handleConnectLC()}
                          className="flex-1 rounded-lg border border-[rgba(0,0,0,0.1)] px-3 py-2 text-[14px] outline-none focus:border-[#0d0d0d]"
                        />
                        <Button
                          onClick={handleConnectLC}
                          disabled={loadingLeetCode || !lcUsername.trim()}
                          className="h-auto rounded-lg bg-[#0d0d0d] px-4 py-2 text-[13px] text-white"
                        >
                          {loadingLeetCode ? <Loader2 className="h-4 w-4 animate-spin" /> : "Connect"}
                        </Button>
                      </div>
                      {lcError && <p className="mt-2 text-[13px] text-[#d45656]">{lcError}</p>}
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
                    Upload your resume below to auto-extract project data. LLM scoring coming soon.
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
                    const progress = getAssessmentProgress(profile?.systemDesignAnswers ?? null, "systemDesign");
                    return (
                      <>
                        {progress && (
                          <p className="mb-2 text-[12px] text-[#999]">
                            {progress.completed} of {progress.total} sections complete
                          </p>
                        )}
                        <Button
                          onClick={() => setShowSDAssessment(true)}
                          variant="outline"
                          className="h-auto w-full rounded-lg px-4 py-2 text-[13px]"
                        >
                          {progress && progress.completed > 0
                            ? progress.completed >= progress.total ? "Retake assessment" : "Continue assessment"
                            : (axes?.systemDesign ?? 0) > 0 ? "Retake assessment" : "Take assessment"}
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
                    const progress = getAssessmentProgress(profile?.coreCsAnswers ?? null, "coreCs");
                    return (
                      <>
                        {progress && (
                          <p className="mb-2 text-[12px] text-[#999]">
                            {progress.completed} of {progress.total} sections complete
                          </p>
                        )}
                        <Button
                          onClick={() => setShowCSAssessment(true)}
                          variant="outline"
                          className="h-auto w-full rounded-lg px-4 py-2 text-[13px]"
                        >
                          {progress && progress.completed > 0
                            ? progress.completed >= progress.total ? "Retake assessment" : "Continue assessment"
                            : (axes?.coreCs ?? 0) > 0 ? "Retake assessment" : "Take assessment"}
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
                          <p className="text-[13px] text-[#333]">{profile.resumeFilename}</p>
                        </div>
                        <div className="flex gap-1">
                          <button
                            onClick={scoreResume}
                            disabled={scoringResume}
                            className="rounded-lg p-1.5 text-[#666] hover:bg-[#f5f5f5] hover:text-[#0d0d0d]"
                            title="Re-score"
                          >
                            {scoringResume ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
                          </button>
                          <button
                            onClick={() => fileInputRef.current?.click()}
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
                      <ResumeBreakdownView breakdown={profile.resumeScoreBreakdown} />
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
