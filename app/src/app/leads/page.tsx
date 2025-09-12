"use client";

import { useState } from "react";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { API_BASE_URL } from "@/lib/config";

export default function LeadsPage() {
  const [leadId, setLeadId] = useState("");
  const [listId, setListId] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const enrichLead = async () => {
    if (!leadId) return;
    setLoading(true);
    setMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/leads/${leadId}/enrich`, {
        method: "POST",
        credentials: "include",
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data?.error || "Failed");
      setMessage(`Queued job ${data.jobId}`);
    } catch (e: any) {
      setMessage(e?.message || "Error");
    } finally {
      setLoading(false);
    }
  };

  const enrichList = async () => {
    if (!listId) return;
    setLoading(true);
    setMessage(null);
    try {
      const res = await fetch(
        `${API_BASE_URL}/api/leads/enrich?list=${encodeURIComponent(listId)}`,
        {
          method: "POST",
          credentials: "include",
        }
      );
      const data = await res.json();
      if (!res.ok) throw new Error(data?.error || "Failed");
      setMessage(`Queued ${data.count || 0} jobs`);
    } catch (e: any) {
      setMessage(e?.message || "Error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6 max-w-3xl mx-auto">
          <div className="mb-6 mt-[100px]">
            <h1 className="text-2xl font-bold">Leads</h1>
            <p className="text-sm text-muted-foreground">
              Minimal page for enrichment actions.
            </p>
          </div>

          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>Enrich Single Lead</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <Input
                  placeholder="Lead ID (UUID)"
                  value={leadId}
                  onChange={(e) => setLeadId(e.target.value)}
                />
                <Button onClick={enrichLead} disabled={!leadId || loading}>
                  {loading ? "Processing..." : "Enrich Lead"}
                </Button>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Bulk Enrich by List</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <Input
                  placeholder="List ID (UUID)"
                  value={listId}
                  onChange={(e) => setListId(e.target.value)}
                />
                <Button onClick={enrichList} disabled={!listId || loading}>
                  {loading ? "Processing..." : "Enrich List"}
                </Button>
              </CardContent>
            </Card>

            {message && (
              <Card>
                <CardContent className="p-4 text-sm">{message}</CardContent>
              </Card>
            )}
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
