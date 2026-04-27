"use client";

import { useSearchParams, useRouter } from "next/navigation";
import { Suspense } from "react";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Users, Upload } from "lucide-react";
import LeadsTabContent from "@/components/workspace/LeadsTabContent";
import ImportTabContent from "@/components/workspace/ImportTabContent";

const VALID_TABS = ["leads", "import"] as const;
type WorkspaceTab = (typeof VALID_TABS)[number];

function WorkspaceContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const tabParam = searchParams.get("tab") || "leads";
  const activeTab: WorkspaceTab = VALID_TABS.includes(tabParam as WorkspaceTab)
    ? (tabParam as WorkspaceTab)
    : "leads";

  const handleTabChange = (value: string) => {
    router.replace(`/workspace?tab=${value}`, { scroll: false });
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6 max-w-7xl mx-auto">
          <Tabs value={activeTab} onValueChange={handleTabChange} className="space-y-6">
            <TabsList className="grid w-full grid-cols-2 h-auto">
              <TabsTrigger value="leads" className="flex items-center gap-2 py-2.5">
                <Users className="h-4 w-4" />
                <span className="hidden sm:inline">Leads</span>
              </TabsTrigger>
              <TabsTrigger value="import" className="flex items-center gap-2 py-2.5">
                <Upload className="h-4 w-4" />
                <span className="hidden sm:inline">Import</span>
              </TabsTrigger>
            </TabsList>

            <TabsContent value="leads">
              <LeadsTabContent />
            </TabsContent>
            <TabsContent value="import">
              <ImportTabContent />
            </TabsContent>
          </Tabs>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}

export default function WorkspacePage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brand-accent"></div>
      </div>
    }>
      <WorkspaceContent />
    </Suspense>
  );
}
