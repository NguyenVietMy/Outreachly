import React from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Target,
  FileText,
  Flag,
  Zap,
  Sparkles,
  BookOpen,
} from "lucide-react";

interface FeatureCardProps {
  icon: React.ReactNode;
  title: string;
  description: string;
}

const FeatureCard = ({
  icon,
  title,
  description = "Feature description",
}: FeatureCardProps) => {
  return (
    <Card className="bg-card border hover:shadow-md transition-shadow duration-300">
      <CardHeader className="pb-2">
        <div className="mb-2 p-2 rounded-lg bg-brand-light dark:bg-brand-deep/20 w-fit">{icon}</div>
        <CardTitle className="text-xl">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <CardDescription className="text-sm text-muted-foreground">
          {description}
        </CardDescription>
      </CardContent>
    </Card>
  );
};

const FeatureGrid = () => {
  const features = [
    {
      icon: <Target className="h-6 w-6 text-brand-deep" />,
      title: "SWE Readiness",
      description:
        "AI-powered assessments across algorithms, system design, and behavioral skills to gauge your interview readiness.",
    },
    {
      icon: <FileText className="h-6 w-6 text-brand-deep" />,
      title: "Resume Scoring",
      description:
        "Upload your resume and get instant AI feedback on impact, structure, and how it stacks up for top companies.",
    },
    {
      icon: <Flag className="h-6 w-6 text-brand-deep" />,
      title: "Goal Tracking",
      description:
        "Set career milestones with deadlines and track your progress week over week to stay on course.",
    },
    {
      icon: <Zap className="h-6 w-6 text-brand-deep" />,
      title: "Integration Hub",
      description:
        "Connect GitHub, Obsidian, Slack, and Linear to automatically track your engineering activity.",
    },
    {
      icon: <Sparkles className="h-6 w-6 text-brand-deep" />,
      title: "AI Digest",
      description:
        "Personalized weekly insights on your activity trends, strengths, and areas to focus on next.",
    },
    {
      icon: <BookOpen className="h-6 w-6 text-brand-deep" />,
      title: "Study Tasks",
      description:
        "AI-generated study plans and tasks tailored to your skill gaps, so you always know what to work on.",
    },
  ];

  return (
    <section className="py-20 px-4 bg-background">
      <div className="container mx-auto max-w-7xl">
        <div className="text-center mb-12">
          <h2 className="text-[40px] font-semibold tracking-[-0.02em] leading-[1.10] mb-2">
            Everything You Need
          </h2>
          <p className="text-muted-foreground max-w-2xl mx-auto">
            From readiness assessments to AI-generated study plans — tools built
            to help you get internship-ready.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {features.map((feature, index) => (
            <FeatureCard
              key={index}
              icon={feature.icon}
              title={feature.title}
              description={feature.description}
            />
          ))}
        </div>
      </div>
    </section>
  );
};

export default FeatureGrid;
