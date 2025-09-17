"use client";

import { useState, useCallback, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useDropzone } from "react-dropzone";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Progress } from "@/components/ui/progress";
import {
  Upload,
  FileText,
  CheckCircle,
  XCircle,
  AlertCircle,
} from "lucide-react";
import { CsvPreviewModal } from "@/components/import/CsvPreviewModal";
import { ColumnMappingModal } from "@/components/import/ColumnMappingModal";
import { ImportHistory } from "@/components/import/ImportHistory";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { API_BASE_URL } from "@/lib/config";
import { useCampaigns } from "@/hooks/useCampaigns";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Search } from "lucide-react";

interface ValidationResult {
  valid: boolean;
  errors: string[];
  data: Record<string, string>[];
  totalRows: number;
}

interface CsvColumn {
  name: string;
  displayName: string;
  sampleValue: string;
  isRequired: boolean;
  currentMapping: string | null;
}

interface FieldOption {
  value: string;
  label: string;
  description: string;
  isRequired: boolean;
  category: string;
}

interface ColumnMappingData {
  detectedColumns: CsvColumn[];
  availableFields: FieldOption[];
  mapping: Record<string, string>;
  hasRequiredFields: boolean;
  missingRequiredFields: string[];
}

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

export default function ImportPage() {
  const [file, setFile] = useState<File | null>(null);
  const [validationResult, setValidationResult] =
    useState<ValidationResult | null>(null);
  const [columnMappingData, setColumnMappingData] =
    useState<ColumnMappingData | null>(null);
  const [isDetectingColumns, setIsDetectingColumns] = useState(false);
  const [isValidating, setIsValidating] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const [showColumnMapping, setShowColumnMapping] = useState(false);
  const [importJob, setImportJob] = useState<ImportJob | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCampaignSelection, setShowCampaignSelection] = useState(false);
  const [selectedCampaignId, setSelectedCampaignId] =
    useState<string>("default");
  const [campaignSearchTerm, setCampaignSearchTerm] = useState("");
  const { campaigns, loading: campaignsLoading } = useCampaigns();

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const selectedFile = acceptedFiles[0];
    if (selectedFile) {
      setFile(selectedFile);
      setError(null);
      setValidationResult(null);
      setColumnMappingData(null);
      detectColumns(selectedFile);
    }
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      "text/csv": [".csv"],
      "application/vnd.ms-excel": [".csv"],
    },
    maxSize: 25 * 1024 * 1024, // 25MB
    multiple: false,
  });

  const detectColumns = async (file: File) => {
    setIsDetectingColumns(true);
    setError(null);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await fetch(
        `${API_BASE_URL}/api/import/detect-columns`,
        {
          method: "POST",
          body: formData,
          credentials: "include",
        }
      );

      const result = await response.json();

      if (response.ok) {
        setColumnMappingData(result);
        setShowColumnMapping(true);
      } else {
        setError(result.error || "Column detection failed");
      }
    } catch (err) {
      setError("Error detecting columns. Please try again.");
      console.error("Column detection error:", err);
    } finally {
      setIsDetectingColumns(false);
    }
  };

  const validateFile = async (file: File) => {
    setIsValidating(true);
    setError(null);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await fetch(`${API_BASE_URL}/api/import/validate`, {
        method: "POST",
        body: formData,
        credentials: "include",
      });

      const result = await response.json();

      if (response.ok) {
        setValidationResult(result);
        if (result.valid) {
          setShowPreview(true);
        }
      } else {
        setError(result.error || "Validation failed");
      }
    } catch (err) {
      setError("Error validating file. Please try again.");
      console.error("Validation error:", err);
    } finally {
      setIsValidating(false);
    }
  };

  const handleColumnMappingConfirm = async (
    mapping: Record<string, string>
  ) => {
    if (!file) return;

    // Store the mapping for later use
    setColumnMappingData((prev) => (prev ? { ...prev, mapping } : null));

    // Close the mapping modal
    setShowColumnMapping(false);

    // Show campaign selection modal
    setShowCampaignSelection(true);
  };

  const handleImport = async () => {
    if (!file || !validationResult?.valid) return;

    // Show campaign selection modal instead of directly importing
    setShowCampaignSelection(true);
  };

  const handleCampaignSelection = async () => {
    if (!file) return;

    setIsImporting(true);
    setError(null);
    setShowCampaignSelection(false);

    try {
      const formData = new FormData();
      formData.append("file", file);

      // Add column mapping if available
      if (columnMappingData?.mapping) {
        formData.append(
          "columnMapping",
          JSON.stringify(columnMappingData.mapping)
        );
      }

      // Add campaign ID if not default
      if (selectedCampaignId !== "default") {
        formData.append("campaignId", selectedCampaignId);
      }

      // Use the new endpoint if we have column mapping, otherwise use the old one
      const endpoint = columnMappingData?.mapping
        ? `${API_BASE_URL}/api/import/process-with-mapping`
        : `${API_BASE_URL}/api/import/process`;

      const response = await fetch(endpoint, {
        method: "POST",
        body: formData,
        credentials: "include",
      });

      const result = await response.json();

      if (response.ok) {
        setImportJob(result);
        setShowPreview(false);
        // Start polling for job status
        pollJobStatus(result.jobId);
      } else {
        setError(result.error || "Import failed");
      }
    } catch (err) {
      setError("Error importing file. Please try again.");
      console.error("Import error:", err);
    } finally {
      setIsImporting(false);
    }
  };

  const pollJobStatus = async (jobId: string) => {
    const pollInterval = setInterval(async () => {
      try {
        const response = await fetch(
          `${API_BASE_URL}/api/import/jobs/${jobId}`,
          {
            credentials: "include",
          }
        );

        if (!response.ok) {
          console.error("Failed to fetch job status:", response.status);
          clearInterval(pollInterval);
          return;
        }

        const job = await response.json();

        setImportJob(job);

        if (
          job.status === "completed" ||
          job.status === "COMPLETED" ||
          job.status === "failed" ||
          job.status === "FAILED"
        ) {
          clearInterval(pollInterval);
        }
      } catch (err) {
        console.error("Error polling job status:", err);
        clearInterval(pollInterval);
      }
    }, 2000); // Poll every 2 seconds
  };

  const getStatusIcon = (status: string) => {
    const normalizedStatus = status?.toLowerCase();
    switch (normalizedStatus) {
      case "completed":
        return <CheckCircle className="h-5 w-5 text-green-500" />;
      case "failed":
        return <XCircle className="h-5 w-5 text-red-500" />;
      case "processing":
        return <AlertCircle className="h-5 w-5 text-blue-500 animate-spin" />;
      default:
        return <AlertCircle className="h-5 w-5 text-yellow-500" />;
    }
  };

  const getStatusText = (status: string) => {
    const normalizedStatus = status?.toLowerCase();
    switch (normalizedStatus) {
      case "pending":
        return "Pending";
      case "processing":
        return "Processing";
      case "completed":
        return "Completed";
      case "failed":
        return "Failed";
      default:
        return "Unknown";
    }
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="container mx-auto p-6 max-w-4xl">
          <div className="mb-8">
            <h1 className="text-3xl font-bold mb-2">Import Leads</h1>
            <p className="text-muted-foreground">
              Upload a CSV file to import leads into your account. Make sure
              your CSV has the required columns.
            </p>
          </div>

          <div className="grid gap-6">
            {/* File Upload Section */}
            <Card>
              <CardHeader>
                <CardTitle>Upload CSV File</CardTitle>
                <CardDescription>
                  Drag and drop your CSV file here, or click to browse. Maximum
                  file size: 25MB
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div
                  {...getRootProps()}
                  className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
                    isDragActive
                      ? "border-primary bg-primary/5"
                      : "border-muted-foreground/25 hover:border-primary/50"
                  }`}
                >
                  <input {...getInputProps()} />
                  <Upload className="h-12 w-12 mx-auto mb-4 text-muted-foreground" />
                  {isDragActive ? (
                    <p className="text-lg">Drop the file here...</p>
                  ) : (
                    <div>
                      <p className="text-lg mb-2">
                        Drag & drop your CSV file here
                      </p>
                      <p className="text-sm text-muted-foreground">
                        or click to browse
                      </p>
                    </div>
                  )}
                </div>

                {file && (
                  <div className="mt-4 p-4 bg-muted rounded-lg flex items-center gap-3">
                    <FileText className="h-5 w-5 text-primary" />
                    <div className="flex-1">
                      <p className="font-medium">{file.name}</p>
                      <p className="text-sm text-muted-foreground">
                        {(file.size / 1024 / 1024).toFixed(2)} MB
                      </p>
                    </div>
                  </div>
                )}

                {isDetectingColumns && (
                  <div className="mt-4 flex items-center gap-2">
                    <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-primary"></div>
                    <span>Detecting columns...</span>
                  </div>
                )}

                {isValidating && (
                  <div className="mt-4 flex items-center gap-2">
                    <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-primary"></div>
                    <span>Validating file...</span>
                  </div>
                )}

                {validationResult && !validationResult.valid && (
                  <Alert className="mt-4" variant="destructive">
                    <XCircle className="h-4 w-4" />
                    <AlertDescription>
                      <div>
                        <p className="font-medium mb-2">Validation failed:</p>
                        <ul className="list-disc list-inside space-y-1">
                          {validationResult.errors.map((error, index) => (
                            <li key={index} className="text-sm">
                              {error}
                            </li>
                          ))}
                        </ul>
                      </div>
                    </AlertDescription>
                  </Alert>
                )}

                {validationResult && validationResult.valid && (
                  <Alert className="mt-4" variant="default">
                    <CheckCircle className="h-4 w-4" />
                    <AlertDescription>
                      File validated successfully! {validationResult.totalRows}{" "}
                      rows found.
                    </AlertDescription>
                  </Alert>
                )}

                {error && (
                  <Alert className="mt-4" variant="destructive">
                    <XCircle className="h-4 w-4" />
                    <AlertDescription>{error}</AlertDescription>
                  </Alert>
                )}

                {validationResult && validationResult.valid && (
                  <div className="mt-4 flex gap-2">
                    <Button
                      onClick={() => setShowPreview(true)}
                      variant="outline"
                    >
                      Preview Data
                    </Button>
                    <Button onClick={handleImport} disabled={isImporting}>
                      {isImporting ? "Importing..." : "Import Leads"}
                    </Button>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Import Progress */}
            {importJob && (
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    {getStatusIcon(importJob.status)}
                    Import Progress
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div>
                      <p className="font-medium">{importJob.filename}</p>
                      <p className="text-sm text-muted-foreground">
                        Status: {getStatusText(importJob.status)}
                      </p>
                    </div>

                    {importJob.status === "processing" && (
                      <div className="space-y-2">
                        <div className="flex justify-between text-sm">
                          <span>Progress</span>
                          <span>
                            {importJob.processedRows} / {importJob.totalRows}
                          </span>
                        </div>
                        <Progress
                          value={
                            (importJob.processedRows / importJob.totalRows) *
                            100
                          }
                          className="w-full"
                        />
                      </div>
                    )}

                    {importJob.status === "completed" && (
                      <Alert>
                        <CheckCircle className="h-4 w-4" />
                        <AlertDescription>
                          Successfully imported {importJob.processedRows} leads!
                          {importJob.errorRows > 0 &&
                            ` ${importJob.errorRows} rows had errors.`}
                        </AlertDescription>
                      </Alert>
                    )}

                    {importJob.status === "failed" && (
                      <Alert variant="destructive">
                        <XCircle className="h-4 w-4" />
                        <AlertDescription>
                          Import failed: {importJob.errorMessage}
                        </AlertDescription>
                      </Alert>
                    )}
                  </div>
                </CardContent>
              </Card>
            )}

            {/* Import History */}
            <ImportHistory />
          </div>

          {/* Column Mapping Modal */}
          {showColumnMapping && columnMappingData && (
            <ColumnMappingModal
              isOpen={showColumnMapping}
              onClose={() => setShowColumnMapping(false)}
              onConfirm={handleColumnMappingConfirm}
              data={columnMappingData}
              isLoading={isImporting}
            />
          )}

          {/* Preview Modal */}
          {showPreview && validationResult && (
            <CsvPreviewModal
              data={validationResult.data}
              onClose={() => setShowPreview(false)}
              onImport={handleImport}
              isImporting={isImporting}
            />
          )}

          {/* Campaign Selection Modal */}
          <Dialog
            open={showCampaignSelection}
            onOpenChange={setShowCampaignSelection}
          >
            <DialogContent className="max-w-md">
              <DialogHeader>
                <DialogTitle>Select Campaign for Import</DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                <div>
                  <label className="text-sm font-medium mb-2 block">
                    Choose where to import your leads
                  </label>
                  <div className="space-y-2">
                    <Input
                      placeholder="Search campaigns..."
                      value={campaignSearchTerm}
                      onChange={(e) => setCampaignSearchTerm(e.target.value)}
                      className="mb-2"
                    />
                    <Select
                      value={selectedCampaignId}
                      onValueChange={setSelectedCampaignId}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Select campaign..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="default">
                          Default (No Campaign)
                        </SelectItem>
                        {campaigns
                          .filter((campaign) =>
                            campaign.name
                              .toLowerCase()
                              .includes(campaignSearchTerm.toLowerCase())
                          )
                          .map((campaign) => (
                            <SelectItem key={campaign.id} value={campaign.id}>
                              {campaign.name}
                            </SelectItem>
                          ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <div className="text-sm text-muted-foreground">
                  {validationResult?.totalRows} lead(s) will be imported to the
                  selected campaign.
                </div>
                <div className="flex gap-2 justify-end">
                  <Button
                    variant="outline"
                    onClick={() => setShowCampaignSelection(false)}
                  >
                    Cancel
                  </Button>
                  <Button
                    onClick={handleCampaignSelection}
                    disabled={isImporting}
                  >
                    {isImporting ? "Importing..." : "Import Leads"}
                  </Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
