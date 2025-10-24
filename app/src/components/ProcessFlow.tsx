"use client";

import React, { useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import {
  Upload,
  CheckCircle,
  Database,
  PenTool,
  BarChart3,
} from "lucide-react";

interface ProcessStepProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  step: number;
  isActive: boolean;
  onClick: () => void;
}

const ProcessStep = ({
  icon,
  title,
  description,
  step,
  isActive,
  onClick,
}: ProcessStepProps) => {
  return (
    <Card
      className={`relative cursor-pointer transition-all duration-300 ${isActive ? "border-primary shadow-md" : "border-border"} bg-background`}
      onClick={onClick}
    >
      <CardContent className="p-6">
        <div className="absolute -top-3 -left-3 w-6 h-6 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-xs font-bold">
          {step}
        </div>
        <div className="flex flex-col items-center text-center gap-3">
          <div
            className={`p-3 rounded-full ${isActive ? "bg-primary/10" : "bg-muted"} transition-colors duration-300`}
          >
            {icon}
          </div>
          <h3 className="font-semibold text-lg">{title}</h3>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>
      </CardContent>
    </Card>
  );
};

const ProcessFlow = () => {
  const [activeStep, setActiveStep] = useState(1);

  const steps = [
    {
      icon: <Upload className="w-6 h-6" />,
      title: "Import Leads",
      description: "Upload your lead list or connect your CRM to get started",
      step: 1,
    },
    {
      icon: <CheckCircle className="w-6 h-6" />,
      title: "Verify",
      description:
        "Automatically verify email deliverability to reduce bounce rates",
      step: 2,
    },
    {
      icon: <Database className="w-6 h-6" />,
      title: "Enrich",
      description: "Add missing contact details and company information",
      step: 3,
    },
    {
      icon: <PenTool className="w-6 h-6" />,
      title: "Personalize",
      description: "AI-powered personalization for each contact",
      step: 4,
    },
    {
      icon: <BarChart3 className="w-6 h-6" />,
      title: "Send & Measure",
      description: "Launch campaigns and track performance metrics",
      step: 5,
    },
  ];

  return (
    <section className="py-16 px-4 bg-background">
      <div className="max-w-6xl mx-auto">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-bold mb-4">How Outreachly Works</h2>
          <p className="text-muted-foreground max-w-2xl mx-auto">
            Our streamlined process helps you go from raw lead lists to
            personalized outreach campaigns in minutes
          </p>
        </div>

        <div className="relative">
          {/* Process steps */}
          <div className="grid grid-cols-1 md:grid-cols-5 gap-4 md:gap-6 relative z-10">
            {steps.map((step) => (
              <ProcessStep
                key={step.step}
                icon={step.icon}
                title={step.title}
                description={step.description}
                step={step.step}
                isActive={activeStep === step.step}
                onClick={() => setActiveStep(step.step)}
              />
            ))}
          </div>

          {/* Connection lines (visible on md screens and up) */}
          <div className="hidden md:block absolute top-1/2 left-0 w-full h-0.5 bg-border -translate-y-1/2 z-0" />
        </div>
      </div>
    </section>
  );
};

export default ProcessFlow;
