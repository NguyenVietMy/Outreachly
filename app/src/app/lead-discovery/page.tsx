"use client";

import AuthGuard from "@/components/AuthGuard";
import DashboardLayout from "@/components/DashboardLayout";

export default function LeadDiscoveryPage() {
  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="space-y-6">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">
              Lead Discovery
            </h1>
            <p className="text-muted-foreground">
              Discover and find new leads for your campaigns
            </p>
          </div>

          <div className="flex items-center justify-center h-96 border-2 border-dashed border-gray-300 rounded-lg">
            <div className="text-center">
              <h3 className="text-lg font-medium text-gray-900 mb-2">
                Lead Discovery
              </h3>
              <p className="text-gray-500">
                This page is under construction. Lead discovery features will be
                added here.
              </p>
            </div>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
