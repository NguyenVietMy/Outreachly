"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { API_BASE_URL } from "@/lib/config";

interface EnrichmentPreviewProps {
  leadId: string;
  onClose: () => void;
}

interface PreviewData {
  leadId: string;
  timestamp: string;
  currentData: {
    firstName?: string;
    lastName?: string;
    email?: string;
    company?: string;
    domain?: string;
    title?: string;
    phone?: string;
    linkedinUrl?: string;
    country?: string;
    state?: string;
    city?: string;
  };
  hunterData: {
    emailFinder?: any;
    emailVerifier?: any;
    domainSearch?: any;
    companySearch?: any;
    emailFinderError?: string;
    emailVerifierError?: string;
    domainSearchError?: string;
    companySearchError?: string;
  };
  suggestedChanges: {
    email?: string;
    emailConfidence?: number;
    emailStatus?: string;
    emailRisk?: string;
    emailRiskReason?: string;
    company?: string;
    title?: string;
    country?: string;
  };
}

export default function EnrichmentPreview({
  leadId,
  onClose,
}: EnrichmentPreviewProps) {
  const [previewData, setPreviewData] = useState<PreviewData | null>(null);
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [acceptedChanges, setAcceptedChanges] = useState<Record<string, any>>(
    {}
  );

  const loadPreview = async () => {
    setLoading(true);
    setMessage(null);
    try {
      const res = await fetch(
        `${API_BASE_URL}/api/leads/${leadId}/enrich/preview`,
        {
          method: "POST",
          credentials: "include",
        }
      );
      const data = await res.json();
      if (!res.ok) throw new Error(data?.error || "Failed to load preview");
      setPreviewData(data);
    } catch (e: any) {
      setMessage(e?.message || "Error loading preview");
    } finally {
      setLoading(false);
    }
  };

  const applyChanges = async () => {
    setApplying(true);
    setMessage(null);
    try {
      const res = await fetch(
        `${API_BASE_URL}/api/leads/${leadId}/enrich/apply`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ acceptedChanges }),
        }
      );
      const data = await res.json();
      if (!res.ok) throw new Error(data?.error || "Failed to apply changes");
      setMessage("Changes applied successfully!");
      setTimeout(() => {
        onClose();
      }, 2000);
    } catch (e: any) {
      setMessage(e?.message || "Error applying changes");
    } finally {
      setApplying(false);
    }
  };

  const toggleChange = (field: string, value: any) => {
    setAcceptedChanges((prev) => ({
      ...prev,
      [field]: prev[field] ? undefined : value,
    }));
  };

  const hasAcceptedChanges = Object.values(acceptedChanges).some(
    (v) => v !== undefined
  );

  if (!previewData) {
    return (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
        <Card className="w-full max-w-4xl max-h-[90vh] overflow-hidden">
          <CardHeader>
            <CardTitle>Enrichment Preview</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Button onClick={loadPreview} disabled={loading}>
              {loading ? "Loading Preview..." : "Load Enrichment Preview"}
            </Button>
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            {message && <div className="text-sm text-red-600">{message}</div>}
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <Card className="w-full max-w-6xl max-h-[90vh] overflow-hidden">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Enrichment Preview - Lead {leadId}</CardTitle>
          <Button variant="outline" onClick={onClose}>
            Close
          </Button>
        </CardHeader>
        <CardContent className="overflow-y-auto max-h-[calc(90vh-120px)]">
          <div className="space-y-6">
            {/* Current vs Suggested Changes */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Current Data</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  {Object.entries(previewData.currentData).map(
                    ([key, value]) => (
                      <div key={key} className="flex justify-between">
                        <span className="font-medium capitalize">{key}:</span>
                        <span className="text-sm text-muted-foreground">
                          {value || "N/A"}
                        </span>
                      </div>
                    )
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Suggested Changes</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  {Object.entries(previewData.suggestedChanges).map(
                    ([key, value]) => (
                      <div
                        key={key}
                        className="flex items-center justify-between"
                      >
                        <div className="flex-1">
                          <span className="font-medium capitalize">{key}:</span>
                          <span className="ml-2 text-sm text-muted-foreground">
                            {value}
                          </span>
                          {key === "email" &&
                            previewData.suggestedChanges.emailConfidence && (
                              <Badge variant="secondary" className="ml-2">
                                {(
                                  previewData.suggestedChanges.emailConfidence *
                                  100
                                ).toFixed(0)}
                                % confidence
                              </Badge>
                            )}
                          {key === "emailStatus" && (
                            <Badge
                              variant={
                                previewData.suggestedChanges.emailRisk ===
                                "risky"
                                  ? "destructive"
                                  : previewData.suggestedChanges.emailRisk ===
                                      "safe"
                                    ? "default"
                                    : "secondary"
                              }
                              className="ml-2"
                            >
                              {value}
                              {previewData.suggestedChanges.emailRisk ===
                                "risky" && " (Risky)"}
                            </Badge>
                          )}
                        </div>
                        <Button
                          size="sm"
                          variant={acceptedChanges[key] ? "default" : "outline"}
                          onClick={() => toggleChange(key, value)}
                        >
                          {acceptedChanges[key] ? "Accept" : "Accept"}
                        </Button>
                      </div>
                    )
                  )}
                  {Object.keys(previewData.suggestedChanges).length === 0 && (
                    <div className="text-sm text-muted-foreground">
                      No suggested changes
                    </div>
                  )}

                  {/* Email Risk Warning */}
                  {previewData.suggestedChanges.emailRisk === "risky" && (
                    <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-md">
                      <div className="flex items-center">
                        <div className="text-red-600 font-medium text-sm">
                          ⚠️ Email Risk Warning
                        </div>
                      </div>
                      <div className="text-red-700 text-sm mt-1">
                        {previewData.suggestedChanges.emailRiskReason}
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>

            {/* Hunter Data Details */}
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Hunter Data</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {previewData.hunterData.emailFinder && (
                  <div>
                    <h4 className="font-medium">Email Finder</h4>
                    <pre className="text-xs bg-muted p-2 rounded overflow-x-auto">
                      {JSON.stringify(
                        previewData.hunterData.emailFinder,
                        null,
                        2
                      )}
                    </pre>
                  </div>
                )}

                {previewData.hunterData.emailVerifier && (
                  <div>
                    <h4 className="font-medium">Email Verifier</h4>
                    <pre className="text-xs bg-muted p-2 rounded overflow-x-auto">
                      {JSON.stringify(
                        previewData.hunterData.emailVerifier,
                        null,
                        2
                      )}
                    </pre>
                  </div>
                )}

                {previewData.hunterData.domainSearch && (
                  <div>
                    <h4 className="font-medium">Domain Search</h4>
                    <pre className="text-xs bg-muted p-2 rounded overflow-x-auto">
                      {JSON.stringify(
                        previewData.hunterData.domainSearch,
                        null,
                        2
                      )}
                    </pre>
                  </div>
                )}

                {previewData.hunterData.companySearch && (
                  <div>
                    <h4 className="font-medium">Company Search</h4>
                    <pre className="text-xs bg-muted p-2 rounded overflow-x-auto">
                      {JSON.stringify(
                        previewData.hunterData.companySearch,
                        null,
                        2
                      )}
                    </pre>
                  </div>
                )}

                {/* Show errors if any */}
                {Object.entries(previewData.hunterData).some(([key, value]) =>
                  key.includes("Error")
                ) && (
                  <div>
                    <h4 className="font-medium text-red-600">Errors</h4>
                    {Object.entries(previewData.hunterData).map(
                      ([key, value]) =>
                        key.includes("Error") && (
                          <div key={key} className="text-sm text-red-600">
                            <strong>{key}:</strong> {value}
                          </div>
                        )
                    )}
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Action Buttons */}
            <div className="flex gap-2 justify-end">
              <Button variant="outline" onClick={onClose}>
                Cancel
              </Button>
              <Button
                onClick={applyChanges}
                disabled={!hasAcceptedChanges || applying}
              >
                {applying
                  ? "Applying..."
                  : `Apply ${Object.keys(acceptedChanges).filter((k) => acceptedChanges[k]).length} Changes`}
              </Button>
            </div>

            {message && (
              <div
                className={`text-sm ${message.includes("success") ? "text-green-600" : "text-red-600"}`}
              >
                {message}
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
