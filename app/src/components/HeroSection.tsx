"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { Button } from "./ui/button";
import { ArrowRight } from "lucide-react";

interface HeroSectionProps {
  headline?: string;
  primaryCTA?: string;
}

const HeroSection = ({
  headline = "Land your SWE internship.",
  primaryCTA = "Get Started",
}: HeroSectionProps) => {
  const router = useRouter();
  return (
    <section className="w-full bg-background py-20 md:py-28 lg:py-32 relative overflow-hidden">
      <div className="absolute inset-0 bg-grid-pattern opacity-[0.03] pointer-events-none"></div>

      {/* Decorative elements */}
      <div className="absolute -top-20 -right-20 w-64 h-64 bg-brand-light dark:bg-brand-deep/10 rounded-full blur-3xl"></div>
      <div className="absolute -bottom-32 -left-20 w-80 h-80 bg-brand-light/50 dark:bg-brand-deep/10 rounded-full blur-3xl"></div>

      <div className="container mx-auto px-4 relative z-10">
        <div className="max-w-3xl mx-auto text-center">
          <h1 className="text-4xl md:text-5xl lg:text-[64px] font-semibold leading-[1.15] tracking-[-0.02em] text-foreground mb-8">
            {headline}
          </h1>

          <div className="flex items-center justify-center mb-12">
            <Button
              size="lg"
              variant="brand"
              className="h-12 px-8 text-base font-medium"
              onClick={() => router.push("/auth")}
            >
              {primaryCTA}
              <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Hero image/mockup */}
        <div className="mt-16 max-w-5xl mx-auto relative">
          <div className="aspect-[16/9] rounded-xl overflow-hidden border border-border/50 shadow-2xl">
            <img
              src="https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=1200&q=80"
              alt="Pulse Dashboard"
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-background/80 to-transparent"></div>
          </div>

          {/* Floating UI elements for visual interest */}
          <div className="absolute -top-6 -right-6 bg-card border border-border/50 rounded-lg p-4 shadow-card hidden md:block">
            <div className="flex items-center gap-2">
              <div className="h-3 w-3 rounded-full bg-brand-accent"></div>
              <span className="text-sm font-medium">Readiness: 82%</span>
            </div>
          </div>

          <div className="absolute -bottom-6 -left-6 bg-card border border-border/50 rounded-lg p-4 shadow-card hidden md:block">
            <div className="flex items-center gap-2">
              <div className="h-3 w-3 rounded-full bg-brand-accent"></div>
              <span className="text-sm font-medium">5 Goals on Track</span>
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
