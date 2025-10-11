"use client";

import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import HeroKPIs from "@/components/dashboard/HeroKPIs";
import OnboardingChecklist from "@/components/dashboard/OnboardingChecklist";
import CampaignSnapshot from "@/components/dashboard/CampaignSnapshot";
import EngagementTrends from "@/components/dashboard/EngagementTrends";
import LeadListOverview from "@/components/dashboard/LeadListOverview";
import SendingProfileHealth from "@/components/dashboard/SendingProfileHealth";
import RecentActivityFeed from "@/components/dashboard/RecentActivityFeed";
import ComplianceTrustCues from "@/components/dashboard/ComplianceTrustCues";
import { useRouter } from "next/navigation";

export default function Dashboard() {
  const router = useRouter();
  const handleOnboardingComplete = (itemId: string) => {
    // Add your onboarding completion logic here
  };

  const handleViewCampaignDetails = (campaignId: string) => {
    // Navigate to campaign details page
  };

  const handleImportLeads = () => {
    // Navigate to import leads page
  };

  const handleViewLeadList = (listId: string) => {
    // Navigate to lead list details
  };

  const handleConfigureDomain = (profileId: string) => {
    // Navigate to domain configuration
  };

  const handleViewProfileDetails = (profileId: string) => {
    // Navigate to profile details
  };

  const handleViewActivityDetails = (activityId: string) => {
    // Navigate to activity details
  };

  const handleViewPrivacyPolicy = () => {
    router.push("/privacy");
  };

  const handleViewTerms = () => {
    router.push("/terms");
  };

  const handleViewDPA = () => {
    // Open DPA
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6">
          {/* Header */}
          <div className="mb-8 mt-[100px]">
            <h1 className="text-3xl font-bold text-gray-900">Welcome back!</h1>
            <p className="text-gray-600 mt-2">
              Here's what's happening with your outreach campaigns.
            </p>
          </div>

          {/* Hero KPIs */}
          <HeroKPIs />

          {/* Main Content Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Left Column - Main Content */}
            <div className="lg:col-span-2 space-y-8">
              {/* Onboarding Checklist */}
              <OnboardingChecklist onComplete={handleOnboardingComplete} />

              {/* Campaign Snapshot */}
              <CampaignSnapshot onViewDetails={handleViewCampaignDetails} />

              {/* Engagement Trends */}
              <EngagementTrends />

              {/* Lead List Overview */}
              <LeadListOverview
                onImportLeads={handleImportLeads}
                onViewList={handleViewLeadList}
              />

              {/* Sending Profile Health */}
              <SendingProfileHealth
                onConfigureDomain={handleConfigureDomain}
                onViewDetails={handleViewProfileDetails}
              />
            </div>

            {/* Right Column - Sidebar */}
            <div className="space-y-8">
              {/* Recent Activity Feed */}
              <RecentActivityFeed onViewDetails={handleViewActivityDetails} />
            </div>
          </div>

          {/* Compliance & Trust Cues */}
          <ComplianceTrustCues
            onViewPrivacyPolicy={handleViewPrivacyPolicy}
            onViewTerms={handleViewTerms}
            onViewDPA={handleViewDPA}
          />
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
