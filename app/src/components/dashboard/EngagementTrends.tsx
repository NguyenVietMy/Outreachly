"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { TrendingUp, TrendingDown, AlertTriangle } from "lucide-react";
import { useState, useEffect } from "react";
import { useDeliveryMetrics, TrendData } from "@/hooks/useDeliveryMetrics";
import { useAuth } from "@/contexts/AuthContext";

interface EngagementTrendsProps {
  data?: TrendData[];
  period?: "7d" | "30d";
  onPeriodChange?: (period: "7d" | "30d") => void;
}

export default function EngagementTrends({
  data,
  period = "7d",
  onPeriodChange,
}: EngagementTrendsProps) {
  const [selectedPeriod, setSelectedPeriod] = useState<"7d" | "30d">(period);
  const { user } = useAuth();
  const {
    trendData: apiTrendData,
    fetchTrendData,
    loading,
    error,
  } = useDeliveryMetrics();

  // Use provided data, API data, or fallback to empty array
  const trendData = data || apiTrendData || [];

  // Fetch data when period changes
  useEffect(() => {
    if (!data) {
      fetchTrendData(selectedPeriod);
    }
  }, [selectedPeriod, data]);

  const handlePeriodChange = (newPeriod: "7d" | "30d") => {
    setSelectedPeriod(newPeriod);
    onPeriodChange?.(newPeriod);
  };

  // Calculate averages and trends from API data
  const avgDelivered = Math.round(
    trendData.reduce((sum, day) => sum + day.delivered, 0) /
      Math.max(trendData.length, 1)
  );
  const avgFailed = Math.round(
    trendData.reduce((sum, day) => sum + day.failed, 0) /
      Math.max(trendData.length, 1)
  );
  const avgTotalSent = Math.round(
    trendData.reduce((sum, day) => sum + day.totalSent, 0) /
      Math.max(trendData.length, 1)
  );
  // Filter out days with no email activity for accurate calculations
  const daysWithActivity = trendData.filter((day) => day.totalSent > 0);

  const avgDeliveryRate =
    daysWithActivity.length > 0
      ? daysWithActivity.reduce((sum, day) => sum + day.deliveryRate, 0) /
        daysWithActivity.length
      : 0;

  // Calculate trends (comparing first half vs second half)
  const midPoint = Math.floor(daysWithActivity.length / 2);
  const firstHalf = daysWithActivity.slice(0, midPoint);
  const secondHalf = daysWithActivity.slice(midPoint);

  const firstHalfAvg =
    firstHalf.length > 0
      ? firstHalf.reduce((sum, day) => sum + day.delivered, 0) /
        firstHalf.length
      : 0;
  const secondHalfAvg =
    secondHalf.length > 0
      ? secondHalf.reduce((sum, day) => sum + day.delivered, 0) /
        secondHalf.length
      : 0;
  const deliveryTrend = secondHalfAvg > firstHalfAvg ? "up" : "down";
  const deliveryTrendPercent =
    firstHalfAvg > 0
      ? Math.abs(((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100)
      : 0;

  const getTrendIcon = (trend: "up" | "down") => {
    return trend === "up" ? (
      <TrendingUp className="h-4 w-4 text-green-600" />
    ) : (
      <TrendingDown className="h-4 w-4 text-red-600" />
    );
  };

  const getTrendColor = (trend: "up" | "down") => {
    return trend === "up" ? "text-green-600" : "text-red-600";
  };

  const isDeliverabilityRisky = avgFailed > 50 || avgDeliveryRate < 90;

  // Helper function to convert UTC offset to timezone string for date formatting
  const getTimezoneString = (timezone: string): string => {
    if (!timezone || timezone === "UTC±0" || timezone === "UTC+0") {
      return "UTC";
    }

    if (timezone.startsWith("UTC")) {
      const offsetPart = timezone.substring(3);
      if (offsetPart.startsWith("+")) {
        const hours = parseInt(offsetPart.substring(1));
        return `Etc/GMT-${hours}`; // Note: GMT offsets are inverted
      } else if (
        offsetPart.startsWith("−") ||
        offsetPart.startsWith("-") ||
        offsetPart.startsWith("?")
      ) {
        // Handle Unicode minus (U+2212), regular minus, and corrupted minus
        const hours = parseInt(offsetPart.substring(1));
        return `Etc/GMT+${hours}`; // Note: GMT offsets are inverted
      }
    }

    return "UTC"; // Fallback
  };

  return (
    <Card className="mb-8">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg font-semibold">
            📈 Engagement Trends
          </CardTitle>
          <div className="flex space-x-2">
            <Button
              variant={selectedPeriod === "7d" ? "default" : "outline"}
              size="sm"
              onClick={() => handlePeriodChange("7d")}
            >
              7 Days
            </Button>
            <Button
              variant={selectedPeriod === "30d" ? "default" : "outline"}
              size="sm"
              onClick={() => handlePeriodChange("30d")}
            >
              30 Days
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          {/* Summary Stats */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600">
                {avgDelivered}
              </div>
              <div className="text-xs text-gray-500">Avg Delivered</div>
              <div
                className={`flex items-center justify-center space-x-1 mt-1 ${getTrendColor(deliveryTrend)}`}
              >
                {getTrendIcon(deliveryTrend)}
                <span className="text-xs">
                  {deliveryTrendPercent.toFixed(1)}%
                </span>
              </div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-red-600">{avgFailed}</div>
              <div className="text-xs text-gray-500">Avg Failed</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">
                {avgTotalSent}
              </div>
              <div className="text-xs text-gray-500">Avg Total Sent</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-purple-600">
                {avgDeliveryRate.toFixed(1)}%
              </div>
              <div className="text-xs text-gray-500">Avg Delivery Rate</div>
            </div>
          </div>

          {/* Simple Bar Chart Visualization */}
          <div className="space-y-2">
            <h4 className="font-medium text-gray-900">
              Daily Delivery Metrics ({user?.timezone || "UTC±0"})
            </h4>
            {loading && (
              <div className="text-center py-4 text-gray-500">
                Loading delivery metrics...
              </div>
            )}
            {error && (
              <div className="text-center py-4 text-red-500">
                Error loading metrics: {error}
              </div>
            )}
            {!loading && !error && trendData.length === 0 && (
              <div className="text-center py-4 text-gray-500">
                No delivery data available for the selected period.
              </div>
            )}
            {!loading && !error && trendData.length > 0 && (
              <div className="space-y-4">
                {trendData.slice(-7).map((day, index) => {
                  const maxValue = Math.max(
                    day.delivered,
                    day.failed,
                    day.totalSent
                  );
                  const deliveredWidth =
                    maxValue > 0 ? (day.delivered / maxValue) * 100 : 0;
                  const failedWidth =
                    maxValue > 0 ? (day.failed / maxValue) * 100 : 0;
                  const totalWidth =
                    maxValue > 0 ? (day.totalSent / maxValue) * 100 : 0;

                  // Format date - backend already provides dates in user's timezone
                  const dateObj = new Date(day.date + "T00:00:00");
                  const formattedDate = dateObj.toLocaleDateString("en-US", {
                    month: "short",
                    day: "numeric",
                  });

                  return (
                    <div
                      key={day.date}
                      className="flex items-center space-x-2 p-3"
                    >
                      <div className="w-16 text-xs text-gray-500">
                        {formattedDate}
                      </div>
                      <div className="flex-1 space-y-2">
                        <div className="flex items-center space-x-1">
                          <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                          <div className="text-xs text-gray-600">Delivered</div>
                          <div className="flex-1 bg-gray-200 rounded-full h-2">
                            <div
                              className="bg-green-500 h-2 rounded-full"
                              style={{ width: `${deliveredWidth}%` }}
                            ></div>
                          </div>
                          <div className="text-xs text-gray-500">
                            {day.delivered}
                          </div>
                        </div>
                        <div className="flex items-center space-x-1">
                          <div className="w-2 h-2 bg-red-500 rounded-full"></div>
                          <div className="text-xs text-gray-600">Failed</div>
                          <div className="flex-1 bg-gray-200 rounded-full h-2">
                            <div
                              className="bg-red-500 h-2 rounded-full"
                              style={{ width: `${failedWidth}%` }}
                            ></div>
                          </div>
                          <div className="text-xs text-gray-500">
                            {day.failed}
                          </div>
                        </div>
                        <div className="flex items-center space-x-1">
                          <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
                          <div className="text-xs text-gray-600">
                            Total Sent
                          </div>
                          <div className="flex-1 bg-gray-200 rounded-full h-2">
                            <div
                              className="bg-blue-500 h-2 rounded-full"
                              style={{ width: `${totalWidth}%` }}
                            ></div>
                          </div>
                          <div className="text-xs text-gray-500">
                            {day.totalSent}
                          </div>
                        </div>
                        <div className="flex items-center space-x-1">
                          <div className="w-2 h-2 bg-purple-500 rounded-full"></div>
                          <div className="text-xs text-gray-600">Rate</div>
                          <div className="text-xs text-gray-500">
                            {day.deliveryRate.toFixed(1)}%
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Deliverability Warning */}
          {isDeliverabilityRisky && (
            <div className="flex items-center space-x-2 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
              <AlertTriangle className="h-5 w-5 text-yellow-600" />
              <div className="flex-1">
                <p className="text-sm font-medium text-yellow-800">
                  Delivery performance risk detected
                </p>
                <p className="text-xs text-yellow-600">
                  High failure rate or low delivery rate may affect email
                  deliverability. Consider reviewing your email content and
                  recipient list quality.
                </p>
              </div>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
