"use client";

import React, { useEffect } from "react";
import { useRouter } from "next/navigation";
import Header from "@/components/Header";
import HeroSection from "@/components/HeroSection";

import { useAuth } from "@/contexts/AuthContext";

const landingPageStyle = {
  backgroundColor: "#ffffff",
  backgroundImage:
    "linear-gradient(180deg, rgba(212, 250, 232, 0.9) 0%, rgba(236, 252, 244, 0.88) 26%, rgba(255, 255, 255, 0.98) 62%, #ffffff 100%), radial-gradient(circle at top center, rgba(24, 226, 153, 0.2) 0%, rgba(24, 226, 153, 0) 50%)",
} as const;

export default function Home() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && user) {
      router.push("/dashboard");
    }
  }, [user, loading, router]);

  if (loading) {
    return (
      <div className="min-h-screen" style={landingPageStyle}>
        <Header />
        <div className="flex items-center justify-center min-h-[50vh]">
          <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-brand-accent"></div>
        </div>
      </div>
    );
  }

  if (user) {
    return (
      <div className="min-h-screen" style={landingPageStyle}>
        <Header />
        <div className="flex items-center justify-center min-h-[50vh]">
          <div className="text-center">
            <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-brand-accent mx-auto mb-4"></div>
            <p className="text-muted-foreground">Redirecting to dashboard...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen" style={landingPageStyle}>
      <Header />
      <HeroSection />
    </div>
  );
}
