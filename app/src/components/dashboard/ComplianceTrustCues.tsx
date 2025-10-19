"use client";

import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Shield, FileText, ExternalLink } from "lucide-react";

interface ComplianceTrustCuesProps {
  onViewPrivacyPolicy?: () => void;
  onViewTerms?: () => void;
  onViewDPA?: () => void;
}

export default function ComplianceTrustCues({
  onViewPrivacyPolicy,
  onViewTerms,
  onViewDPA,
}: ComplianceTrustCuesProps) {
  return (
    <div className="space-y-4">
      {/* Trust & Compliance Links */}
      <Card>
        <CardContent className="p-4">
          <div className="flex flex-col md:flex-row md:items-center justify-between space-y-3 md:space-y-0">
            <div className="flex items-center space-x-2">
              <Shield className="h-5 w-5 text-blue-600" />
              <span className="text-sm font-medium text-gray-900">
                Trust & Compliance
              </span>
            </div>

            <div className="flex flex-wrap gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={onViewPrivacyPolicy}
                className="flex items-center space-x-1"
              >
                <FileText className="h-3 w-3" />
                <span>Privacy Policy</span>
                <ExternalLink className="h-3 w-3" />
              </Button>

              <Button
                variant="outline"
                size="sm"
                onClick={onViewTerms}
                className="flex items-center space-x-1"
              >
                <FileText className="h-3 w-3" />
                <span>Terms of Service</span>
                <ExternalLink className="h-3 w-3" />
              </Button>

              <Button
                variant="outline"
                size="sm"
                onClick={onViewDPA}
                className="flex items-center space-x-1"
              >
                <FileText className="h-3 w-3" />
                <span>Data Processing Agreement</span>
                <ExternalLink className="h-3 w-3" />
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
