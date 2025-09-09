"use client";

import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Shield,
  Users,
  FileText,
  ExternalLink,
  CheckCircle,
  AlertTriangle,
  Info,
} from "lucide-react";

interface ComplianceStats {
  globalUnsubscribes: number;
  suppressionListSize: number;
  complianceScore: number;
  lastAuditDate: string;
}

interface ComplianceTrustCuesProps {
  stats?: ComplianceStats;
  onViewPrivacyPolicy?: () => void;
  onViewTerms?: () => void;
  onViewDPA?: () => void;
}

export default function ComplianceTrustCues({
  stats,
  onViewPrivacyPolicy,
  onViewTerms,
  onViewDPA,
}: ComplianceTrustCuesProps) {
  // Mock data for demonstration
  const mockStats: ComplianceStats = {
    globalUnsubscribes: 47,
    suppressionListSize: 1234,
    complianceScore: 98,
    lastAuditDate: "2024-01-15",
  };

  const complianceData = stats || mockStats;

  const getComplianceScoreColor = (score: number) => {
    if (score >= 95) return "text-green-600";
    if (score >= 85) return "text-yellow-600";
    return "text-red-600";
  };

  const getComplianceScoreBadge = (score: number) => {
    if (score >= 95) {
      return <Badge className="bg-green-100 text-green-800">Excellent</Badge>;
    }
    if (score >= 85) {
      return <Badge className="bg-yellow-100 text-yellow-800">Good</Badge>;
    }
    return <Badge className="bg-red-100 text-red-800">Needs Attention</Badge>;
  };

  return (
    <div className="space-y-4">
      {/* Compliance Stats */}
      <Card>
        <CardContent className="p-4">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="text-center">
              <div className="flex items-center justify-center space-x-1 mb-1">
                <Users className="h-4 w-4 text-gray-500" />
                <span className="text-2xl font-bold text-gray-900">
                  {complianceData.globalUnsubscribes}
                </span>
              </div>
              <p className="text-xs text-gray-500">Global Unsubscribes</p>
            </div>

            <div className="text-center">
              <div className="flex items-center justify-center space-x-1 mb-1">
                <Shield className="h-4 w-4 text-gray-500" />
                <span className="text-2xl font-bold text-gray-900">
                  {complianceData.suppressionListSize.toLocaleString()}
                </span>
              </div>
              <p className="text-xs text-gray-500">Suppression List Size</p>
            </div>

            <div className="text-center">
              <div className="flex items-center justify-center space-x-1 mb-1">
                <CheckCircle className="h-4 w-4 text-gray-500" />
                <span
                  className={`text-2xl font-bold ${getComplianceScoreColor(complianceData.complianceScore)}`}
                >
                  {complianceData.complianceScore}%
                </span>
              </div>
              <p className="text-xs text-gray-500">Compliance Score</p>
              <div className="mt-1">
                {getComplianceScoreBadge(complianceData.complianceScore)}
              </div>
            </div>

            <div className="text-center">
              <div className="flex items-center justify-center space-x-1 mb-1">
                <FileText className="h-4 w-4 text-gray-500" />
                <span className="text-sm font-medium text-gray-900">
                  {new Date(complianceData.lastAuditDate).toLocaleDateString()}
                </span>
              </div>
              <p className="text-xs text-gray-500">Last Audit</p>
            </div>
          </div>
        </CardContent>
      </Card>

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

      {/* Compliance Tips */}
      <Card>
        <CardContent className="p-4">
          <div className="space-y-3">
            <div className="flex items-start space-x-2">
              <Info className="h-5 w-5 text-blue-600 mt-0.5" />
              <div>
                <h4 className="text-sm font-medium text-gray-900 mb-1">
                  Compliance Best Practices
                </h4>
                <div className="space-y-2 text-xs text-gray-600">
                  <div className="flex items-center space-x-2">
                    <CheckCircle className="h-3 w-3 text-green-600" />
                    <span>Always include unsubscribe links in your emails</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <CheckCircle className="h-3 w-3 text-green-600" />
                    <span>
                      Maintain bounce rates below 1% for optimal deliverability
                    </span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <CheckCircle className="h-3 w-3 text-green-600" />
                    <span>
                      Keep complaint rates below 0.1% to avoid spam filters
                    </span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <CheckCircle className="h-3 w-3 text-green-600" />
                    <span>Regularly clean your suppression lists</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Warning Indicators */}
      {complianceData.complianceScore < 95 && (
        <Card className="border-yellow-200 bg-yellow-50">
          <CardContent className="p-4">
            <div className="flex items-start space-x-2">
              <AlertTriangle className="h-5 w-5 text-yellow-600 mt-0.5" />
              <div>
                <h4 className="text-sm font-medium text-yellow-800 mb-1">
                  Compliance Attention Needed
                </h4>
                <p className="text-xs text-yellow-700">
                  Your compliance score is below 95%. Review your email
                  practices and consider cleaning your lists to improve
                  deliverability.
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Footer */}
      <div className="text-center py-4 border-t border-gray-200">
        <p className="text-xs text-gray-500">
          Outreachly is committed to maintaining the highest standards of email
          compliance and deliverability.
        </p>
        <p className="text-xs text-gray-400 mt-1">
          Last updated: {new Date().toLocaleDateString()}
        </p>
      </div>
    </div>
  );
}

