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
import {
  ChevronLeft,
  ChevronRight,
  Mail,
  ExternalLink,
  Plus,
} from "lucide-react";
import { CampaignSelectionModal } from "@/components/shared/CampaignSelectionModal";
import {
  createLeadsFromHunterData,
  addLeadsToCampaign,
  HunterEmailData,
} from "@/lib/leadUtils";
import { useToast } from "@/components/ui/use-toast";

// Use the HunterEmailData type from leadUtils
type EmailData = HunterEmailData;

interface EmailResultsModalProps {
  emails: EmailData[];
  companyName: string;
  domain: string;
  onClose: () => void;
}

const ITEMS_PER_PAGE = 10;

export function EmailResultsModal({
  emails,
  companyName,
  domain,
  onClose,
}: EmailResultsModalProps) {
  const [currentPage, setCurrentPage] = useState(1);
  const [showCampaignSelection, setShowCampaignSelection] = useState(false);
  const [isAddingToCampaign, setIsAddingToCampaign] = useState(false);
  const [selectedEmails, setSelectedEmails] = useState<Set<number>>(new Set());
  const { toast } = useToast();
  const totalPages = Math.ceil(emails.length / ITEMS_PER_PAGE);

  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const endIndex = startIndex + ITEMS_PER_PAGE;
  const currentData = emails.slice(startIndex, endIndex);

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

  const getConfidenceBadgeVariant = (confidence: number) => {
    if (confidence >= 90) return "default";
    if (confidence >= 70) return "secondary";
    return "destructive";
  };

  const getVerificationBadgeVariant = (status: string) => {
    switch (status) {
      case "deliverable":
        return "default"; // valid
      case "accept_all":
        return "secondary"; // risky
      case "undeliverable":
        return "destructive"; // invalid
      default:
        return "outline"; // unknown
    }
  };

  const mapVerificationStatus = (hunterStatus: string) => {
    switch (hunterStatus) {
      case "deliverable":
        return "valid";
      case "accept_all":
        return "risky";
      case "undeliverable":
        return "invalid";
      default:
        return "unknown";
    }
  };

  const formatValue = (value: string | null, maxLength: number = 30) => {
    if (!value) return "-";
    return value.length > maxLength
      ? `${value.substring(0, maxLength)}...`
      : value;
  };

  const handleSelectEmail = (index: number) => {
    const globalIndex = startIndex + index;
    setSelectedEmails((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(globalIndex)) {
        newSet.delete(globalIndex);
      } else {
        newSet.add(globalIndex);
      }
      return newSet;
    });
  };

  const handleSelectAll = () => {
    const allIndices = new Set(
      Array.from({ length: emails.length }, (_, i) => i)
    );
    setSelectedEmails(
      selectedEmails.size === emails.length ? new Set() : allIndices
    );
  };

  const handleAddToCampaign = () => {
    if (selectedEmails.size === 0) {
      toast({
        title: "No emails selected",
        description: "Please select at least one email to add to campaign.",
        variant: "destructive",
      });
      return;
    }
    setShowCampaignSelection(true);
  };

  const handleCampaignSelection = async (campaignId: string) => {
    if (selectedEmails.size === 0) return;

    setIsAddingToCampaign(true);
    setShowCampaignSelection(false);

    try {
      // Get selected email data
      const selectedEmailData = Array.from(selectedEmails).map(
        (index) => emails[index]
      );

      // Create leads from Hunter data
      const result = await createLeadsFromHunterData(
        selectedEmailData,
        domain,
        campaignId === "default" ? undefined : campaignId
      );

      if (result.success && result.leadIds) {
        toast({
          title: "Success!",
          description: `Successfully added ${result.leadIds.length} leads to campaign.`,
        });

        // Clear selection and close modal
        setSelectedEmails(new Set());
        onClose();
      } else {
        throw new Error(result.error || "Failed to create leads");
      }
    } catch (error) {
      console.error("Error adding leads to campaign:", error);
      toast({
        title: "Error",
        description:
          error instanceof Error
            ? error.message
            : "Failed to add leads to campaign",
        variant: "destructive",
      });
    } finally {
      setIsAddingToCampaign(false);
    }
  };

  return (
    <Dialog open={true} onOpenChange={onClose}>
      <DialogContent className="max-w-[95vw] max-h-[90vh] w-[95vw] h-[90vh] flex flex-col">
        <DialogHeader className="flex-shrink-0">
          <DialogTitle className="flex items-center gap-2">
            <Mail className="h-5 w-5" />
            Email Discovery Results
          </DialogTitle>
          <p className="text-sm text-muted-foreground">
            Found {emails.length} emails for <strong>{companyName}</strong> (
            {domain})
          </p>
        </DialogHeader>

        <div className="flex-1 overflow-hidden flex flex-col">
          {/* Table */}
          <div className="flex-1 overflow-auto border rounded-lg">
            <Table>
              <TableHeader className="sticky top-0 bg-background">
                <TableRow>
                  <TableHead className="w-12">
                    <input
                      type="checkbox"
                      checked={
                        selectedEmails.size === emails.length &&
                        emails.length > 0
                      }
                      onChange={handleSelectAll}
                      className="rounded"
                    />
                  </TableHead>
                  <TableHead className="whitespace-nowrap">
                    First Name
                  </TableHead>
                  <TableHead className="whitespace-nowrap">Last Name</TableHead>
                  <TableHead className="whitespace-nowrap">Domain</TableHead>
                  <TableHead className="whitespace-nowrap">Email</TableHead>
                  <TableHead className="whitespace-nowrap">Position</TableHead>
                  <TableHead className="whitespace-nowrap">LinkedIn</TableHead>
                  <TableHead className="whitespace-nowrap">
                    Verified Status
                  </TableHead>
                  <TableHead className="whitespace-nowrap">
                    Confidence
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {currentData.map((email, index) => {
                  const globalIndex = startIndex + index;
                  const isSelected = selectedEmails.has(globalIndex);
                  return (
                    <TableRow
                      key={startIndex + index}
                      className={isSelected ? "bg-muted/50" : ""}
                    >
                      <TableCell>
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => handleSelectEmail(index)}
                          className="rounded"
                        />
                      </TableCell>
                      <TableCell className="font-medium">
                        {email.first_name}
                      </TableCell>
                      <TableCell className="font-medium">
                        {email.last_name}
                      </TableCell>
                      <TableCell>
                        <span className="text-blue-600 font-mono text-sm">
                          {domain}
                        </span>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <Mail className="h-4 w-4 text-muted-foreground" />
                          <span className="text-blue-600">{email.value}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="max-w-[200px]">
                          <div className="truncate" title={email.position_raw}>
                            {formatValue(email.position, 25)}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        {email.linkedin ? (
                          <Button variant="ghost" size="sm" asChild>
                            <a
                              href={email.linkedin}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-blue-600 hover:text-blue-800"
                            >
                              <ExternalLink className="h-4 w-4" />
                            </a>
                          </Button>
                        ) : (
                          "-"
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={getVerificationBadgeVariant(
                            email.verification.status
                          )}
                        >
                          {mapVerificationStatus(email.verification.status)}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={getConfidenceBadgeVariant(email.confidence)}
                        >
                          {email.confidence}%
                        </Badge>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex-shrink-0 flex items-center justify-between mt-4">
              <div className="text-sm text-muted-foreground">
                Showing {startIndex + 1} to {Math.min(endIndex, emails.length)}{" "}
                of {emails.length} emails
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
        <div className="flex-shrink-0 flex justify-between items-center pt-4 border-t">
          <div className="text-sm text-muted-foreground">
            {selectedEmails.size > 0 && (
              <span>{selectedEmails.size} email(s) selected</span>
            )}
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={onClose}>
              Close
            </Button>
            <Button
              onClick={handleAddToCampaign}
              disabled={selectedEmails.size === 0 || isAddingToCampaign}
            >
              <Plus className="h-4 w-4 mr-2" />
              {isAddingToCampaign ? "Adding..." : "Add to Campaign"}
            </Button>
          </div>
        </div>
      </DialogContent>

      {/* Campaign Selection Modal */}
      <CampaignSelectionModal
        isOpen={showCampaignSelection}
        onClose={() => setShowCampaignSelection(false)}
        onConfirm={handleCampaignSelection}
        title="Add Emails to Campaign"
        description="Choose which campaign to add the selected emails to"
        itemCount={selectedEmails.size}
        itemName="email(s)"
        isLoading={isAddingToCampaign}
      />
    </Dialog>
  );
}
