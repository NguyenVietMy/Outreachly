"use client";

import { useState, useMemo } from "react";
import { Button } from "@/components/ui/button";
import { Loader2, X, ChevronRight, ChevronLeft, Check } from "lucide-react";
import type { SectionDefinition } from "@/data/assessmentContent";
import type {
  AxisAssessmentPayload,
  SectionAssessment,
} from "@/hooks/usePersonal";

interface MilestoneAssessmentModalProps {
  axisId: "systemDesign" | "coreCs";
  title: string;
  sections: SectionDefinition[];
  existingAssessment: AxisAssessmentPayload | Record<string, unknown> | null;
  onSectionComplete: (section: SectionAssessment) => Promise<void>;
  onComplete: () => void;
  onClose: () => void;
}

function isNewFormat(
  data: AxisAssessmentPayload | Record<string, unknown> | null
): data is AxisAssessmentPayload {
  return !!data && "sections" in data && "axisId" in data;
}

function getCompletedSectionIds(
  existing: AxisAssessmentPayload | Record<string, unknown> | null
): Set<string> {
  if (!isNewFormat(existing)) return new Set();
  return new Set(existing.sections.map((s) => s.sectionId));
}

function getExistingSectionData(
  existing: AxisAssessmentPayload | Record<string, unknown> | null,
  sectionId: string
): SectionAssessment | null {
  if (!isNewFormat(existing)) return null;
  return existing.sections.find((s) => s.sectionId === sectionId) ?? null;
}

const CONFIDENCE_OPTIONS = [
  {
    value: "low" as const,
    label: "Low",
    desc: "I might be overestimating myself here",
  },
  {
    value: "medium" as const,
    label: "Medium",
    desc: "I feel fairly accurate",
  },
  {
    value: "high" as const,
    label: "High",
    desc: "I'm confident in my self-assessment",
  },
];

