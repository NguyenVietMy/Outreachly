import { useState, useEffect, useCallback, useMemo } from "react";
import {
  detectUrls,
  replaceUrlsWithTracking,
  restoreOriginalUrls,
} from "@/lib/linkTracking";

interface UseClickTrackingProps {
  emailContent: string;
  messageId: string;
  userId: string;
  campaignId?: string;
  orgId?: string;
}

export function useClickTracking({
  emailContent,
  messageId,
  userId,
  campaignId,
  orgId,
}: UseClickTrackingProps) {
  const [isTrackingEnabled, setIsTrackingEnabled] = useState(false);
  const [detectedUrls, setDetectedUrls] = useState<string[]>([]);
  const [processedContent, setProcessedContent] = useState(emailContent);

  // Memoize detected URLs to prevent unnecessary re-calculations
  const memoizedDetectedUrls = useMemo(() => {
    return detectUrls(emailContent);
  }, [emailContent]);

  // Update detected URLs when email content changes
  useEffect(() => {
    setDetectedUrls(memoizedDetectedUrls);
  }, [memoizedDetectedUrls]);

  // Update processed content when dependencies change
  useEffect(() => {
    if (memoizedDetectedUrls.length > 0 && isTrackingEnabled) {
      const trackedContent = replaceUrlsWithTracking(
        emailContent,
        messageId,
        userId,
        campaignId,
        orgId
      );
      setProcessedContent(trackedContent);
    } else {
      setProcessedContent(emailContent);
    }
  }, [
    emailContent,
    messageId,
    userId,
    campaignId,
    orgId,
    isTrackingEnabled,
    memoizedDetectedUrls.length,
  ]);

  // Toggle tracking on/off
  const toggleTracking = useCallback(
    (enabled: boolean) => {
      setIsTrackingEnabled(enabled);

      if (enabled && memoizedDetectedUrls.length > 0) {
        // Apply tracking to URLs
        const trackedContent = replaceUrlsWithTracking(
          emailContent,
          messageId,
          userId,
          campaignId,
          orgId
        );
        setProcessedContent(trackedContent);
      } else {
        // Remove tracking from URLs
        const originalContent = restoreOriginalUrls(emailContent);
        setProcessedContent(originalContent);
      }
    },
    [
      emailContent,
      messageId,
      userId,
      campaignId,
      orgId,
      memoizedDetectedUrls.length,
    ]
  );

  return {
    isTrackingEnabled,
    detectedUrls: memoizedDetectedUrls,
    processedContent,
    toggleTracking,
    hasUrls: memoizedDetectedUrls.length > 0,
  };
}
