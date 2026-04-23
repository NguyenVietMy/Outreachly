"use client";

import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Link } from "lucide-react";

interface ClickTrackerToggleProps {
  isEnabled: boolean;
  onToggle: (enabled: boolean) => void;
  detectedUrls: string[];
  className?: string;
}

function ClickTrackerToggle({
  isEnabled,
  onToggle,
  detectedUrls,
  className = "",
}: ClickTrackerToggleProps) {
  const hasUrls = detectedUrls.length > 0;

  return (
    <div className={`flex items-center space-x-3 ${className}`}>
      <div className="flex items-center space-x-2">
        <Switch
          id="click-tracker"
          checked={isEnabled}
          onCheckedChange={onToggle}
          disabled={!hasUrls}
        />
        <Label htmlFor="click-tracker" className="text-sm font-medium">
          Click Tracking
        </Label>
      </div>

      {/* Simplified badges to isolate the issue */}
      {hasUrls && (
        <div className="flex items-center space-x-2">
          <div className="text-sm bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400 px-2 py-1 rounded font-mono uppercase tracking-wider">
            {isEnabled ? "Click Tracker ON" : "Click Tracker OFF"}
          </div>
          <div className="text-xs bg-secondary text-muted-foreground px-2 py-1 rounded">
            {detectedUrls.length} links detected
          </div>
        </div>
      )}

      {!hasUrls && (
        <div className="text-xs text-muted-foreground px-2 py-1 rounded">
          No links detected
        </div>
      )}
    </div>
  );
}

export default ClickTrackerToggle;
