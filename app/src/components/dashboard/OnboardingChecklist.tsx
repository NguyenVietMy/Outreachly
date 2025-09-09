"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  CheckCircle,
  Circle,
  ArrowRight,
  Upload,
  Users,
  FileText,
  Shield,
  Rocket,
} from "lucide-react";
import { useState } from "react";

interface ChecklistItem {
  id: string;
  title: string;
  description: string;
  completed: boolean;
  ctaText: string;
  ctaAction: () => void;
  icon: React.ComponentType<{ className?: string }>;
}

interface OnboardingChecklistProps {
  onComplete?: (itemId: string) => void;
}

export default function OnboardingChecklist({
  onComplete,
}: OnboardingChecklistProps) {
  const [checklistItems, setChecklistItems] = useState<ChecklistItem[]>([
    {
      id: "create-list",
      title: "Create List",
      description: "Import CSV with your leads",
      completed: false,
      ctaText: "Import CSV",
      ctaAction: () => console.log("Import CSV clicked"),
      icon: Upload,
    },
    {
      id: "verify-leads",
      title: "Verify / Enrich Leads",
      description: "Validate email addresses and add missing data",
      completed: false,
      ctaText: "Verify Leads",
      ctaAction: () => console.log("Verify leads clicked"),
      icon: Users,
    },
    {
      id: "create-template",
      title: "Create Template",
      description: "Design your first email template",
      completed: false,
      ctaText: "Create Template",
      ctaAction: () => console.log("Create template clicked"),
      icon: FileText,
    },
    {
      id: "connect-domain",
      title: "Connect Domain",
      description: "Set up SPF/DKIM/DMARC for better deliverability",
      completed: false,
      ctaText: "Setup Domain",
      ctaAction: () => console.log("Setup domain clicked"),
      icon: Shield,
    },
    {
      id: "launch-campaign",
      title: "Launch Campaign",
      description: "Send your first outreach campaign",
      completed: false,
      ctaText: "Launch Campaign",
      ctaAction: () => console.log("Launch campaign clicked"),
      icon: Rocket,
    },
  ]);

  const handleItemComplete = (itemId: string) => {
    setChecklistItems((prev) =>
      prev.map((item) =>
        item.id === itemId ? { ...item, completed: true } : item
      )
    );
    onComplete?.(itemId);
  };

  const completedCount = checklistItems.filter((item) => item.completed).length;
  const totalCount = checklistItems.length;
  const progressPercentage = (completedCount / totalCount) * 100;

  return (
    <Card className="mb-8">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg font-semibold">
            🎯 Onboarding Checklist
          </CardTitle>
          <Badge variant="secondary" className="text-xs">
            {completedCount}/{totalCount} Complete
          </Badge>
        </div>
        <div className="w-full bg-gray-200 rounded-full h-2">
          <div
            className="bg-blue-600 h-2 rounded-full transition-all duration-300"
            style={{ width: `${progressPercentage}%` }}
          />
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {checklistItems.map((item, index) => {
            const Icon = item.icon;
            return (
              <div
                key={item.id}
                className={`flex items-center space-x-4 p-4 rounded-lg border transition-all ${
                  item.completed
                    ? "bg-green-50 border-green-200"
                    : "bg-gray-50 border-gray-200 hover:bg-gray-100"
                }`}
              >
                <div className="flex-shrink-0">
                  {item.completed ? (
                    <CheckCircle className="h-6 w-6 text-green-600" />
                  ) : (
                    <Circle className="h-6 w-6 text-gray-400" />
                  )}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center space-x-2">
                    <Icon className="h-5 w-5 text-gray-500" />
                    <h3
                      className={`font-medium ${
                        item.completed ? "text-green-800" : "text-gray-900"
                      }`}
                    >
                      {item.title}
                    </h3>
                    {item.completed && (
                      <Badge
                        variant="outline"
                        className="text-xs text-green-600 border-green-300"
                      >
                        Complete
                      </Badge>
                    )}
                  </div>
                  <p
                    className={`text-sm mt-1 ${
                      item.completed ? "text-green-600" : "text-gray-500"
                    }`}
                  >
                    {item.description}
                  </p>
                </div>

                {!item.completed && (
                  <Button
                    size="sm"
                    onClick={() => handleItemComplete(item.id)}
                    className="flex items-center space-x-1"
                  >
                    <span>{item.ctaText}</span>
                    <ArrowRight className="h-4 w-4" />
                  </Button>
                )}
              </div>
            );
          })}
        </div>

        {completedCount === totalCount && (
          <div className="mt-6 p-4 bg-green-50 border border-green-200 rounded-lg text-center">
            <CheckCircle className="h-8 w-8 text-green-600 mx-auto mb-2" />
            <h3 className="font-semibold text-green-800 mb-1">
              🎉 Onboarding Complete!
            </h3>
            <p className="text-sm text-green-600">
              You're all set to run successful outreach campaigns.
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

