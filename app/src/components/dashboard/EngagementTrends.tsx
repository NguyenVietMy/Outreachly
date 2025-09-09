"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { TrendingUp, TrendingDown, AlertTriangle } from "lucide-react";
import { useState } from "react";

interface TrendData {
  date: string;
  opens: number;
  clicks: number;
  replies: number;
  bounces: number;
  complaints: number;
}

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

  // Mock data for demonstration
  const mockData7d: TrendData[] = [
    {
      date: "2024-01-14",
      opens: 245,
      clicks: 32,
      replies: 18,
      bounces: 8,
      complaints: 1,
    },
    {
      date: "2024-01-15",
      opens: 312,
      clicks: 41,
      replies: 22,
      bounces: 12,
      complaints: 0,
    },
    {
      date: "2024-01-16",
      opens: 289,
      clicks: 38,
      replies: 19,
      bounces: 6,
      complaints: 1,
    },
    {
      date: "2024-01-17",
      opens: 356,
      clicks: 47,
      replies: 25,
      bounces: 9,
      complaints: 0,
    },
    {
      date: "2024-01-18",
      opens: 298,
      clicks: 39,
      replies: 21,
      bounces: 7,
      complaints: 1,
    },
    {
      date: "2024-01-19",
      opens: 334,
      clicks: 44,
      replies: 23,
      bounces: 11,
      complaints: 0,
    },
    {
      date: "2024-01-20",
      opens: 267,
      clicks: 35,
      replies: 17,
      bounces: 5,
      complaints: 1,
    },
  ];

  const mockData30d: TrendData[] = [
    {
      date: "2024-01-01",
      opens: 1200,
      clicks: 156,
      replies: 89,
      bounces: 45,
      complaints: 3,
    },
    {
      date: "2024-01-02",
      opens: 1350,
      clicks: 178,
      replies: 102,
      bounces: 52,
      complaints: 2,
    },
    {
      date: "2024-01-03",
      opens: 1280,
      clicks: 167,
      replies: 95,
      bounces: 48,
      complaints: 4,
    },
    {
      date: "2024-01-04",
      opens: 1420,
      clicks: 189,
      replies: 108,
      bounces: 55,
      complaints: 1,
    },
    {
      date: "2024-01-05",
      opens: 1380,
      clicks: 182,
      replies: 104,
      bounces: 51,
      complaints: 3,
    },
    {
      date: "2024-01-06",
      opens: 1250,
      clicks: 163,
      replies: 92,
      bounces: 47,
      complaints: 2,
    },
    {
      date: "2024-01-07",
      opens: 1320,
      clicks: 174,
      replies: 98,
      bounces: 49,
      complaints: 1,
    },
    {
      date: "2024-01-08",
      opens: 1450,
      clicks: 192,
      replies: 110,
      bounces: 58,
      complaints: 4,
    },
    {
      date: "2024-01-09",
      opens: 1390,
      clicks: 185,
      replies: 106,
      bounces: 53,
      complaints: 2,
    },
    {
      date: "2024-01-10",
      opens: 1310,
      clicks: 171,
      replies: 97,
      bounces: 46,
      complaints: 3,
    },
    {
      date: "2024-01-11",
      opens: 1360,
      clicks: 180,
      replies: 103,
      bounces: 50,
      complaints: 1,
    },
    {
      date: "2024-01-12",
      opens: 1280,
      clicks: 168,
      replies: 96,
      bounces: 48,
      complaints: 2,
    },
    {
      date: "2024-01-13",
      opens: 1340,
      clicks: 177,
      replies: 101,
      bounces: 49,
      complaints: 1,
    },
    {
      date: "2024-01-14",
      opens: 1410,
      clicks: 187,
      replies: 107,
      bounces: 54,
      complaints: 3,
    },
    {
      date: "2024-01-15",
      opens: 1370,
      clicks: 182,
      replies: 104,
      bounces: 51,
      complaints: 2,
    },
    {
      date: "2024-01-16",
      opens: 1290,
      clicks: 170,
      replies: 97,
      bounces: 47,
      complaints: 1,
    },
    {
      date: "2024-01-17",
      opens: 1350,
      clicks: 179,
      replies: 102,
      bounces: 50,
      complaints: 4,
    },
    {
      date: "2024-01-18",
      opens: 1320,
      clicks: 175,
      replies: 100,
      bounces: 48,
      complaints: 2,
    },
    {
      date: "2024-01-19",
      opens: 1380,
      clicks: 183,
      replies: 105,
      bounces: 52,
      complaints: 1,
    },
    {
      date: "2024-01-20",
      opens: 1310,
      clicks: 173,
      replies: 99,
      bounces: 46,
      complaints: 3,
    },
  ];

  const trendData =
    data || (selectedPeriod === "7d" ? mockData7d : mockData30d);

  const handlePeriodChange = (newPeriod: "7d" | "30d") => {
    setSelectedPeriod(newPeriod);
    onPeriodChange?.(newPeriod);
  };

  // Calculate averages and trends
  const avgOpens = Math.round(
    trendData.reduce((sum, day) => sum + day.opens, 0) / trendData.length
  );
  const avgClicks = Math.round(
    trendData.reduce((sum, day) => sum + day.clicks, 0) / trendData.length
  );
  const avgReplies = Math.round(
    trendData.reduce((sum, day) => sum + day.replies, 0) / trendData.length
  );
  const avgBounces = Math.round(
    trendData.reduce((sum, day) => sum + day.bounces, 0) / trendData.length
  );
  const avgComplaints = Math.round(
    trendData.reduce((sum, day) => sum + day.complaints, 0) / trendData.length
  );

  // Calculate trends (comparing first half vs second half)
  const midPoint = Math.floor(trendData.length / 2);
  const firstHalf = trendData.slice(0, midPoint);
  const secondHalf = trendData.slice(midPoint);

  const firstHalfAvg =
    firstHalf.reduce((sum, day) => sum + day.opens, 0) / firstHalf.length;
  const secondHalfAvg =
    secondHalf.reduce((sum, day) => sum + day.opens, 0) / secondHalf.length;
  const openTrend = secondHalfAvg > firstHalfAvg ? "up" : "down";
  const openTrendPercent = Math.abs(
    ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100
  );

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

  const isDeliverabilityRisky = avgBounces > 50 || avgComplaints > 2;

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
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600">{avgOpens}</div>
              <div className="text-xs text-gray-500">Avg Opens</div>
              <div
                className={`flex items-center justify-center space-x-1 mt-1 ${getTrendColor(openTrend)}`}
              >
                {getTrendIcon(openTrend)}
                <span className="text-xs">{openTrendPercent.toFixed(1)}%</span>
              </div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-purple-600">
                {avgClicks}
              </div>
              <div className="text-xs text-gray-500">Avg Clicks</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">
                {avgReplies}
              </div>
              <div className="text-xs text-gray-500">Avg Replies</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-orange-600">
                {avgBounces}
              </div>
              <div className="text-xs text-gray-500">Avg Bounces</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-red-600">
                {avgComplaints}
              </div>
              <div className="text-xs text-gray-500">Avg Complaints</div>
            </div>
          </div>

          {/* Simple Bar Chart Visualization */}
          <div className="space-y-2">
            <h4 className="font-medium text-gray-900">
              Daily Opens vs Clicks vs Replies
            </h4>
            <div className="space-y-1">
              {trendData.slice(-7).map((day, index) => {
                const maxValue = Math.max(day.opens, day.clicks, day.replies);
                const openWidth = (day.opens / maxValue) * 100;
                const clickWidth = (day.clicks / maxValue) * 100;
                const replyWidth = (day.replies / maxValue) * 100;

                return (
                  <div key={day.date} className="flex items-center space-x-2">
                    <div className="w-16 text-xs text-gray-500">
                      {new Date(day.date).toLocaleDateString("en-US", {
                        month: "short",
                        day: "numeric",
                      })}
                    </div>
                    <div className="flex-1 space-y-1">
                      <div className="flex items-center space-x-1">
                        <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
                        <div className="text-xs text-gray-600">Opens</div>
                        <div className="flex-1 bg-gray-200 rounded-full h-2">
                          <div
                            className="bg-blue-500 h-2 rounded-full"
                            style={{ width: `${openWidth}%` }}
                          ></div>
                        </div>
                        <div className="text-xs text-gray-500">{day.opens}</div>
                      </div>
                      <div className="flex items-center space-x-1">
                        <div className="w-2 h-2 bg-purple-500 rounded-full"></div>
                        <div className="text-xs text-gray-600">Clicks</div>
                        <div className="flex-1 bg-gray-200 rounded-full h-2">
                          <div
                            className="bg-purple-500 h-2 rounded-full"
                            style={{ width: `${clickWidth}%` }}
                          ></div>
                        </div>
                        <div className="text-xs text-gray-500">
                          {day.clicks}
                        </div>
                      </div>
                      <div className="flex items-center space-x-1">
                        <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                        <div className="text-xs text-gray-600">Replies</div>
                        <div className="flex-1 bg-gray-200 rounded-full h-2">
                          <div
                            className="bg-green-500 h-2 rounded-full"
                            style={{ width: `${replyWidth}%` }}
                          ></div>
                        </div>
                        <div className="text-xs text-gray-500">
                          {day.replies}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Deliverability Warning */}
          {isDeliverabilityRisky && (
            <div className="flex items-center space-x-2 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
              <AlertTriangle className="h-5 w-5 text-yellow-600" />
              <div className="flex-1">
                <p className="text-sm font-medium text-yellow-800">
                  Domain reputation risk detected
                </p>
                <p className="text-xs text-yellow-600">
                  High bounce or complaint rates may affect deliverability.
                  Consider reviewing your list quality.
                </p>
              </div>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

