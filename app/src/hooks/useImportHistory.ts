import { useState, useEffect } from "react";
import { API_BASE_URL } from "@/lib/config";

export interface ImportJob {
  id: string;
  filename: string;
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  totalRows: number;
  processedRows: number;
  errorRows: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UseImportHistoryReturn {
  importJobs: ImportJob[];
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

export function useImportHistory(): UseImportHistoryReturn {
  const [importJobs, setImportJobs] = useState<ImportJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchImportHistory = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await fetch(`${API_BASE_URL}/api/import/jobs`, {
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      });

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error("Authentication required. Please log in again.");
        }
        throw new Error(
          `Failed to fetch import history: ${response.status} ${response.statusText}`
        );
      }

      const jobs = await response.json();
      setImportJobs(jobs);
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "Error loading import history";
      setError(errorMessage);
      console.error("Error fetching import history:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchImportHistory();
  }, []);

  return {
    importJobs,
    loading,
    error,
    refetch: fetchImportHistory,
  };
}
