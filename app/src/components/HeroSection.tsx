"use client";

import React, { useRef } from "react";
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
  const videoRef = useRef<HTMLVideoElement>(null);

  const handleMouseEnter = () => {
    videoRef.current?.play();
  };

  const handleMouseLeave = () => {
    if (videoRef.current) {
      videoRef.current.pause();
    }
  };

  return (
    <section className="w-full bg-background py-20 md:py-28 lg:py-32 relative overflow-hidden">
      <div className="absolute inset-0 bg-grid-pattern opacity-[0.03] pointer-events-none"></div>

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

        <div
          className="mt-16 max-w-5xl mx-auto relative group"
          onMouseEnter={handleMouseEnter}
          onMouseLeave={handleMouseLeave}
        >
          <div className="rounded-xl overflow-hidden border border-border/50 shadow-2xl relative">
            <img
              src="/dashboard-preview.png"
              alt="Pulse Dashboard"
              className="w-full h-auto transition-opacity duration-300 group-hover:opacity-0"
            />
            <video
              ref={videoRef}
              src="/dashboard-demo.mp4"
              muted
              loop
              playsInline
              poster="/dashboard-preview.png"
              className="absolute inset-0 w-full h-full object-contain opacity-0 transition-opacity duration-300 group-hover:opacity-100"
            />
          </div>
        </div>

        <p className="mt-16 text-center text-sm text-muted-foreground">
          &copy; 2026 Pulse
        </p>
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
