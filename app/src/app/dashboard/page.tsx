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

export default function Dashboard() {
  const handleOnboardingComplete = (itemId: string) => {
    console.log(`Onboarding item completed: ${itemId}`);
    // Add your onboarding completion logic here
  };

  const handleViewCampaignDetails = (campaignId: string) => {
    console.log(`View campaign details: ${campaignId}`);
    // Navigate to campaign details page
  };

  const handleImportLeads = () => {
    console.log("Import leads clicked");
    // Navigate to import leads page
  };

  const handleViewLeadList = (listId: string) => {
    console.log(`View lead list: ${listId}`);
    // Navigate to lead list details
  };

  const handleConfigureDomain = (profileId: string) => {
    console.log(`Configure domain: ${profileId}`);
    // Navigate to domain configuration
  };

  const handleViewProfileDetails = (profileId: string) => {
    console.log(`View profile details: ${profileId}`);
    // Navigate to profile details
  };

  const handleViewActivityDetails = (activityId: string) => {
    console.log(`View activity details: ${activityId}`);
    // Navigate to activity details
  };

  const handleViewPrivacyPolicy = () => {
    console.log("View privacy policy");
    // Open privacy policy
  };

  const handleViewTerms = () => {
    console.log("View terms of service");
    // Open terms of service
  };

  const handleViewDPA = () => {
    console.log("View data processing agreement");
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
