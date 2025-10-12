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
    const updateProcessedContent = async () => {
      if (memoizedDetectedUrls.length > 0 && isTrackingEnabled) {
        try {
          const trackedContent = await replaceUrlsWithTracking(
            emailContent,
            messageId,
            userId,
            campaignId,
            orgId
          );
          setProcessedContent(trackedContent);
        } catch (error) {
          console.error("Error processing URLs with tracking:", error);
          setProcessedContent(emailContent);
        }
      } else {
        setProcessedContent(emailContent);
      }
    };

    updateProcessedContent();
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
    async (enabled: boolean) => {
      setIsTrackingEnabled(enabled);

      if (enabled && memoizedDetectedUrls.length > 0) {
        try {
          // Apply tracking to URLs
          const trackedContent = await replaceUrlsWithTracking(
            emailContent,
            messageId,
            userId,
            campaignId,
            orgId
          );
          setProcessedContent(trackedContent);
        } catch (error) {
          console.error("Error applying tracking:", error);
          setProcessedContent(emailContent);
        }
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
