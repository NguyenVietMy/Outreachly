"use client";

import { useState } from "react";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import ClickTrackerToggle from "@/components/email/ClickTrackerToggle";
import ClickTrackerNotification from "@/components/email/ClickTrackerNotification";
import { useClickTracking } from "@/hooks/useClickTracking";

interface EmailEditorProps {
  onSend?: (content: string, isTrackingEnabled: boolean) => void;
  className?: string;
}

export default function EmailEditor({
  onSend,
  className = "",
}: EmailEditorProps) {
  const [emailContent, setEmailContent] = useState("");

  // Mock data - in real app, these would come from context/auth
  const messageId = "msg_" + Date.now();
  const userId = "user_123";
  const campaignId = "campaign_456";
  const orgId = "org_789";

  const {
    isTrackingEnabled,
    detectedUrls,
    processedContent,
    toggleTracking,
    hasUrls,
  } = useClickTracking({
    emailContent,
    messageId,
    userId,
    campaignId,
    orgId,
  });

  const handleSend = () => {
    if (onSend) {
      onSend(processedContent, isTrackingEnabled);
    }
  };

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>Compose Email</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Click Tracker Toggle */}
        <ClickTrackerToggle
          isEnabled={isTrackingEnabled}
          onToggle={toggleTracking}
          detectedUrls={detectedUrls}
        />

        {/* Click Tracker Notification */}
        <ClickTrackerNotification
          isEnabled={isTrackingEnabled}
          detectedUrls={detectedUrls}
        />

        {/* Email Content Editor */}
        <div className="space-y-2">
          <label htmlFor="email-content" className="text-sm font-medium">
            Email Content
          </label>
          <Textarea
            id="email-content"
            placeholder="Type your email content here... Include links like https://example.com to see click tracking in action!"
            value={emailContent}
            onChange={(e) => setEmailContent(e.target.value)}
            className="min-h-[200px]"
          />
        </div>

        {/* Preview of processed content */}
        {isTrackingEnabled && hasUrls && (
          <div className="space-y-2">
            <label className="text-sm font-medium text-green-700">
              Processed Content (with tracking):
            </label>
            <div className="p-3 bg-green-50 border border-green-200 rounded-md text-sm">
              <pre className="whitespace-pre-wrap break-all">
                {processedContent}
              </pre>
            </div>
          </div>
        )}

        {/* Send Button */}
        <div className="flex justify-end">
          <Button onClick={handleSend} disabled={!emailContent.trim()}>
            Send Email
          </Button>
        </div>

        {/* Debug Info */}
        {process.env.NODE_ENV === "development" && (
          <div className="mt-4 p-3 bg-gray-100 rounded-md text-xs">
            <div>
              <strong>Detected URLs:</strong>{" "}
              {detectedUrls.join(", ") || "None"}
            </div>
            <div>
              <strong>Tracking Enabled:</strong>{" "}
              {isTrackingEnabled ? "Yes" : "No"}
            </div>
            <div>
              <strong>Message ID:</strong> {messageId}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}


