"use client";

import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Shield,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Settings,
  ExternalLink,
  Info,
  Globe,
  Mail,
} from "lucide-react";

interface RegisteredDomain {
  id: string;
  domain: string;
  provider: string;
  fromEmail: string;
  fromName: string;
  isActive: boolean;
  lastUpdated: string;
}

interface SendingProfileHealthProps {
  onConfigureDomain?: (profileId: string) => void;
  onViewDetails?: (profileId: string) => void;
}

export default function SendingProfileHealth({
  onConfigureDomain,
  onViewDetails,
}: SendingProfileHealthProps) {
  const [domains, setDomains] = useState<RegisteredDomain[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDomains();
  }, []);

  const fetchDomains = async () => {
    try {
      setLoading(true);
      setError(null);

      const API_URL =
        process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

      const response = await fetch(`${API_URL}/api/email/domains`, {
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      });

      if (response.ok) {
        const data = await response.json();
        setDomains(data);
      } else if (response.status === 401) {
        setError("Authentication required. Please log in again.");
      } else if (response.status === 404) {
        setError(
          "API endpoint not found. Please check if the server is running."
        );
      } else {
        setError(
          `Failed to fetch domains: ${response.status} ${response.statusText}`
        );
      }
    } catch (err) {
      setError("Network error. Please check your connection.");
      console.error("Error fetching domains:", err);
    } finally {
      setLoading(false);
    }
  };

  const getProviderIcon = (provider: string) => {
    switch (provider.toLowerCase()) {
      case "resend":
        return <Mail className="h-4 w-4 text-blue-600" />;
      case "gmail":
        return <Mail className="h-4 w-4 text-red-600" />;
      default:
        return <Globe className="h-4 w-4 text-gray-600" />;
    }
  };

  const getStatusBadge = (isActive: boolean) => {
    return (
      <Badge
        variant={isActive ? "default" : "secondary"}
        className={
          isActive ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-800"
        }
      >
        {isActive ? "Active" : "Inactive"}
      </Badge>
    );
  };

  const formatLastUpdated = (lastUpdated: string) => {
    try {
      const date = new Date(lastUpdated);
      const now = new Date();
      const diffInHours = Math.floor(
        (now.getTime() - date.getTime()) / (1000 * 60 * 60)
      );

      if (diffInHours < 1) return "Just now";
      if (diffInHours < 24) return `${diffInHours} hours ago`;
      const diffInDays = Math.floor(diffInHours / 24);
      return `${diffInDays} days ago`;
    } catch {
      return "Unknown";
    }
  };

  if (loading) {
    return (
      <Card className="mb-8">
        <CardHeader>
          <CardTitle className="text-lg font-semibold flex items-center space-x-2">
            <Shield className="h-5 w-5" />
            <span>📧 Registered Sending Domains</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-center py-8">
            <div className="text-gray-400 mb-2">
              <Shield className="h-12 w-12 mx-auto animate-pulse" />
            </div>
            <p className="text-sm text-gray-500">Loading domains...</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="mb-8">
      <CardHeader>
        <CardTitle className="text-lg font-semibold flex items-center space-x-2">
          <Shield className="h-5 w-5" />
          <span>📧 Registered Sending Domains</span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {domains.length > 0 ? (
            domains.map((domain) => (
              <div
                key={domain.id}
                className="border border-gray-200 rounded-lg p-4 hover:bg-gray-50 transition-colors"
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center space-x-3">
                    {getProviderIcon(domain.provider)}
                    <div>
                      <h3 className="font-medium text-gray-900">
                        {domain.domain}
                      </h3>
                      <p className="text-sm text-gray-500">
                        {domain.fromName} &lt;{domain.fromEmail}&gt;
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-2">
                    {getStatusBadge(domain.isActive)}
                    <Badge variant="outline" className="text-xs">
                      {domain.provider}
                    </Badge>
                  </div>
                </div>

                <div className="flex items-center justify-between text-xs text-gray-500">
                  <span>
                    Last updated: {formatLastUpdated(domain.lastUpdated)}
                  </span>
                  {domain.id !== "default" && (
                    <div className="flex space-x-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onConfigureDomain?.(domain.id)}
                      >
                        <Settings className="h-3 w-3 mr-1" />
                        Configure
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onViewDetails?.(domain.id)}
                      >
                        <ExternalLink className="h-3 w-3 mr-1" />
                        Details
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            ))
          ) : (
            <div className="text-center py-8">
              <div className="text-gray-400 mb-2">
                <Globe className="h-12 w-12 mx-auto" />
              </div>
              <h3 className="font-medium text-gray-900 mb-1">
                No sending domains configured
              </h3>
              <p className="text-sm text-gray-500 mb-4">
                Set up your domain to start sending emails with better
                deliverability
              </p>
              <Button onClick={() => onConfigureDomain?.("new")}>
                <Settings className="h-4 w-4 mr-2" />
                Configure Domain
              </Button>
            </div>
          )}
        </div>

        {error && (
          <div className="mt-4 p-4 bg-red-50 border border-red-200 rounded-lg">
            <div className="flex items-start space-x-2">
              <AlertTriangle className="h-5 w-5 text-red-600 mt-0.5" />
              <div>
                <h4 className="text-sm font-medium text-red-800 mb-1">
                  Error loading domains
                </h4>
                <p className="text-xs text-red-600">{error}</p>
              </div>
            </div>
          </div>
        )}

        {/* Help Text */}
        <div className="mt-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <div className="flex items-start space-x-2">
            <Info className="h-5 w-5 text-blue-600 mt-0.5" />
            <div>
              <h4 className="text-sm font-medium text-blue-800 mb-1">
                About Sending Domains
              </h4>
              <p className="text-xs text-blue-600">
                Configured domains are used as the "From" address for your
                emails. This helps improve deliverability and builds trust with
                recipients.
              </p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
