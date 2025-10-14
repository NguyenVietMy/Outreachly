"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { Card, CardContent } from "@/components/ui/card";
import { Clock } from "lucide-react";

export default function TimeDisplay() {
  const { user } = useAuth();
  const [currentTime, setCurrentTime] = useState<string>("");
  const [utcTime, setUtcTime] = useState<string>("");

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();

      // UTC time
      const utc = now.toISOString().substring(11, 19);
      setUtcTime(utc);

      // User's timezone time
      if (user?.timezone) {
        try {
          // Parse UTC offset (e.g., "UTC+5" -> "+05:00")
          let offset = "+00:00";
          if (user.timezone.startsWith("UTC")) {
            const offsetPart = user.timezone.substring(3);
            if (offsetPart === "±0" || offsetPart === "+0") {
              offset = "+00:00";
            } else if (offsetPart.startsWith("+")) {
              const hours = parseInt(offsetPart.substring(1));
              offset = `+${hours.toString().padStart(2, "0")}:00`;
            } else if (
              offsetPart.startsWith("−") ||
              offsetPart.startsWith("-") ||
              offsetPart.startsWith("?")
            ) {
              // Handle Unicode minus (U+2212), regular minus, and corrupted minus
              const hours = parseInt(offsetPart.substring(1));
              offset = `-${hours.toString().padStart(2, "0")}:00`;
            }
          }

          // Create timezone-aware date
          const userTime = new Date(
            now.getTime() +
              parseInt(offset.substring(1, 3)) *
                (offset.startsWith("+") ? 1 : -1) *
                60 *
                60 *
                1000
          );
          const userTimeStr = userTime.toISOString().substring(11, 19);
          setCurrentTime(userTimeStr);
        } catch (error) {
          console.error("Error parsing timezone:", error);
          setCurrentTime(utc);
        }
      } else {
        setCurrentTime(utc);
      }
    };

    // Update immediately
    updateTime();

    // Update every second
    const interval = setInterval(updateTime, 1000);

    return () => clearInterval(interval);
  }, [user?.timezone]);

  return (
    <Card className="w-full">
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Clock className="h-4 w-4 text-blue-600" />
            <span className="text-sm font-medium text-gray-700">
              Current Time
            </span>
          </div>
          <div className="text-right">
            <div className="text-lg font-mono font-bold text-gray-900">
              {currentTime}
            </div>
            <div className="text-xs text-gray-500">
              {user?.timezone || "UTC±0"}
            </div>
            <div className="text-xs text-gray-400">UTC: {utcTime}</div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
