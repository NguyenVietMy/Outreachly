"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Link, Info, CheckCircle } from "lucide-react";

interface ClickTrackerNotificationProps {
  isEnabled: boolean;
  detectedUrls: string[];
  className?: string;
}

function ClickTrackerNotification({
  isEnabled,
  detectedUrls,
  className = "",
}: ClickTrackerNotificationProps) {
  const hasUrls = detectedUrls.length > 0;

  if (!hasUrls) {
    return null; // Don't show notification if no URLs detected
  }

  if (isEnabled) {
    return (
      <Alert className={`border-green-200 bg-green-50 ${className}`}>
        <CheckCircle className="h-4 w-4 text-green-600" />
        <AlertDescription className="text-green-800">
          <div className="flex items-center space-x-2">
            <Link className="h-4 w-4" />
            <span className="font-medium">Click tracking is active!</span>
          </div>
          <p className="text-sm mt-1">
            All {detectedUrls.length} link{detectedUrls.length !== 1 ? "s" : ""}{" "}
            in your email will be tracked for analytics.
          </p>
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <Alert className={`border-blue-200 bg-blue-50 ${className}`}>
      <Info className="h-4 w-4 text-blue-600" />
      <AlertDescription className="text-blue-800">
        <div className="flex items-center space-x-2">
          <Link className="h-4 w-4" />
          <span className="font-medium">Links detected in your email</span>
        </div>
        <p className="text-sm mt-1">
          Enable click tracking to monitor engagement on {detectedUrls.length}{" "}
          link{detectedUrls.length !== 1 ? "s" : ""}.
        </p>
      </AlertDescription>
    </Alert>
  );
}

export default ClickTrackerNotification;
