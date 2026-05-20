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
  OnboardingData,
  AxisAssessmentPayload,
  SectionAssessment,
  ResumeScoreBreakdown,
  ResumeSubScore,
  AiTask,
  RoadmapItem,
} from "@/hooks/usePersonal";
import MilestoneAssessmentModal from "@/components/assessment/MilestoneAssessmentModal";
import ChatPanel from "@/components/personal/ChatPanel";
import { MarkdownText } from "@/lib/renderMarkdown";
import {
  CS_FUNDAMENTALS_SECTIONS,
  SYSTEM_DESIGN_SECTIONS,
  SECTION_COUNTS,
} from "@/data/assessmentContent";
import {
  Bot,
  FileText,
  GripVertical,
  Loader2,
  Pencil,
  RefreshCw,
  Sparkles,
  Trash2,
  Upload,
  GraduationCap,
  Briefcase,
  Target,
} from "lucide-react";
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
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

// --- Roadmap Helpers ---

const STATUS_CYCLE: Record<string, string> = {
  pending: "in_progress",
  in_progress: "completed",
  completed: "pending",
};

const STATUS_STYLES: Record<string, { bg: string; text: string; label: string }> = {
  pending: { bg: "bg-[#f0f0f0]", text: "text-[#666]", label: "Pending" },
  in_progress: { bg: "bg-[#fff3e0]", text: "text-[#e67e22]", label: "In Progress" },
  completed: { bg: "bg-[#e8f5e9]", text: "text-[#2e7d32]", label: "Done" },
};

function daysUntilDeadline(deadline: string | null): string | null {
  if (!deadline) return null;
  const days = Math.ceil((new Date(deadline).getTime() - Date.now()) / 86400000);
  if (days < 0) return `${Math.abs(days)}d overdue`;
  if (days === 0) return "Today";
  return `${days}d`;
}

