import React from "react";
import { Button } from "./ui/button";
import { Check } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "./ui/card";

interface PricingTier {
  name: string;
  price: string;
  description: string;
  features: string[];
  cta: string;
  popular?: boolean;
}

export default function PricingCards() {
  const pricingTiers: PricingTier[] = [
    {
      name: "Free",
      price: "$0",
      description: "Perfect for trying out Outreachly",
      features: [
        "Up to 100 leads per month",
        "Basic email verification",
        "1 user seat",
        "Standard templates",
        "Email support",
      ],
      cta: "Get Started",
    },
    {
      name: "Pro",
      price: "$49",
      description: "For growing teams and businesses",
      features: [
        "Up to 2,500 leads per month",
        "Advanced email verification",
        "5 user seats",
        "AI personalization",
        "Campaign analytics",
        "Priority support",
      ],
      cta: "Start Free Trial",
      popular: true,
    },
    {
      name: "Enterprise",
      price: "Custom",
      description: "For large teams with advanced needs",
      features: [
        "Unlimited leads",
        "Advanced verification & enrichment",
        "Unlimited user seats",
        "Custom AI training",
        "Advanced analytics & reporting",
        "Dedicated account manager",
        "SLA guarantees",
      ],
      cta: "Contact Sales",
    },
  ];

  return (
    <section className="w-full py-16 bg-background">
      <div className="container px-4 md:px-6">
        <div className="flex flex-col items-center justify-center space-y-4 text-center mb-12">
          <h2 className="text-3xl font-bold tracking-tighter sm:text-4xl md:text-5xl">
            Simple, Transparent Pricing
          </h2>
          <p className="max-w-[700px] text-muted-foreground md:text-xl/relaxed">
            Choose the plan that's right for your outreach needs. All plans
            include our core features.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 lg:gap-8">
          {pricingTiers.map((tier, index) => (
            <Card
              key={index}
              className={`flex flex-col h-full ${tier.popular ? "border-primary shadow-lg relative" : ""}`}
            >
              {tier.popular && (
                <div className="absolute -top-4 left-0 right-0 flex justify-center">
                  <span className="bg-primary text-primary-foreground text-xs font-medium px-3 py-1 rounded-full">
                    Most Popular
                  </span>
                </div>
              )}

              <CardHeader>
                <CardTitle className="text-xl">{tier.name}</CardTitle>
                <div className="mt-4 flex items-baseline text-5xl font-extrabold">
                  {tier.price}
                  {tier.price !== "Custom" && (
                    <span className="ml-1 text-xl font-medium text-muted-foreground">
                      /month
                    </span>
                  )}
                </div>
                <CardDescription className="mt-2">
                  {tier.description}
                </CardDescription>
              </CardHeader>

              <CardContent className="flex-grow">
                <ul className="space-y-3">
                  {tier.features.map((feature, i) => (
                    <li key={i} className="flex items-start">
                      <Check className="h-5 w-5 text-primary flex-shrink-0 mr-2" />
                      <span>{feature}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>

              <CardFooter>
                <Button
                  className={`w-full ${tier.popular ? "bg-primary hover:bg-primary/90" : ""}`}
                  variant={tier.popular ? "default" : "outline"}
                >
                  {tier.cta}
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>

        <div className="mt-10 text-center">
          <p className="text-muted-foreground">
            Need a custom solution?{" "}
            <a href="#" className="font-medium text-primary hover:underline">
              Contact our sales team
            </a>{" "}
            for a tailored plan.
          </p>
        </div>
      </div>
    </section>
  );
}
