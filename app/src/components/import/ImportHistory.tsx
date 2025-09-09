"use client";

import { useState, useEffect } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  CheckCircle,
  XCircle,
  Clock,
  AlertCircle,
  RefreshCw,
} from "lucide-react";
import { API_BASE_URL } from "@/lib/config";

interface ImportJob {
  id: string;
  filename: string;
  status: "pending" | "processing" | "completed" | "failed";
  totalRows: number;
  processedRows: number;
  errorRows: number;
  errorMessage?: string;
  createdAt: string;
}

export function ImportHistory() {
  const [importJobs, setImportJobs] = useState<ImportJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchImportHistory = async () => {
    try {
      setLoading(true);
      const response = await fetch(`${API_BASE_URL}/api/import/jobs`, {
        credentials: "include",
      });

      if (response.ok) {
        const jobs = await response.json();
        setImportJobs(jobs);
      } else {
        setError("Failed to load import history");
      }
    } catch (err) {
      setError("Error loading import history");
      console.error("Error fetching import history:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchImportHistory();
  }, []);

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "completed":
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case "failed":
        return <XCircle className="h-4 w-4 text-red-500" />;
      case "processing":
        return <AlertCircle className="h-4 w-4 text-blue-500 animate-spin" />;
      default:
        return <Clock className="h-4 w-4 text-yellow-500" />;
    }
  };

  const getStatusBadge = (status: string) => {
    const variants = {
      pending: "secondary",
      processing: "default",
      completed: "default",
      failed: "destructive",
    } as const;

    return (
      <Badge variant={variants[status as keyof typeof variants] || "secondary"}>
        {status.charAt(0).toUpperCase() + status.slice(1)}
      </Badge>
    );
  };

  const formatDate = (dateString: string) => {
    try {
      const date = new Date(dateString);
      const now = new Date();
      const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

      if (diffInSeconds < 60) return "just now";
      if (diffInSeconds < 3600)
        return `${Math.floor(diffInSeconds / 60)} minutes ago`;
      if (diffInSeconds < 86400)
        return `${Math.floor(diffInSeconds / 3600)} hours ago`;
      if (diffInSeconds < 2592000)
        return `${Math.floor(diffInSeconds / 86400)} days ago`;
      if (diffInSeconds < 31536000)
        return `${Math.floor(diffInSeconds / 2592000)} months ago`;
      return `${Math.floor(diffInSeconds / 31536000)} years ago`;
    } catch {
      return "Unknown";
    }
  };

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Import History</CardTitle>
          <CardDescription>Loading your recent imports...</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center py-8">
            <RefreshCw className="h-6 w-6 animate-spin" />
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>Import History</CardTitle>
            <CardDescription>
              View your recent CSV imports and their status
            </CardDescription>
          </div>
          <Button variant="outline" size="sm" onClick={fetchImportHistory}>
            <RefreshCw className="h-4 w-4 mr-2" />
            Refresh
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {error && <div className="text-red-500 text-sm mb-4">{error}</div>}

        {importJobs.length === 0 ? (
          <div className="text-center py-8 text-muted-foreground">
            No import history found. Upload your first CSV file to get started.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>File</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Progress</TableHead>
                  <TableHead>Results</TableHead>
                  <TableHead>Date</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {importJobs.map((job) => (
                  <TableRow key={job.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {getStatusIcon(job.status)}
                        <span className="font-medium">{job.filename}</span>
                      </div>
                    </TableCell>
                    <TableCell>{getStatusBadge(job.status)}</TableCell>
                    <TableCell>
                      {job.status === "processing" ? (
                        <div className="text-sm">
                          {job.processedRows} / {job.totalRows}
                        </div>
                      ) : (
                        <div className="text-sm text-muted-foreground">
                          {job.totalRows} rows
                        </div>
                      )}
                    </TableCell>
                    <TableCell>
                      {job.status === "completed" && (
                        <div className="text-sm">
                          <div className="text-green-600">
                            ✓ {job.processedRows} imported
                          </div>
                          {job.errorRows > 0 && (
                            <div className="text-red-600">
                              ✗ {job.errorRows} errors
                            </div>
                          )}
                        </div>
                      )}
                      {job.status === "failed" && job.errorMessage && (
                        <div
                          className="text-sm text-red-600 max-w-xs truncate"
                          title={job.errorMessage}
                        >
                          {job.errorMessage}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {formatDate(job.createdAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
