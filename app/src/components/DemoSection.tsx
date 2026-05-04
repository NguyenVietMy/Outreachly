"use client";

import React, { useEffect, useRef, useState } from "react";
import { Play } from "lucide-react";

const DemoSection = () => {
  const sectionRef = useRef<HTMLDivElement>(null);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.disconnect();
        }
      },
      { threshold: 0.15 }
    );
    if (sectionRef.current) observer.observe(sectionRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <section ref={sectionRef} className="w-full py-20 bg-background overflow-hidden">
      <div className="container px-4 md:px-6 max-w-5xl mx-auto">
        <div
          className="text-center mb-12 transition-all duration-700 ease-out"
          style={{
            opacity: isVisible ? 1 : 0,
            transform: isVisible ? "translateY(0)" : "translateY(32px)",
          }}
        >
          <h2 className="text-[40px] font-semibold tracking-[-0.02em] leading-[1.10] mb-2">
            See Pulse in Action
          </h2>
          <p className="text-muted-foreground max-w-2xl mx-auto">
            A quick look at how Pulse helps you prepare, track, and land your
            next role.
          </p>
        </div>

        <div
          className="transition-all duration-1000 ease-out"
          style={{
            opacity: isVisible ? 1 : 0,
            transform: isVisible ? "translateY(0) scale(1)" : "translateY(48px) scale(0.97)",
            transitionDelay: "200ms",
          }}
        >
          <div className="aspect-video rounded-xl overflow-hidden border border-border/50 shadow-2xl relative bg-gradient-to-br from-brand-light/30 via-background to-brand-light/10">
            <div
              className="absolute inset-0 opacity-[0.03]"
              style={{
                backgroundImage:
                  "radial-gradient(circle, currentColor 1px, transparent 1px)",
                backgroundSize: "24px 24px",
              }}
            />

            <div className="absolute inset-0 flex flex-col items-center justify-center gap-4">
              <div className="w-20 h-20 rounded-full bg-foreground text-background flex items-center justify-center shadow-lg cursor-pointer hover:scale-105 transition-transform duration-200">
                <Play className="w-8 h-8 ml-1" fill="currentColor" />
              </div>
              <p className="text-sm text-muted-foreground font-medium">
                Demo coming soon
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default DemoSection;
