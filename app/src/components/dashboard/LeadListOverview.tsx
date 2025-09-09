"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Users,
  Upload,
  CheckCircle,
  AlertCircle,
  Clock,
  ExternalLink,
  Plus,
} from "lucide-react";

interface LeadList {
  id: string;
  name: string;
  totalLeads: number;
  verifiedLeads: number;
  enrichedLeads: number;
  lastUpdated: string;
  status: "active" | "processing" | "error";
}

interface LeadListOverviewProps {
  lists?: LeadList[];
  onImportLeads?: () => void;
  onViewList?: (listId: string) => void;
}

export default function LeadListOverview({
  lists,
  onImportLeads,
  onViewList,
}: LeadListOverviewProps) {
  // Mock data for demonstration
  const mockLists: LeadList[] = [
    {
      id: "1",
      name: "Enterprise Prospects Q4",
      totalLeads: 2540,
      verifiedLeads: 2380,
      enrichedLeads: 1890,
      lastUpdated: "2 hours ago",
      status: "active",
    },
    {
      id: "2",
      name: "SaaS Companies",
      totalLeads: 1890,
      verifiedLeads: 1750,
      enrichedLeads: 1420,
      lastUpdated: "1 day ago",
      status: "active",
    },
    {
      id: "3",
      name: "Startup Founders",
      totalLeads: 3200,
      verifiedLeads: 0,
      enrichedLeads: 0,
      lastUpdated: "5 minutes ago",
      status: "processing",
    },
    {
      id: "4",
      name: "Marketing Directors",
      totalLeads: 1200,
      verifiedLeads: 1100,
      enrichedLeads: 950,
      lastUpdated: "3 days ago",
      status: "active",
    },
    {
      id: "5",
      name: "Failed Import - Duplicates",
      totalLeads: 0,
      verifiedLeads: 0,
      enrichedLeads: 0,
      lastUpdated: "1 week ago",
      status: "error",
    },
  ];

  const leadLists = lists || mockLists;

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "active":
        return <CheckCircle className="h-4 w-4 text-green-600" />;
      case "processing":
        return <Clock className="h-4 w-4 text-yellow-600" />;
      case "error":
        return <AlertCircle className="h-4 w-4 text-red-600" />;
      default:
        return <Clock className="h-4 w-4 text-gray-600" />;
    }
  };

  const getStatusBadge = (status: string) => {
    const variants = {
      active: {
        variant: "default" as const,
        className: "bg-green-100 text-green-800",
      },
      processing: {
        variant: "secondary" as const,
        className: "bg-yellow-100 text-yellow-800",
      },
      error: {
        variant: "destructive" as const,
        className: "bg-red-100 text-red-800",
      },
    };

    const config = variants[status as keyof typeof variants] || variants.active;

    return (
      <Badge variant={config.variant} className={config.className}>
        {status.charAt(0).toUpperCase() + status.slice(1)}
      </Badge>
    );
  };

  const calculateVerificationRate = (verified: number, total: number) => {
    if (total === 0) return 0;
    return Math.round((verified / total) * 100);
  };

  const calculateEnrichmentRate = (enriched: number, total: number) => {
    if (total === 0) return 0;
    return Math.round((enriched / total) * 100);
  };

  const totalLeads = leadLists.reduce((sum, list) => sum + list.totalLeads, 0);
  const totalVerified = leadLists.reduce(
    (sum, list) => sum + list.verifiedLeads,
    0
  );
  const totalEnriched = leadLists.reduce(
    (sum, list) => sum + list.enrichedLeads,
    0
  );

  return (
    <Card className="mb-8">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg font-semibold">
            📂 Lead List Overview
          </CardTitle>
          <Button
            onClick={onImportLeads}
            size="sm"
            className="flex items-center space-x-1"
          >
            <Plus className="h-4 w-4" />
            <span>Import More</span>
          </Button>
        </div>
        <div className="grid grid-cols-3 gap-4 text-sm">
          <div>
            <span className="font-medium text-gray-900">
              {totalLeads.toLocaleString()}
            </span>
            <span className="text-gray-500"> total leads</span>
          </div>
          <div>
            <span className="font-medium text-green-600">
              {totalVerified.toLocaleString()}
            </span>
            <span className="text-gray-500"> verified</span>
          </div>
          <div>
            <span className="font-medium text-blue-600">
              {totalEnriched.toLocaleString()}
            </span>
            <span className="text-gray-500"> enriched</span>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {leadLists.map((list) => {
            const verificationRate = calculateVerificationRate(
              list.verifiedLeads,
              list.totalLeads
            );
            const enrichmentRate = calculateEnrichmentRate(
              list.enrichedLeads,
              list.totalLeads
            );

            return (
              <div
                key={list.id}
                className="flex items-center justify-between p-3 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer"
                onClick={() => onViewList?.(list.id)}
              >
                <div className="flex items-center space-x-3 flex-1 min-w-0">
                  <div className="flex-shrink-0">
                    {getStatusIcon(list.status)}
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center space-x-2 mb-1">
                      <h3 className="font-medium text-gray-900 truncate">
                        {list.name}
                      </h3>
                      {getStatusBadge(list.status)}
                    </div>

                    <div className="grid grid-cols-3 gap-4 text-sm text-gray-600">
                      <div>
                        <span className="font-medium">
                          {list.totalLeads.toLocaleString()}
                        </span>
                        <span className="text-gray-500"> leads</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        <span className="font-medium text-green-600">
                          {verificationRate}%
                        </span>
                        <span className="text-gray-500">verified</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        <span className="font-medium text-blue-600">
                          {enrichmentRate}%
                        </span>
                        <span className="text-gray-500">enriched</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="flex-shrink-0 text-right">
                  <p className="text-xs text-gray-500 mb-1">Last updated</p>
                  <p className="text-sm font-medium text-gray-900">
                    {list.lastUpdated}
                  </p>
                </div>
              </div>
            );
          })}
        </div>

        {leadLists.length === 0 && (
          <div className="text-center py-8">
            <div className="text-gray-400 mb-2">
              <Users className="h-12 w-12 mx-auto" />
            </div>
            <h3 className="font-medium text-gray-900 mb-1">
              No lead lists yet
            </h3>
            <p className="text-sm text-gray-500 mb-4">
              Import your first CSV to start building your outreach database
            </p>
            <Button
              onClick={onImportLeads}
              className="flex items-center space-x-1"
            >
              <Upload className="h-4 w-4" />
              <span>Import CSV</span>
            </Button>
          </div>
        )}

        {/* Quick Actions */}
        <div className="mt-6 pt-4 border-t border-gray-200">
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              size="sm"
              className="flex items-center space-x-1"
            >
              <Upload className="h-4 w-4" />
              <span>Import CSV</span>
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="flex items-center space-x-1"
            >
              <CheckCircle className="h-4 w-4" />
              <span>Verify All</span>
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="flex items-center space-x-1"
            >
              <ExternalLink className="h-4 w-4" />
              <span>View All Lists</span>
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