export default function MilestoneAssessmentModal({
  axisId,
  title,
  sections,
  existingAssessment,
  onSectionComplete,
  onComplete,
  onClose,
}: MilestoneAssessmentModalProps) {
  const completedIds = useMemo(
    () => getCompletedSectionIds(existingAssessment),
    [existingAssessment]
  );

  const firstIncomplete = sections.findIndex((s) => !completedIds.has(s.id));

  const [currentIndex, setCurrentIndex] = useState(
    firstIncomplete >= 0 ? firstIncomplete : sections.length
  );
  const [answers, setAnswers] = useState<Record<string, number>>(() => {
    if (!isNewFormat(existingAssessment)) return {};
    const map: Record<string, number> = {};
    for (const sec of existingAssessment.sections) {
      for (const ss of sec.subskills) {
        map[ss.subskillId] = ss.tier;
      }
    }
    return map;
  });
  const [confidence, setConfidence] = useState<
    "low" | "medium" | "high" | null
  >(null);
  const [phase, setPhase] = useState<"subskills" | "confidence" | "done">(
    firstIncomplete < 0 ? "done" : "subskills"
  );
  const [submitting, setSubmitting] = useState(false);
  const [completedDuringSession, setCompletedDuringSession] = useState<
    Set<string>
  >(new Set());

  const totalCompleted = completedIds.size + completedDuringSession.size;
  const isAllDone = totalCompleted >= sections.length;
  const section = currentIndex < sections.length ? sections[currentIndex] : null;

  const allSubskillsAnswered =
    section?.subskills.every((ss) => answers[ss.id] !== undefined) ?? false;

  const handleSelectTier = (subskillId: string, tier: number) => {
    setAnswers((prev) => ({ ...prev, [subskillId]: tier }));
  };

  const handleConfidenceDone = async () => {
    if (!section || !confidence) return;
    setSubmitting(true);
    try {
      const sectionAssessment: SectionAssessment = {
        sectionId: section.id,
        confidence,
        completedAt: new Date().toISOString(),
        subskills: section.subskills.map((ss) => ({
          subskillId: ss.id,
          tier: (answers[ss.id] ?? 0) as 0 | 1 | 2 | 3 | 4,
        })),
      };
      await onSectionComplete(sectionAssessment);
      setCompletedDuringSession((prev) => new Set(prev).add(section.id));
      setConfidence(null);

      const nextIncomplete = sections.findIndex(
        (s, i) =>
          i > currentIndex &&
          !completedIds.has(s.id) &&
          !completedDuringSession.has(s.id) &&
          s.id !== section.id
      );

      if (nextIncomplete >= 0) {
        setCurrentIndex(nextIncomplete);
        setPhase("subskills");
      } else {
        const anyRemaining = sections.findIndex(
          (s) =>
            !completedIds.has(s.id) &&
            !completedDuringSession.has(s.id) &&
            s.id !== section.id
        );
        if (anyRemaining >= 0) {
          setCurrentIndex(anyRemaining);
          setPhase("subskills");
        } else {
          setPhase("done");
        }
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleGoToSection = (index: number) => {
    setCurrentIndex(index);
    setConfidence(null);
    setPhase("subskills");
  };

  const finalScore = useMemo(() => {
    let total = 0;
    let count = 0;
    for (const sec of sections) {
      for (const ss of sec.subskills) {
        if (answers[ss.id] !== undefined) {
          total += answers[ss.id];
          count++;
        }
      }
    }
    return count > 0 ? Math.round((total / (count * 4)) * 100) : 0;
  }, [answers, sections]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="mx-4 flex w-full max-w-[720px] max-h-[90vh] flex-col rounded-[20px] bg-white shadow-xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[rgba(0,0,0,0.05)] px-8 py-5">
          <div>
            <h2 className="text-[20px] font-semibold tracking-[-0.2px] text-[#0d0d0d]">
              {title}
            </h2>
            <p className="mt-0.5 text-[13px] text-[#666]">
              {totalCompleted} of {sections.length} sections complete
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1 text-[#999] hover:text-[#333]"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Progress bar */}
        <div className="h-1 w-full bg-[#f5f5f5]">
          <div
            className="h-full bg-[#18E299] transition-all duration-300"
            style={{
              width: `${(totalCompleted / sections.length) * 100}%`,
            }}
          />
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-8 py-6">
          {phase === "done" ? (
            <div className="flex flex-col items-center py-8">
              <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-[#d4fae8]">
                <Check className="h-8 w-8 text-[#0fa76e]" />
              </div>
              <h3 className="text-[22px] font-semibold text-[#0d0d0d]">
                Assessment Complete
              </h3>
              <p className="mt-2 text-[15px] text-[#666]">
                Your {axisId === "coreCs" ? "CS Fundamentals" : "System Design"}{" "}
                score
              </p>
              <p className="mt-3 text-[48px] font-bold leading-none text-[#0fa76e]">
                {finalScore}
              </p>
              <p className="mt-1 text-[13px] text-[#999]">/ 100</p>

              {/* Section breakdown */}
              <div className="mt-8 w-full max-w-md space-y-2">
                {sections.map((sec) => {
                  let secTotal = 0;
                  let secCount = 0;
                  for (const ss of sec.subskills) {
                    if (answers[ss.id] !== undefined) {
                      secTotal += answers[ss.id];
                      secCount++;
                    }
                  }
                  const secScore =
                    secCount > 0
                      ? Math.round((secTotal / (secCount * 4)) * 100)
                      : 0;
                  return (
                    <div
                      key={sec.id}
                      className="flex items-center justify-between rounded-lg bg-[#fafafa] px-4 py-2.5"
                    >
                      <span className="text-[14px] text-[#333]">
                        {sec.label}
                      </span>
                      <span className="text-[14px] font-semibold text-[#0d0d0d]">
                        {secScore}
                      </span>
                    </div>
                  );
                })}
              </div>

              <Button
                onClick={() => {
                  onComplete();
                  onClose();
                }}
                className="mt-8 h-auto rounded-full bg-[#0d0d0d] px-8 py-2.5 text-[15px] text-white"
              >
                Done
              </Button>
            </div>
          ) : phase === "confidence" && section ? (
            <div>
              <p className="mb-1 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666]">
                Section {currentIndex + 1} of {sections.length}
              </p>
              <h3 className="text-[18px] font-semibold text-[#0d0d0d]">
                {section.label} — Confidence
              </h3>
              <p className="mt-2 text-[14px] text-[#666]">
                How confident are you in your answers for this section?
              </p>
              <div className="mt-5 space-y-2">
                {CONFIDENCE_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    onClick={() => setConfidence(opt.value)}
                    className={`w-full rounded-xl border px-5 py-4 text-left transition-colors ${
                      confidence === opt.value
                        ? "border-[#0d0d0d] bg-[#0d0d0d] text-white"
                        : "border-[rgba(0,0,0,0.1)] text-[#333] hover:border-[#999]"
                    }`}
                  >
                    <p className="text-[15px] font-medium">{opt.label}</p>
                    <p
                      className={`mt-0.5 text-[13px] ${confidence === opt.value ? "text-white/70" : "text-[#999]"}`}
                    >
                      {opt.desc}
                    </p>
                  </button>
                ))}
              </div>
            </div>
          ) : section ? (
            <div>
              <p className="mb-1 font-mono text-[12px] font-medium uppercase tracking-[0.6px] text-[#666]">
                Section {currentIndex + 1} of {sections.length}
              </p>
              <h3 className="mb-5 text-[18px] font-semibold text-[#0d0d0d]">
                {section.label}
              </h3>
              <div className="space-y-6">
                {section.subskills.map((ss) => (
                  <div key={ss.id}>
                    <p className="mb-2 text-[15px] font-medium text-[#0d0d0d]">
                      {ss.label}
                    </p>
                    <div className="space-y-1.5">
                      {ss.tiers.map((statement, tier) => (
                        <button
                          key={tier}
                          onClick={() => handleSelectTier(ss.id, tier)}
                          className={`w-full rounded-lg border px-3 py-2 text-left text-[13px] leading-[1.5] transition-colors ${
                            answers[ss.id] === tier
                              ? "border-[#0d0d0d] bg-[#0d0d0d] text-white"
                              : "border-[rgba(0,0,0,0.1)] text-[#333] hover:border-[#999]"
                          }`}
                        >
                          <span
                            className={`mr-2 inline-block text-[11px] font-semibold ${
                              answers[ss.id] === tier
                                ? "text-white/60"
                                : "text-[#999]"
                            }`}
                          >
                            T{tier}
                          </span>
                          {statement}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </div>

        {/* Footer */}
        {phase !== "done" && (
          <div className="flex items-center justify-between border-t border-[rgba(0,0,0,0.05)] px-8 py-4">
            <div className="flex gap-2">
              {/* Section nav dots */}
              {sections.map((sec, i) => {
                const isDone =
                  completedIds.has(sec.id) ||
                  completedDuringSession.has(sec.id);
                const isCurrent = i === currentIndex;
                return (
                  <button
                    key={sec.id}
                    onClick={() => isDone && handleGoToSection(i)}
                    disabled={!isDone && !isCurrent}
                    title={sec.label}
                    className={`h-2.5 w-2.5 rounded-full transition-colors ${
                      isCurrent
                        ? "bg-[#0d0d0d]"
                        : isDone
                          ? "bg-[#18E299] hover:bg-[#0fa76e]"
                          : "bg-[#e5e5e5]"
                    }`}
                  />
                );
              })}
            </div>
            <div className="flex gap-2">
              {phase === "confidence" ? (
                <>
                  <Button
                    variant="outline"
                    onClick={() => setPhase("subskills")}
                    className="h-auto rounded-full px-5 py-2 text-[14px]"
                  >
                    <ChevronLeft className="mr-1 h-4 w-4" />
                    Back
                  </Button>
                  <Button
                    onClick={handleConfidenceDone}
                    disabled={!confidence || submitting}
                    className="h-auto rounded-full bg-[#0d0d0d] px-5 py-2 text-[14px] text-white"
                  >
                    {submitting ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                      <Check className="mr-1 h-4 w-4" />
                    )}
                    Save Section
                  </Button>
                </>
              ) : (
                <Button
                  onClick={() => setPhase("confidence")}
                  disabled={!allSubskillsAnswered}
                  className="h-auto rounded-full bg-[#0d0d0d] px-5 py-2 text-[14px] text-white"
                >
                  Continue
                  <ChevronRight className="ml-1 h-4 w-4" />
                </Button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
