"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { ChevronLeft, ChevronRight, X } from "lucide-react";

interface CsvPreviewModalProps {
  data: Record<string, string>[];
  onClose: () => void;
  onImport: () => void;
  isImporting: boolean;
}

const ITEMS_PER_PAGE = 20;

export function CsvPreviewModal({
  data,
  onClose,
  onImport,
  isImporting,
}: CsvPreviewModalProps) {
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.ceil(data.length / ITEMS_PER_PAGE);

  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const endIndex = startIndex + ITEMS_PER_PAGE;
  const currentData = data.slice(startIndex, endIndex);

  const headers = data.length > 0 ? Object.keys(data[0]) : [];

  const getPageNumbers = () => {
    const pages = [];
    const maxVisible = 5;

    if (totalPages <= maxVisible) {
      for (let i = 1; i <= totalPages; i++) {
        pages.push(i);
      }
    } else {
      const start = Math.max(1, currentPage - 2);
      const end = Math.min(totalPages, start + maxVisible - 1);

      for (let i = start; i <= end; i++) {
        pages.push(i);
      }
    }

    return pages;
  };

  const formatValue = (value: string) => {
    if (!value) return "-";
    return value.length > 50 ? `${value.substring(0, 50)}...` : value;
  };

  const getColumnBadgeVariant = (column: string) => {
    const requiredColumns = ["email", "first_name"];
    const importantColumns = ["company", "domain", "title"];

    if (requiredColumns.includes(column)) {
      return "destructive";
    } else if (importantColumns.includes(column)) {
      return "default";
    } else {
      return "secondary";
    }
  };

  return (
    <Dialog open={true} onOpenChange={onClose}>
      <DialogContent className="max-w-[90vw] max-h-[90vh] w-[90vw] h-[90vh] flex flex-col">
        <DialogHeader className="flex-shrink-0">
          <div className="flex items-center justify-between">
            <DialogTitle>Preview CSV Data</DialogTitle>
            <Button variant="ghost" size="sm" onClick={onClose}>
              <X className="h-4 w-4" />
            </Button>
          </div>
          <p className="text-sm text-muted-foreground">
            Showing {data.length} rows. Review the data before importing.
          </p>
        </DialogHeader>

        <div className="flex-1 overflow-hidden flex flex-col">
          {/* Column Headers with Badges */}
          <div className="mb-4 flex flex-wrap gap-2">
            {headers.map((header) => (
              <Badge key={header} variant={getColumnBadgeVariant(header)}>
                {header.replace("_", " ").toUpperCase()}
                {["email", "first_name"].includes(header) && " *"}
              </Badge>
            ))}
          </div>

          {/* Table */}
          <div className="flex-1 overflow-auto border rounded-lg">
            <Table>
              <TableHeader className="sticky top-0 bg-background">
                <TableRow>
                  {headers.map((header) => (
                    <TableHead key={header} className="whitespace-nowrap">
                      {header.replace("_", " ").toUpperCase()}
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {currentData.map((row, index) => (
                  <TableRow key={startIndex + index}>
                    {headers.map((header) => (
                      <TableCell key={header} className="max-w-[200px]">
                        <div className="truncate" title={row[header] || ""}>
                          {formatValue(row[header] || "")}
                        </div>
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex-shrink-0 flex items-center justify-between mt-4">
              <div className="text-sm text-muted-foreground">
                Showing {startIndex + 1} to {Math.min(endIndex, data.length)} of{" "}
                {data.length} rows
              </div>

              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setCurrentPage((prev) => Math.max(1, prev - 1))
                  }
                  disabled={currentPage === 1}
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>

                {getPageNumbers().map((page) => (
                  <Button
                    key={page}
                    variant={currentPage === page ? "default" : "outline"}
                    size="sm"
                    onClick={() => setCurrentPage(page)}
                    className="w-8 h-8 p-0"
                  >
                    {page}
                  </Button>
                ))}

                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setCurrentPage((prev) => Math.min(totalPages, prev + 1))
                  }
                  disabled={currentPage === totalPages}
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex-shrink-0 flex justify-end gap-2 pt-4 border-t">
          <Button variant="outline" onClick={onClose} disabled={isImporting}>
            Cancel
          </Button>
          <Button onClick={onImport} disabled={isImporting}>
            {isImporting ? "Importing..." : `Import ${data.length} Leads`}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
