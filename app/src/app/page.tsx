import React from "react";
import HeroSection from "@/components/HeroSection";
import FeatureGrid from "@/components/FeatureGrid";
import ProcessFlow from "@/components/ProcessFlow";
import PricingCards from "@/components/PricingCards";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  ArrowRight,
  CheckCircle,
  ExternalLink,
  Github,
  Twitter,
} from "lucide-react";

export default function Home() {
  return (
    <div className="min-h-screen bg-background">
      {/* Hero Section */}
      <HeroSection />

      {/* Social Proof Bar */}
      <section className="w-full py-12 bg-muted/50">
        <div className="container px-4 md:px-6">
          <div className="flex flex-col items-center justify-center space-y-4">
            <h3 className="text-center text-sm font-medium text-muted-foreground">
              TRUSTED BY INNOVATIVE SALES TEAMS WORLDWIDE
            </h3>
            <div className="flex flex-wrap items-center justify-center gap-8 md:gap-12 opacity-70">
              <img
                src="https://api.dicebear.com/7.x/initials/svg?seed=Acme"
                alt="Acme Inc"
                className="h-8 w-auto"
              />
              <img
                src="https://api.dicebear.com/7.x/initials/svg?seed=Globex"
                alt="Globex"
                className="h-8 w-auto"
              />
              <img
                src="https://api.dicebear.com/7.x/initials/svg?seed=Stark"
                alt="Stark Industries"
                className="h-8 w-auto"
              />
              <img
                src="https://api.dicebear.com/7.x/initials/svg?seed=Wayne"
                alt="Wayne Enterprises"
                className="h-8 w-auto"
              />
              <img
                src="https://api.dicebear.com/7.x/initials/svg?seed=Hooli"
                alt="Hooli"
                className="h-8 w-auto"
              />
              <img
                src="https://api.dicebear.com/7.x/initials/svg?seed=Initech"
                alt="Initech"
                className="h-8 w-auto"
              />
            </div>
          </div>
        </div>
      </section>

      {/* Feature Grid */}
      <FeatureGrid />

      {/* Process Flow */}
      <ProcessFlow />

      {/* Metrics Highlight */}
      <section className="w-full py-20 bg-primary text-primary-foreground">
        <div className="container px-4 md:px-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 text-center">
            <div className="space-y-2">
              <h3 className="text-4xl font-bold">{"<1%"}</h3>
              <p className="text-lg text-primary-foreground/80">
                Bounce rate with our verified emails
              </p>
            </div>
            <div className="space-y-2">
              <h3 className="text-4xl font-bold">{"<30"}</h3>
              <p className="text-lg text-primary-foreground/80">
                Minutes to fully onboard your team
              </p>
            </div>
            <div className="space-y-2">
              <h3 className="text-4xl font-bold">70%</h3>
              <p className="text-lg text-primary-foreground/80">
                Personalization efficiency with AI
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Pricing Cards */}
      <PricingCards />

      {/* CTA Section */}
      <section className="w-full py-20 bg-muted/30">
        <div className="container px-4 md:px-6">
          <div className="flex flex-col items-center justify-center space-y-6 text-center">
            <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl">
              Ready to transform your outreach?
            </h2>
            <p className="max-w-[700px] text-muted-foreground md:text-xl/relaxed">
              Join thousands of sales teams who've improved their response rates
              with Outreachly AI.
            </p>
            <div className="flex flex-col sm:flex-row gap-4">
              <Button size="lg" className="h-12 px-8">
                Get Started Free
                <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
              <Button size="lg" variant="outline" className="h-12 px-8">
                Book a Demo
              </Button>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="w-full py-12 bg-background border-t">
        <div className="container px-4 md:px-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            <div className="space-y-4">
              <h4 className="text-lg font-semibold">Outreachly AI</h4>
              <p className="text-sm text-muted-foreground">
                Smarter lead enrichment and cold outreach that actually gets
                replies.
              </p>
              <div className="flex space-x-4">
                <Twitter className="h-5 w-5 text-muted-foreground hover:text-foreground transition-colors" />
                <Github className="h-5 w-5 text-muted-foreground hover:text-foreground transition-colors" />
              </div>
            </div>
            <div className="space-y-4">
              <h4 className="text-sm font-semibold">Product</h4>
              <ul className="space-y-2 text-sm">
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Features
                  </a>
                </li>
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Pricing
                  </a>
                </li>
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    API
                  </a>
                </li>
              </ul>
            </div>
            <div className="space-y-4">
              <h4 className="text-sm font-semibold">Resources</h4>
              <ul className="space-y-2 text-sm">
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Documentation
                  </a>
                </li>
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Guides
                  </a>
                </li>
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Support
                  </a>
                </li>
              </ul>
            </div>
            <div className="space-y-4">
              <h4 className="text-sm font-semibold">Legal</h4>
              <ul className="space-y-2 text-sm">
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Terms
                  </a>
                </li>
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Privacy
                  </a>
                </li>
                <li>
                  <a
                    href="#"
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Compliance
                  </a>
                </li>
              </ul>
            </div>
          </div>
          <Separator className="my-8" />
          <div className="flex flex-col md:flex-row items-center justify-between">
            <p className="text-sm text-muted-foreground">
              © 2023 Outreachly AI. All rights reserved.
            </p>
            <div className="flex items-center space-x-4 mt-4 md:mt-0">
              <a
                href="#"
                className="text-sm text-muted-foreground hover:text-foreground transition-colors flex items-center"
              >
                <CheckCircle className="mr-1 h-3 w-3" /> GDPR Compliant
              </a>
              <a
                href="#"
                className="text-sm text-muted-foreground hover:text-foreground transition-colors flex items-center"
              >
                <CheckCircle className="mr-1 h-3 w-3" /> SOC 2 Certified
              </a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
