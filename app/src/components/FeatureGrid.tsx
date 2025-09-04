import React from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Mail,
  CheckCircle,
  UserPlus,
  Send,
  BarChart3,
  Shield,
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
    <Card className="bg-white border shadow-sm hover:shadow-md transition-shadow duration-300">
      <CardHeader className="pb-2">
        <div className="mb-2 p-2 rounded-lg bg-primary/10 w-fit">{icon}</div>
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
      icon: <UserPlus className="h-6 w-6 text-primary" />,
      title: "Lead Enrichment",
      description:
        "Automatically enhance your lead data with verified contact information, social profiles, and company details.",
    },
    {
      icon: <CheckCircle className="h-6 w-6 text-primary" />,
      title: "Email Verification",
      description:
        "Ensure your emails reach real inboxes with our advanced verification system that reduces bounce rates to under 1%.",
    },
    {
      icon: <Mail className="h-6 w-6 text-primary" />,
      title: "AI Personalization",
      description:
        "Create highly personalized outreach messages that resonate with your prospects using our AI-powered content engine.",
    },
    {
      icon: <Send className="h-6 w-6 text-primary" />,
      title: "Campaign Management",
      description:
        "Design, schedule, and manage multi-step outreach campaigns with intuitive workflows and automation.",
    },
    {
      icon: <BarChart3 className="h-6 w-6 text-primary" />,
      title: "Performance Analytics",
      description:
        "Track open rates, replies, and conversions with detailed analytics to optimize your outreach strategy.",
    },
    {
      icon: <Shield className="h-6 w-6 text-primary" />,
      title: "Deliverability Controls",
      description:
        "Maximize inbox placement with smart sending controls, domain warmup, and reputation management tools.",
    },
  ];

  return (
    <section className="py-16 px-4 bg-slate-50">
      <div className="container mx-auto max-w-7xl">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-bold tracking-tight mb-2">
            Powerful Features
          </h2>
          <p className="text-muted-foreground max-w-2xl mx-auto">
            Everything you need to create successful outreach campaigns that get
            real results.
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
