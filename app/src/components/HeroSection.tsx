"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { Button } from "./ui/button";
import { ArrowRight, BarChart3, Mail, Shield } from "lucide-react";

interface HeroSectionProps {
  headline?: string;
  subtext?: string;
  primaryCTA?: string;
  secondaryCTA?: string;
}

const HeroSection = ({
  headline = "Smarter lead enrichment. Cold outreach that actually gets replies.",
  subtext = "Leverage AI-powered personalization, enhanced deliverability, and lightning-fast campaign management to boost your sales outreach performance.",
  primaryCTA = "Get Started Free",
  secondaryCTA = "Book Demo",
}: HeroSectionProps) => {
  const router = useRouter();
  return (
    <section className="w-full bg-gradient-to-b from-background to-background/80 py-20 md:py-28 lg:py-32 relative overflow-hidden">
      <div className="absolute inset-0 bg-grid-pattern opacity-[0.03] pointer-events-none"></div>

      {/* Decorative elements */}
      <div className="absolute -top-20 -right-20 w-64 h-64 bg-primary/10 rounded-full blur-3xl"></div>
      <div className="absolute -bottom-32 -left-20 w-80 h-80 bg-primary/5 rounded-full blur-3xl"></div>

      <div className="container mx-auto px-4 relative z-10">
        <div className="max-w-3xl mx-auto text-center">
          <div className="inline-flex items-center px-4 py-1.5 mb-6 rounded-full bg-primary/10 text-primary text-sm font-medium">
            <span className="mr-2">✨</span>
            <span>AI-Powered Outreach Platform</span>
          </div>

          <h1 className="text-4xl md:text-5xl lg:text-6xl font-bold tracking-tight mb-6 bg-clip-text text-transparent bg-gradient-to-r from-foreground to-foreground/80">
            {headline}
          </h1>

          <p className="text-lg md:text-xl text-muted-foreground mb-8 max-w-2xl mx-auto">
            {subtext}
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-12">
            <Button
              size="lg"
              className="h-12 px-8 text-base font-medium"
              onClick={() => router.push("/auth")}
            >
              {primaryCTA}
              <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="h-12 px-8 text-base font-medium"
            >
              {secondaryCTA}
            </Button>
          </div>
        </div>

        {/* Hero image/mockup */}
        <div className="mt-16 max-w-5xl mx-auto relative">
          <div className="aspect-[16/9] rounded-xl overflow-hidden border border-border/50 shadow-2xl">
            <img
              src="https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=1200&q=80"
              alt="Outreachly AI Dashboard"
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-background/80 to-transparent"></div>
          </div>

          {/* Floating UI elements for visual interest */}
          <div className="absolute -top-6 -right-6 bg-card border border-border/50 rounded-lg p-4 shadow-lg hidden md:block">
            <div className="flex items-center gap-2">
              <div className="h-3 w-3 rounded-full bg-green-500"></div>
              <span className="text-sm font-medium">98% Delivery Rate</span>
            </div>
          </div>

          <div className="absolute -bottom-6 -left-6 bg-card border border-border/50 rounded-lg p-4 shadow-lg hidden md:block">
            <div className="flex items-center gap-2">
              <div className="h-3 w-3 rounded-full bg-blue-500"></div>
              <span className="text-sm font-medium">3.2x Reply Rate</span>
            </div>
          </div>
        </div>
      </div>

      <style jsx>{`
        .bg-grid-pattern {
          background-image: radial-gradient(
            circle,
            currentColor 1px,
            transparent 1px
          );
          background-size: 24px 24px;
        }
      `}</style>
    </section>
  );
};

export default HeroSection;