function SortableRoadmapCard({
  item,
  onStatusChange,
  onUpdate,
  onDelete,
}: {
  item: RoadmapItem;
  onStatusChange: (id: string, status: string) => void;
  onUpdate: (id: string, updates: { title?: string; description?: string | null; deadline?: string | null }) => Promise<unknown>;
  onDelete: (id: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ title: item.title, description: item.description ?? "", deadline: item.deadline ?? "" });

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: item.id, disabled: editing });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  const statusStyle = STATUS_STYLES[item.status] ?? STATUS_STYLES.pending;
  const countdown = daysUntilDeadline(item.deadline);

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="rounded-[12px] border border-[rgba(0,0,0,0.05)] bg-white p-4 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]"
    >
      <div className="flex items-start gap-2">
        <button
          {...attributes}
          {...(editing ? {} : listeners)}
          className="mt-0.5 shrink-0 cursor-grab touch-none text-[#ccc] hover:text-[#999] active:cursor-grabbing"
        >
          <GripVertical className="h-4 w-4" />
        </button>
        <div className="min-w-0 flex-1">
          {editing ? (
            <div className="space-y-2">
              <input
                value={draft.title}
                onChange={(e) => setDraft((d) => ({ ...d, title: e.target.value }))}
                className="w-full rounded border border-[rgba(0,0,0,0.15)] px-2 py-1 text-[14px] font-semibold"
                placeholder="Title"
              />
              <textarea
                value={draft.description}
                onChange={(e) => setDraft((d) => ({ ...d, description: e.target.value }))}
                rows={2}
                className="w-full rounded border border-[rgba(0,0,0,0.15)] px-2 py-1 text-[13px]"
                placeholder="Description (optional)"
              />
              <input
                type="date"
                value={draft.deadline}
                onChange={(e) => setDraft((d) => ({ ...d, deadline: e.target.value }))}
                className="rounded border border-[rgba(0,0,0,0.15)] px-2 py-1 text-[13px]"
              />
              {item.phase && <p className="text-[12px] text-[#999]">{item.phase}</p>}
              {item.aiRationale && <p className="text-[11px] italic text-[#aaa]"><MarkdownText>{item.aiRationale}</MarkdownText></p>}
              <div className="flex gap-2">
                <button
                  onClick={async () => {
                    await onUpdate(item.id, {
                      title: draft.title,
                      description: draft.description || null,
                      deadline: draft.deadline || null,
                    });
                    setEditing(false);
                  }}
                  className="text-[12px] font-medium text-[#0fa76e]"
                >
                  Save
                </button>
                <button onClick={() => setEditing(false)} className="text-[12px] text-[#999]">
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => onStatusChange(item.id, STATUS_CYCLE[item.status])}
                  className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${statusStyle.bg} ${statusStyle.text}`}
                >
                  {statusStyle.label}
                </button>
                <h4 className="text-[14px] font-semibold leading-[1.4] text-[#0d0d0d]">
                  {item.title}
                </h4>
                <button
                  onClick={() => {
                    setDraft({ title: item.title, description: item.description ?? "", deadline: item.deadline ?? "" });
                    setEditing(true);
                  }}
                  className="shrink-0 text-[#ccc] hover:text-[#666]"
                >
                  <Pencil className="h-3 w-3" />
                </button>
                <button onClick={() => onDelete(item.id)} className="shrink-0 text-[#ccc] hover:text-[#d45656]">
                  <Trash2 className="h-3 w-3" />
                </button>
              </div>
              {(item.phase || countdown) && (
                <div className="mt-1 flex items-center gap-2 text-[12px] text-[#999]">
                  {item.phase && <span>{item.phase}</span>}
                  {item.phase && countdown && <span>·</span>}
                  {countdown && (
                    <span className={countdown.includes("overdue") ? "text-[#d45656]" : ""}>
                      {countdown}
                    </span>
                  )}
                </div>
              )}
              {item.description && (
                <p className="mt-1.5 text-[13px] leading-[1.5] text-[#555]">
                  {item.description}
                </p>
              )}
              {item.aiRationale && (
                <p className="mt-1 text-[11px] italic leading-[1.5] text-[#aaa]">
                  <MarkdownText>{item.aiRationale}</MarkdownText>
                </p>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function RoadmapSection({
  roadmap,
  canGenerate,
  generating,
  onGenerate,
  onReorder,
  onStatusChange,
  onUpdate,
  onDelete,
  onAdd,
}: {
  roadmap: RoadmapItem[];
  canGenerate: boolean;
  generating: boolean;
  onGenerate: () => void;
  onReorder: (orderedIds: string[]) => void;
  onStatusChange: (id: string, status: string) => void;
  onUpdate: (id: string, updates: { title?: string; description?: string | null; deadline?: string | null }) => Promise<unknown>;
  onDelete: (id: string) => void;
  onAdd: (item: { title: string; description?: string | null; deadline?: string | null }) => Promise<unknown>;
}) {
  const [addingItem, setAddingItem] = useState(false);
  const [addDraft, setAddDraft] = useState({ title: "", description: "", deadline: "" });

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = roadmap.findIndex((r) => r.id === active.id);
    const newIndex = roadmap.findIndex((r) => r.id === over.id);
    const reordered = arrayMove(roadmap, oldIndex, newIndex);
    onReorder(reordered.map((r) => r.id));
  };

  const addMilestoneLink = (
    !addingItem && (roadmap.length > 0 || canGenerate) && (
      <button
        onClick={() => setAddingItem(true)}
        className="mt-2 text-[13px] font-medium text-[#0fa76e] hover:underline"
      >
        + Add milestone
      </button>
    )
  );

  const addMilestoneForm = addingItem && (
    <div className="mt-3 rounded-[12px] border border-[rgba(0,0,0,0.08)] bg-white p-4">
      <div className="space-y-2">
        <input
          value={addDraft.title}
          onChange={(e) => setAddDraft((d) => ({ ...d, title: e.target.value }))}
          className="w-full rounded border border-[rgba(0,0,0,0.15)] px-2 py-1 text-[14px] font-semibold"
          placeholder="Title *"
        />
        <input
          value={addDraft.description}
          onChange={(e) => setAddDraft((d) => ({ ...d, description: e.target.value }))}
          className="w-full rounded border border-[rgba(0,0,0,0.15)] px-2 py-1 text-[13px]"
          placeholder="Description (optional)"
        />
        <input
          type="date"
          value={addDraft.deadline}
          onChange={(e) => setAddDraft((d) => ({ ...d, deadline: e.target.value }))}
          className="rounded border border-[rgba(0,0,0,0.15)] px-2 py-1 text-[13px]"
        />
        <div className="flex gap-2">
          <button
            onClick={async () => {
              if (!addDraft.title.trim()) return;
              await onAdd({
                title: addDraft.title,
                description: addDraft.description || null,
                deadline: addDraft.deadline || null,
              });
              setAddDraft({ title: "", description: "", deadline: "" });
              setAddingItem(false);
            }}
            className="text-[12px] font-medium text-[#0fa76e]"
          >
            Add
          </button>
          <button onClick={() => setAddingItem(false)} className="text-[12px] text-[#999]">
            Cancel
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <div>
      <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
        Career Roadmap
      </h2>
      {roadmap.length > 0 ? (
        <>
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={roadmap.map((r) => r.id)} strategy={verticalListSortingStrategy}>
              <div className="space-y-3">
                {roadmap.map((item) => (
                  <SortableRoadmapCard
                    key={item.id}
                    item={item}
                    onStatusChange={onStatusChange}
                    onUpdate={onUpdate}
                    onDelete={onDelete}
                  />
                ))}
              </div>
            </SortableContext>
          </DndContext>
          {addMilestoneLink}
          {addMilestoneForm}
        </>
      ) : canGenerate ? (
        <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
          <div className="flex flex-col items-center gap-3 py-2 text-center">
            <Sparkles className="h-6 w-6 text-[#0fa76e]" />
            <p className="text-[14px] leading-[1.6] text-[#666]">
              Generate a personalized career roadmap with AI-calculated deadlines based on your profile.
            </p>
            <Button
              onClick={onGenerate}
              disabled={generating}
              className="mt-1 rounded-lg bg-[#0fa76e] px-4 py-2 text-[13px] font-medium text-white hover:bg-[#0d8f5e]"
            >
              {generating ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Sparkles className="mr-2 h-4 w-4" />
              )}
              Generate Roadmap
            </Button>
            {addMilestoneLink}
            {addMilestoneForm}
          </div>
        </article>
      ) : (
        <article className="rounded-[16px] border border-[rgba(0,0,0,0.05)] bg-white p-5 shadow-[rgba(0,0,0,0.03)_0px_2px_4px]">
          <p className="text-[14px] leading-[1.6] text-[#666]">
            Complete your profile (set target role + finish an assessment or upload your resume)
            to unlock your career roadmap.
          </p>
        </article>
      )}
    </div>
  );
}

// --- Constants ---

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
  onUploadResume,
}: {
  onComplete: (data: OnboardingData) => Promise<void>;
  onUploadResume: (file: File) => Promise<unknown>;
}) {
  const [step, setStep] = useState(0);
  const [targetRole, setTargetRole] = useState("swe_intern");
  const [graduationYear, setGraduationYear] = useState(
    new Date().getFullYear() + 2,
  );
  const [submitting, setSubmitting] = useState(false);
  const [uploadingResume, setUploadingResume] = useState(false);
  const resumeInputRef = useRef<HTMLInputElement>(null);

  const handleFinishStep1 = async () => {
    setSubmitting(true);
    try {
      await onComplete({ targetRole, graduationYear });
      setStep(1);
    } finally {
      setSubmitting(false);
    }
  };

  const handleResumeUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingResume(true);
    try {
      await onUploadResume(file);
    } finally {
      setUploadingResume(false);
      if (resumeInputRef.current) resumeInputRef.current.value = "";
    }
  };

  const currentYear = new Date().getFullYear();
  const yearOptions = Array.from({ length: 7 }, (_, i) => currentYear + i);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="mx-4 w-full max-w-[540px] rounded-[20px] bg-white p-8 shadow-xl">
        <div className="mb-6">
          <p className="font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666666]">
            Step {step + 1} of 2
          </p>
          <h2 className="mt-1 text-[24px] font-semibold leading-[1.3] tracking-[-0.24px] text-[#0d0d0d]">
            {step === 0 && "About you"}
            {step === 1 && "Upload your resume"}
          </h2>
          <p className="mt-1 text-[14px] text-[#666]">
            {step === 0 && "Help us tailor your experience."}
            {step === 1 && "We'll score it and generate study tasks."}
          </p>
        </div>

        {step === 0 && (
          <div className="space-y-5">
            <div className="space-y-2">
              <label className="text-[14px] font-medium text-[#333]">
                What are you targeting?
              </label>
              <div className="flex gap-2">
                {[
                  {
                    value: "swe_intern",
                    label: "SWE Internship",
                    icon: Target,
                  },
                  { value: "swe_job", label: "SWE Full-time", icon: Briefcase },
                ].map(({ value, label, icon: Icon }) => (
                  <button
                    key={value}
                    onClick={() => setTargetRole(value)}
                    className={`flex flex-1 items-center justify-center gap-2 rounded-xl border px-4 py-3 text-[14px] font-medium transition-colors ${
                      targetRole === value
                        ? "border-[#0d0d0d] bg-[#0d0d0d] text-white"
                        : "border-[rgba(0,0,0,0.1)] bg-white text-[#333] hover:border-[rgba(0,0,0,0.2)]"
                    }`}
                  >
                    <Icon className="h-4 w-4" />
                    {label}
                  </button>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <label className="text-[14px] font-medium text-[#333]">
                Expected graduation year
              </label>
              <div className="flex flex-wrap gap-2">
                {yearOptions.map((y) => (
                  <button
                    key={y}
                    onClick={() => setGraduationYear(y)}
                    className={`rounded-lg px-4 py-2 text-[14px] font-medium transition-colors ${
                      graduationYear === y
                        ? "bg-[#0d0d0d] text-white"
                        : "bg-[#f5f5f5] text-[#666] hover:bg-[#e5e5e5]"
                    }`}
                  >
                    {y}
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="flex flex-col items-center gap-4 py-4">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-[#f5f5f5]">
              <Upload className="h-7 w-7 text-[#999]" />
            </div>
            <p className="text-center text-[14px] text-[#666]">
              Upload your resume as a PDF to get AI-powered scoring and
              personalized study tasks.
            </p>
            <Button
              onClick={() => resumeInputRef.current?.click()}
              disabled={uploadingResume}
              className="h-auto rounded-full bg-[#0d0d0d] px-6 py-2.5 text-[15px] text-white"
            >
              {uploadingResume ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Upload className="mr-2 h-4 w-4" />
              )}
              {uploadingResume ? "Uploading..." : "Upload resume (PDF)"}
            </Button>
            <input
              ref={resumeInputRef}
              type="file"
              accept=".pdf"
              onChange={handleResumeUpload}
              className="hidden"
            />
          </div>
        )}

        <div className="mt-6 flex justify-end">
          {step === 0 && (
            <Button
              onClick={handleFinishStep1}
              disabled={submitting}
              className="h-auto rounded-full bg-[#0d0d0d] px-6 py-2 text-[15px] text-white"
            >
              {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Next
            </Button>
          )}
          {step === 1 && (
            <Button
              onClick={() => window.location.reload()}
              variant="outline"
              className="h-auto rounded-full px-6 py-2 text-[15px]"
            >
              {uploadingResume ? "Please wait..." : "Skip"}
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
          <MarkdownText>{sub.rationale}</MarkdownText>
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
    roadmap,
    loadingProfile,
    loadingInsights,
    loadingLeetCode,
    loadingResume,
    scoringResume,
    generatingRoadmap,
    canGenerateRoadmap,
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
    generateRoadmap,
    reorderRoadmap,
    updateRoadmapItemStatus,
    updateRoadmapItem,
    createRoadmapItem,
    deleteRoadmapItem,
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
          <OnboardingModal
            onComplete={completeOnboarding}
            onUploadResume={uploadResume}
          />
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
                      updateFadeState(e.currentTarget, setShowLeftFade)
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
                                    <MarkdownText>{line.replace(/^\d+\.\s*/, "")}</MarkdownText>
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
                          connected={!!profile?.systemDesignAnswers && Object.keys(profile.systemDesignAnswers).length > 0}
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
                          connected={!!profile?.coreCsAnswers && Object.keys(profile.coreCsAnswers).length > 0}
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
                <div className="relative min-h-0 lg:flex-1">
                  <div
                    ref={rightScrollRef}
                    onScroll={(e) =>
                      updateFadeState(e.currentTarget, setShowRightFade)
                    }
                    className="min-h-0 space-y-6 lg:h-full lg:overflow-y-auto lg:[scrollbar-width:none] lg:[-ms-overflow-style:none] lg:[&::-webkit-scrollbar]:hidden"
                  >
                    {/* Career Roadmap */}
                    <RoadmapSection
                      roadmap={roadmap}
                      canGenerate={canGenerateRoadmap}
                      generating={generatingRoadmap}
                      onGenerate={generateRoadmap}
                      onReorder={reorderRoadmap}
                      onStatusChange={updateRoadmapItemStatus}
                      onUpdate={updateRoadmapItem}
                      onDelete={deleteRoadmapItem}
                      onAdd={createRoadmapItem}
                    />

                    {/* Study Plan */}
                    <div>
                      <h2 className="mb-4 text-[20px] font-semibold leading-[1.3] tracking-[-0.2px] text-[#0d0d0d]">
                        Study Plan
                      </h2>
                      <div className="space-y-3">
                        {tasks.length > 0 ? (
                          Object.entries(
                            tasks.reduce<Record<string, AiTask[]>>(
                              (acc, task) => {
                                const key = task.axis;
                                if (!acc[key]) acc[key] = [];
                                acc[key].push(task);
                                return acc;
                              },
                              {},
                            ),
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
                              generated from your assessments and resume
                              signals.
                            </p>
                          </article>
                        )}
                      </div>
                    </div>
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
      <ChatPanel />
    </AuthGuard>
  );
}
