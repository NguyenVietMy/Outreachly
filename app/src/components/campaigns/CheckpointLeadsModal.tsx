"use client";

import { useState, useEffect, useMemo } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Search, Filter, Users, AlertTriangle, Mail, User } from "lucide-react";
import { useLeads, Lead } from "@/hooks/useLeads";
import { useCampaignCheckpoints } from "@/hooks/useCampaignCheckpoints";

interface FilterState {
  verifiedStatus: string;
  position: string;
  source: string;
  dateRange: string;
  hasEmail: boolean;
  hasLinkedIn: boolean;
}

interface CheckpointLeadsModalProps {
  isOpen: boolean;
  onClose: () => void;
  campaignId: string;
  checkpointId: string;
  checkpointName: string;
}

export default function CheckpointLeadsModal({
  isOpen,
  onClose,
  campaignId,
  checkpointId,
  checkpointName,
}: CheckpointLeadsModalProps) {
  const [selectedLeads, setSelectedLeads] = useState<string[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [filters, setFilters] = useState<FilterState>({
    verifiedStatus: "all",
    position: "all",
    source: "all",
    dateRange: "all",
    hasEmail: false,
    hasLinkedIn: false,
  });
  const [isAssigning, setIsAssigning] = useState(false);

  const API_URL =
    process.env.NEXT_PUBLIC_API_URL || "https://api.outreach-ly.com";

  // Get leads for this campaign
  const { leads, loading } = useLeads(campaignId);

  // Get checkpoint data to check which leads are already assigned
  const { checkpoints } = useCampaignCheckpoints(campaignId);
  const currentCheckpoint = checkpoints.find((cp) => cp.id === checkpointId);

  // State for assigned leads
  const [assignedLeads, setAssignedLeads] = useState<string[]>([]);
  const [loadingAssignedLeads, setLoadingAssignedLeads] = useState(false);

  // Fetch assigned leads when modal opens
  useEffect(() => {
    if (isOpen && checkpointId) {
      fetchAssignedLeads();
    }
  }, [isOpen, checkpointId]);

  const fetchAssignedLeads = async () => {
    setLoadingAssignedLeads(true);
    try {
      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints/${checkpointId}/leads`,
        {
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
        }
      );

      if (response.ok) {
        const checkpointLeads = await response.json();
        const leadIds = checkpointLeads.map((cl: any) => cl.leadId);
        setAssignedLeads(leadIds);
      }
    } catch (error) {
      console.error("Failed to fetch assigned leads:", error);
    } finally {
      setLoadingAssignedLeads(false);
    }
  };

  // Filter leads based on search and filters
  const filteredLeads = useMemo(() => {
    return leads.filter((lead) => {
      // Search filter
      if (searchTerm) {
        const searchLower = searchTerm.toLowerCase();
        const matchesSearch =
          lead.firstName.toLowerCase().includes(searchLower) ||
          lead.lastName.toLowerCase().includes(searchLower) ||
          lead.email.toLowerCase().includes(searchLower) ||
          (lead.position &&
            lead.position.toLowerCase().includes(searchLower)) ||
          (lead.domain && lead.domain.toLowerCase().includes(searchLower));

        if (!matchesSearch) return false;
      }

      // Verified status filter
      if (
        filters.verifiedStatus !== "all" &&
        lead.verifiedStatus !== filters.verifiedStatus
      ) {
        return false;
      }

      // Position filter
      if (filters.position !== "all" && lead.position !== filters.position) {
        return false;
      }

      // Source filter
      if (filters.source !== "all" && lead.source !== filters.source) {
        return false;
      }

      // Has email filter
      if (filters.hasEmail && !lead.email) {
        return false;
      }

      // Has LinkedIn filter
      if (filters.hasLinkedIn && !lead.linkedinUrl) {
        return false;
      }

      return true;
    });
  }, [leads, searchTerm, filters]);

  // Get unique values for filter dropdowns
  const verifiedStatuses = useMemo(() => {
    const statuses = new Set(leads.map((lead) => lead.verifiedStatus));
    return Array.from(statuses);
  }, [leads]);

  const positions = useMemo(() => {
    const pos = new Set(leads.map((lead) => lead.position).filter(Boolean));
    return Array.from(pos);
  }, [leads]);

  const sources = useMemo(() => {
    const src = new Set(leads.map((lead) => lead.source).filter(Boolean));
    return Array.from(src);
  }, [leads]);

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      // Select all leads that are not already assigned and have required fields
      const selectableLeads = filteredLeads.filter(
        (lead) => !isLeadAssigned(lead.id) && lead.email && lead.firstName
      );
      setSelectedLeads(selectableLeads.map((lead) => lead.id));
    } else {
      setSelectedLeads([]);
    }
  };

  const handleSelectLead = (leadId: string, checked: boolean) => {
    if (checked) {
      setSelectedLeads((prev) => [...prev, leadId]);
    } else {
      setSelectedLeads((prev) => prev.filter((id) => id !== leadId));
    }
  };

  const handleAssignToCheckpoint = async () => {
    if (selectedLeads.length === 0) return;

    console.log("Assigning leads to checkpoint:", {
      campaignId,
      checkpointId,
      leadIds: selectedLeads,
      leadCount: selectedLeads.length,
    });

    setIsAssigning(true);
    try {
      const response = await fetch(
        `${API_URL}/api/campaigns/${campaignId}/checkpoints/${checkpointId}/leads`,
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ leadIds: selectedLeads }),
        }
      );

      console.log("Response status:", response.status);

      if (!response.ok) {
        const errorText = await response.text();
        console.error("Response error:", errorText);
        throw new Error("Failed to assign leads to checkpoint");
      }

      const result = await response.json();
      console.log("Assignment result:", result);

      setSelectedLeads([]);
      onClose();
    } catch (error) {
      console.error("Assignment error:", error);
      alert("Failed to assign leads to checkpoint. Please try again.");
    } finally {
      setIsAssigning(false);
    }
  };

  const isLeadAssigned = (leadId: string) => assignedLeads.includes(leadId);
  const isLeadSelectable = (lead: Lead) => lead.email && lead.firstName;

  const allSelectableSelected = useMemo(() => {
    const selectableLeads = filteredLeads.filter(
      (lead) => !isLeadAssigned(lead.id) && isLeadSelectable(lead)
    );
    return (
      selectableLeads.length > 0 &&
      selectableLeads.every((lead) => selectedLeads.includes(lead.id))
    );
  }, [filteredLeads, selectedLeads, assignedLeads]);

  const someSelectableSelected = useMemo(() => {
    const selectableLeads = filteredLeads.filter(
      (lead) => !isLeadAssigned(lead.id) && isLeadSelectable(lead)
    );
    return selectableLeads.some((lead) => selectedLeads.includes(lead.id));
  }, [filteredLeads, selectedLeads, assignedLeads]);

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="w-[80vw] h-[80vh] max-w-none max-h-none">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Users className="h-5 w-5" />
            Assign Leads to "{checkpointName}"
          </DialogTitle>
        </DialogHeader>

        <div className="flex flex-col h-full">
          {/* Search and Filters */}
          <div className="flex items-center gap-4 mb-4">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
              <Input
                placeholder="Search leads by name, email, position, or domain..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>
            <Button
              variant="outline"
              onClick={() => setShowFilters(!showFilters)}
              className="flex items-center gap-2"
            >
              <Filter className="h-4 w-4" />
              Filters
            </Button>
          </div>

          {/* Filters Panel */}
          {showFilters && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4 p-4 bg-gray-50 rounded-lg">
              <div>
                <label className="text-sm font-medium mb-2 block">
                  Verified Status
                </label>
                <Select
                  value={filters.verifiedStatus}
                  onValueChange={(value) =>
                    setFilters((prev) => ({ ...prev, verifiedStatus: value }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Statuses</SelectItem>
                    {verifiedStatuses.map((status) => (
                      <SelectItem key={status} value={status}>
                        {status.charAt(0).toUpperCase() + status.slice(1)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <label className="text-sm font-medium mb-2 block">
                  Position
                </label>
                <Select
                  value={filters.position}
                  onValueChange={(value) =>
                    setFilters((prev) => ({ ...prev, position: value }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Positions</SelectItem>
                    {positions.map((position) => (
                      <SelectItem key={position} value={position || ""}>
                        {position}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <label className="text-sm font-medium mb-2 block">Source</label>
                <Select
                  value={filters.source}
                  onValueChange={(value) =>
                    setFilters((prev) => ({ ...prev, source: value }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Sources</SelectItem>
                    {sources.map((source) => (
                      <SelectItem key={source} value={source}>
                        {source}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium">
                  Additional Filters
                </label>
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="hasEmail"
                    checked={filters.hasEmail}
                    onCheckedChange={(checked) =>
                      setFilters((prev) => ({ ...prev, hasEmail: !!checked }))
                    }
                  />
                  <label htmlFor="hasEmail" className="text-sm">
                    Has Email
                  </label>
                </div>
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="hasLinkedIn"
                    checked={filters.hasLinkedIn}
                    onCheckedChange={(checked) =>
                      setFilters((prev) => ({
                        ...prev,
                        hasLinkedIn: !!checked,
                      }))
                    }
                  />
                  <label htmlFor="hasLinkedIn" className="text-sm">
                    Has LinkedIn
                  </label>
                </div>
              </div>
            </div>
          )}

          {/* Action Bar */}
          <div className="flex items-center justify-between mb-4 p-3 bg-blue-50 rounded-lg">
            <div className="flex items-center gap-4">
              <div className="flex items-center space-x-2">
                <Checkbox
                  checked={allSelectableSelected}
                  onCheckedChange={handleSelectAll}
                  ref={(el) => {
                    if (el)
                      el.indeterminate =
                        someSelectableSelected && !allSelectableSelected;
                  }}
                />
                <label className="text-sm font-medium">
                  Select All (
                  {
                    filteredLeads.filter(
                      (lead) =>
                        !isLeadAssigned(lead.id) && isLeadSelectable(lead)
                    ).length
                  }{" "}
                  selectable)
                </label>
              </div>
              <Badge variant="secondary">{selectedLeads.length} selected</Badge>
            </div>
            <Button
              onClick={handleAssignToCheckpoint}
              disabled={selectedLeads.length === 0 || isAssigning}
              className="flex items-center gap-2"
            >
              {isAssigning ? "Assigning..." : "Add to Checkpoint"}
            </Button>
          </div>

          {/* Table */}
          <div className="flex-1 overflow-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12"></TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Position</TableHead>
                  <TableHead>Company</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Source</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center py-8">
                      Loading leads...
                    </TableCell>
                  </TableRow>
                ) : filteredLeads.length === 0 ? (
                  <TableRow>
                    <TableCell
                      colSpan={8}
                      className="text-center py-8 text-gray-500"
                    >
                      No leads found matching your criteria.
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredLeads.map((lead) => {
                    const isAssigned = isLeadAssigned(lead.id);
                    const isSelectable = isLeadSelectable(lead);
                    const isSelected = selectedLeads.includes(lead.id);
                    const hasWarnings = !lead.email || !lead.firstName;

                    return (
                      <TableRow
                        key={lead.id}
                        className={isAssigned ? "bg-gray-50" : ""}
                      >
                        <TableCell>
                          <Checkbox
                            checked={isSelected}
                            onCheckedChange={(checked) =>
                              handleSelectLead(lead.id, !!checked)
                            }
                            disabled={isAssigned || !isSelectable}
                          />
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <span className="font-medium">
                              {lead.firstName} {lead.lastName}
                            </span>
                            {!lead.firstName && (
                              <AlertTriangle
                                className="h-4 w-4 text-red-500"
                                title="Missing first name"
                              />
                            )}
                          </div>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <span>{lead.email || "No email"}</span>
                            {!lead.email && (
                              <AlertTriangle
                                className="h-4 w-4 text-red-500"
                                title="Missing email"
                              />
                            )}
                          </div>
                        </TableCell>
                        <TableCell>{lead.position || "-"}</TableCell>
                        <TableCell>{lead.domain || "-"}</TableCell>
                        <TableCell>
                          <Badge
                            variant={
                              lead.verifiedStatus === "valid"
                                ? "default"
                                : lead.verifiedStatus === "risky"
                                  ? "secondary"
                                  : lead.verifiedStatus === "invalid"
                                    ? "destructive"
                                    : "outline"
                            }
                          >
                            {lead.verifiedStatus}
                          </Badge>
                        </TableCell>
                        <TableCell>{lead.source || "-"}</TableCell>
                        <TableCell>
                          {isAssigned ? (
                            <Badge variant="outline" className="text-green-600">
                              Already Assigned
                            </Badge>
                          ) : !isSelectable ? (
                            <Badge variant="outline" className="text-red-600">
                              Missing Required Fields
                            </Badge>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
