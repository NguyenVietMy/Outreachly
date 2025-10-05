"use client";

import { useState, useMemo, useEffect } from "react";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Search,
  Filter,
  Plus,
  Mail,
  Eye,
  History,
  Download,
  Users,
  Loader2,
} from "lucide-react";
import { useLeads, Lead } from "@/hooks/useLeads";
import { useCampaigns, Campaign } from "@/hooks/useCampaigns";
import Link from "next/link";
import { listTemplates, parseContent, TemplateModel } from "@/lib/templates";
import TemplateBrowserModal from "@/components/templates/TemplateBrowserModal";

// Activity mock stays for now; templates will be fetched live

const mockActivities = [
  {
    id: "1",
    email: "john.doe@techcorp.com",
    operationStatus: "sent",
    emailStatus: "delivered",
    timestamp: "2024-01-15 10:30 AM",
  },
  {
    id: "2",
    email: "jane@designstudio.com",
    operationStatus: "sending",
    emailStatus: "pending",
    timestamp: "2024-01-15 11:15 AM",
  },
  {
    id: "3",
    email: "bob.wilson@marketinginc.com",
    operationStatus: "sent",
    emailStatus: "bounced",
    timestamp: "2024-01-15 09:45 AM",
  },
];

interface FilterState {
  verifiedStatus: string;
  position: string;
  source: string;
  dateRange: string;
  hasEmail: boolean;
  hasPhone: boolean;
  hasLinkedIn: boolean;
}

export default function LeadsPage() {
  const [selectedCampaignId, setSelectedCampaignId] = useState<
    string | undefined
  >(undefined);
  const {
    campaigns,
    loading: campaignsLoading,
    createCampaign,
  } = useCampaigns();
  const {
    leads,
    loading,
    error,
    refetch,
    enrichLeads,
    exportLeads,
    assignLeadsToCampaign,
    removeLeadsFromCampaign,
  } = useLeads(selectedCampaignId);
  const [selectedLeads, setSelectedLeads] = useState<string[]>([]);
  const [showLeadModal, setShowLeadModal] = useState(false);
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);
  const [showTemplatePreview, setShowTemplatePreview] = useState(false);
  const [selectedTemplate, setSelectedTemplate] =
    useState<TemplateModel | null>(null);
  const [recentTemplates, setRecentTemplates] = useState<TemplateModel[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templatesError, setTemplatesError] = useState<string | null>(null);
  const [allTemplates, setAllTemplates] = useState<TemplateModel[]>([]);
  const [showAllTemplates, setShowAllTemplates] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [showCreateCampaign, setShowCreateCampaign] = useState(false);
  const [showCampaignImport, setShowCampaignImport] = useState(false);
  const [campaignForm, setCampaignForm] = useState({
    name: "",
    description: "",
  });
  const [buttonLoading, setButtonLoading] = useState({
    enrich: false,
    export: false,
    campaignImport: false,
    removeFromCampaign: false,
  });
  const [selectedCampaignForImport, setSelectedCampaignForImport] =
    useState<string>("");
  const [filters, setFilters] = useState<FilterState>({
    verifiedStatus: "all",
    position: "",
    source: "",
    dateRange: "all",
    hasEmail: false,
    hasPhone: false,
    hasLinkedIn: false,
  });

  // Filter leads based on search term and filters
  const filteredLeads = useMemo(() => {
    let filtered = leads;

    // Apply search term filter
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      filtered = filtered.filter(
        (lead) =>
          lead.firstName?.toLowerCase().includes(term) ||
          lead.lastName?.toLowerCase().includes(term) ||
          lead.email?.toLowerCase().includes(term) ||
          lead.position?.toLowerCase().includes(term) ||
          lead.department?.toLowerCase().includes(term)
      );
    }

    // Apply verified status filter
    if (filters.verifiedStatus !== "all") {
      filtered = filtered.filter(
        (lead) => lead.verifiedStatus === filters.verifiedStatus
      );
    }

    // Apply position filter
    if (filters.position) {
      const positionTerm = filters.position.toLowerCase();
      filtered = filtered.filter((lead) =>
        lead.position?.toLowerCase().includes(positionTerm)
      );
    }

    // Apply source filter
    if (filters.source) {
      const sourceTerm = filters.source.toLowerCase();
      filtered = filtered.filter((lead) =>
        lead.source?.toLowerCase().includes(sourceTerm)
      );
    }

    // Apply date range filter
    if (filters.dateRange !== "all") {
      const now = new Date();
      const daysAgo =
        filters.dateRange === "today"
          ? 1
          : filters.dateRange === "week"
            ? 7
            : filters.dateRange === "month"
              ? 30
              : 0;

      if (daysAgo > 0) {
        const cutoffDate = new Date(
          now.getTime() - daysAgo * 24 * 60 * 60 * 1000
        );
        filtered = filtered.filter((lead) => {
          const leadDate = new Date(lead.createdAt);
          return leadDate >= cutoffDate;
        });
      }
    }

    // Apply contact information filters
    if (filters.hasEmail) {
      filtered = filtered.filter(
        (lead) => lead.email && lead.email.trim() !== ""
      );
    }

    if (filters.hasPhone) {
      filtered = filtered.filter(
        (lead) => lead.phone && lead.phone.trim() !== ""
      );
    }

    if (filters.hasLinkedIn) {
      filtered = filtered.filter(
        (lead) => lead.linkedinUrl && lead.linkedinUrl.trim() !== ""
      );
    }

    return filtered;
  }, [leads, searchTerm, filters]);

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedLeads(filteredLeads.map((lead) => lead.id));
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

  const handleLeadClick = (lead: Lead) => {
    setSelectedLead(lead);
    setShowLeadModal(true);
  };

  const handleTemplatePreview = (template: any) => {
    setSelectedTemplate(template);
    setShowTemplatePreview(true);
  };

  const handleFilterChange = (
    key: keyof FilterState,
    value: string | boolean
  ) => {
    setFilters((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  const resetFilters = () => {
    setFilters({
      verifiedStatus: "all",
      position: "",
      source: "",
      dateRange: "all",
      hasEmail: false,
      hasPhone: false,
      hasLinkedIn: false,
    });
    setSearchTerm("");
  };

  // Load recent templates (latest 3)
  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setTemplatesLoading(true);
      setTemplatesError(null);
      try {
        const templates = await listTemplates();
        if (!mounted) return;
        setAllTemplates(templates);
        setRecentTemplates(templates.slice(0, 3));
      } catch (e: any) {
        if (!mounted) return;
        setTemplatesError(e?.message || "Failed to load templates");
      } finally {
        if (mounted) setTemplatesLoading(false);
      }
    };
    load();
    return () => {
      mounted = false;
    };
  }, []);

  const getActiveFiltersCount = () => {
    let count = 0;
    if (filters.verifiedStatus !== "all") count++;
    if (filters.position) count++;
    if (filters.source) count++;
    if (filters.dateRange !== "all") count++;
    if (filters.hasEmail) count++;
    if (filters.hasPhone) count++;
    if (filters.hasLinkedIn) count++;
    return count;
  };

  const handleCreateCampaign = async () => {
    if (!campaignForm.name.trim()) return;

    try {
      await createCampaign(campaignForm.name, campaignForm.description);
      setCampaignForm({ name: "", description: "" });
      setShowCreateCampaign(false);
    } catch (error) {
      console.error("Error creating campaign:", error);
    }
  };

  const handleCampaignChange = (campaignId: string) => {
    setSelectedCampaignId(campaignId === "all" ? undefined : campaignId);
    setSelectedLeads([]); // Clear selected leads when switching campaigns
  };

  const getCurrentCampaignName = () => {
    if (!selectedCampaignId) return "All Leads";
    const campaign = campaigns.find((c) => c.id === selectedCampaignId);
    return campaign ? campaign.name : "Unknown Campaign";
  };

  // Button handlers
  const handleEnrich = async () => {
    if (selectedLeads.length === 0) return;

    setButtonLoading((prev) => ({ ...prev, enrich: true }));
    try {
      await enrichLeads(selectedLeads);
      setSelectedLeads([]);
      // Show success message or toast
    } catch (error) {
      console.error("Error enriching leads:", error);
      // Show error message or toast
    } finally {
      setButtonLoading((prev) => ({ ...prev, enrich: false }));
    }
  };

  const handleExport = async () => {
    if (selectedLeads.length === 0) return;

    setButtonLoading((prev) => ({ ...prev, export: true }));
    try {
      await exportLeads(selectedLeads);
      setSelectedLeads([]);
      // Show success message or toast
    } catch (error) {
      console.error("Error exporting leads:", error);
      // Show error message or toast
    } finally {
      setButtonLoading((prev) => ({ ...prev, export: false }));
    }
  };

  const handleCampaignImport = async () => {
    if (selectedLeads.length === 0 || !selectedCampaignForImport) return;

    setButtonLoading((prev) => ({ ...prev, campaignImport: true }));
    try {
      await assignLeadsToCampaign(selectedLeads, selectedCampaignForImport);
      setSelectedLeads([]);
      setShowCampaignImport(false);
      setSelectedCampaignForImport("");
      // Show success message or toast
    } catch (error) {
      console.error("Error assigning leads to campaign:", error);
      // Show error message or toast
    } finally {
      setButtonLoading((prev) => ({ ...prev, campaignImport: false }));
    }
  };

  const handleRemoveFromCampaign = async () => {
    if (selectedLeads.length === 0 || !selectedCampaignId) return;

    setButtonLoading((prev) => ({ ...prev, removeFromCampaign: true }));
    try {
      await removeLeadsFromCampaign(selectedLeads, selectedCampaignId);
      setSelectedLeads([]);
      // Show success message or toast
    } catch (error) {
      console.error("Error removing leads from campaign:", error);
      // Show error message or toast
    } finally {
      setButtonLoading((prev) => ({ ...prev, removeFromCampaign: false }));
    }
  };

  const getStatusBadge = (status: string) => {
    const variants: any = {
      sent: "default",
      sending: "secondary",
      delivered: "default",
      bounced: "destructive",
      pending: "secondary",
    };
    return <Badge variant={variants[status] || "secondary"}>{status}</Badge>;
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6 max-w-7xl mx-auto">
          {/* Header with Campaign Switcher and Create Campaign Button */}
          <div className="mb-6 mt-[100px]">
            <div className="flex justify-between items-center mb-4">
              <div>
                <h1 className="text-3xl font-bold">Leads</h1>
                <p className="text-sm text-muted-foreground mt-1">
                  Manage your leads, track activities, and create campaigns
                  {!loading && leads.length > 0 && (
                    <span className="ml-2">• {leads.length} total leads</span>
                  )}
                </p>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={refetch} disabled={loading}>
                  {loading ? (
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  ) : null}
                  Refresh
                </Button>
                <Link href="/leads/modify">
                  <Button className="bg-gray-700 hover:bg-gray-800">
                    Modify Leads
                  </Button>
                </Link>
                <Button
                  className="bg-blue-600 hover:bg-blue-700"
                  onClick={() => setShowCreateCampaign(true)}
                >
                  <Plus className="w-4 h-4 mr-2" />
                  Create Campaign
                </Button>
              </div>
            </div>

            {/* Campaign Switcher */}
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <label className="text-sm font-medium">Campaign:</label>
                <Select
                  value={selectedCampaignId || "all"}
                  onValueChange={handleCampaignChange}
                  disabled={campaignsLoading}
                >
                  <SelectTrigger className="w-64">
                    <SelectValue placeholder="Select campaign..." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Leads</SelectItem>
                    {campaigns.map((campaign) => (
                      <SelectItem key={campaign.id} value={campaign.id}>
                        {campaign.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="text-sm text-muted-foreground">
                Currently viewing:{" "}
                <span className="font-medium">{getCurrentCampaignName()}</span>
              </div>
            </div>
          </div>

          {/* Leads Table Section */}
          <Card className="mb-6 overflow-hidden">
            <CardHeader>
              <div className="flex justify-between items-center">
                <CardTitle>Your Leads</CardTitle>
                <div className="flex gap-2">
                  <div className="relative">
                    <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      placeholder="Search leads..."
                      value={searchTerm}
                      onChange={(e) => setSearchTerm(e.target.value)}
                      className="pl-8 w-64"
                    />
                  </div>
                  <Button
                    variant="outline"
                    onClick={() => setShowFilters(!showFilters)}
                  >
                    <Filter className="w-4 h-4 mr-2" />
                    Filter
                    {getActiveFiltersCount() > 0 && (
                      <Badge variant="secondary" className="ml-2">
                        {getActiveFiltersCount()}
                      </Badge>
                    )}
                  </Button>
                </div>
              </div>
            </CardHeader>

            {/* Filter Panel */}
            {showFilters && (
              <div className="px-6 py-4 border-b bg-gray-50">
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                  {/* Verified Status Filter */}
                  <div>
                    <label className="text-sm font-medium mb-2 block">
                      Verification Status
                    </label>
                    <Select
                      value={filters.verifiedStatus}
                      onValueChange={(value) =>
                        handleFilterChange("verifiedStatus", value)
                      }
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="All statuses" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="all">All Statuses</SelectItem>
                        <SelectItem value="valid">Valid</SelectItem>
                        <SelectItem value="risky">Risky</SelectItem>
                        <SelectItem value="invalid">Invalid</SelectItem>
                        <SelectItem value="unknown">Unknown</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  {/* Position Filter */}
                  <div>
                    <label className="text-sm font-medium mb-2 block">
                      Position
                    </label>
                    <Input
                      placeholder="Filter by position..."
                      value={filters.position}
                      onChange={(e) =>
                        handleFilterChange("position", e.target.value)
                      }
                    />
                  </div>

                  {/* Source Filter */}
                  <div>
                    <label className="text-sm font-medium mb-2 block">
                      Source
                    </label>
                    <Input
                      placeholder="Filter by source..."
                      value={filters.source}
                      onChange={(e) =>
                        handleFilterChange("source", e.target.value)
                      }
                    />
                  </div>

                  {/* Date Range Filter */}
                  <div>
                    <label className="text-sm font-medium mb-2 block">
                      Date Added
                    </label>
                    <Select
                      value={filters.dateRange}
                      onValueChange={(value) =>
                        handleFilterChange("dateRange", value)
                      }
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="All time" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="all">All Time</SelectItem>
                        <SelectItem value="today">Today</SelectItem>
                        <SelectItem value="week">This Week</SelectItem>
                        <SelectItem value="month">This Month</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                {/* Contact Information Filters */}
                <div className="mt-4">
                  <label className="text-sm font-medium mb-2 block">
                    Contact Information
                  </label>
                  <div className="flex flex-wrap gap-4">
                    <div className="flex items-center space-x-2">
                      <Checkbox
                        id="hasEmail"
                        checked={filters.hasEmail}
                        onCheckedChange={(checked) =>
                          handleFilterChange("hasEmail", checked as boolean)
                        }
                      />
                      <label htmlFor="hasEmail" className="text-sm">
                        Has Email
                      </label>
                    </div>
                    <div className="flex items-center space-x-2">
                      <Checkbox
                        id="hasPhone"
                        checked={filters.hasPhone}
                        onCheckedChange={(checked) =>
                          handleFilterChange("hasPhone", checked as boolean)
                        }
                      />
                      <label htmlFor="hasPhone" className="text-sm">
                        Has Phone
                      </label>
                    </div>
                    <div className="flex items-center space-x-2">
                      <Checkbox
                        id="hasLinkedIn"
                        checked={filters.hasLinkedIn}
                        onCheckedChange={(checked) =>
                          handleFilterChange("hasLinkedIn", checked as boolean)
                        }
                      />
                      <label htmlFor="hasLinkedIn" className="text-sm">
                        Has LinkedIn
                      </label>
                    </div>
                  </div>
                </div>

                {/* Filter Actions */}
                <div className="flex justify-between items-center mt-4">
                  <div className="text-sm text-muted-foreground">
                    {getActiveFiltersCount() > 0 && (
                      <span>{getActiveFiltersCount()} filter(s) applied</span>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={resetFilters}>
                      Clear All
                    </Button>
                    <Button size="sm" onClick={() => setShowFilters(false)}>
                      Apply Filters
                    </Button>
                  </div>
                </div>
              </div>
            )}

            <CardContent>
              {/* Active Filter Chips */}
              {getActiveFiltersCount() > 0 && (
                <div className="mb-4">
                  <div className="flex flex-wrap gap-2">
                    {filters.verifiedStatus !== "all" && (
                      <Badge
                        variant="secondary"
                        className="flex items-center gap-1"
                      >
                        Status: {filters.verifiedStatus}
                        <button
                          onClick={() =>
                            handleFilterChange("verifiedStatus", "all")
                          }
                          className="ml-1 hover:bg-gray-300 rounded-full p-0.5"
                        >
                          ×
                        </button>
                      </Badge>
                    )}
                    {filters.position && (
                      <Badge
                        variant="secondary"
                        className="flex items-center gap-1"
                      >
                        Position: {filters.position}
                        <button
                          onClick={() => handleFilterChange("position", "")}
                          className="ml-1 hover:bg-gray-300 rounded-full p-0.5"
                        >
                          ×
                        </button>
                      </Badge>
                    )}
                    {filters.source && (
                      <Badge
                        variant="secondary"
                        className="flex items-center gap-1"
                      >
                        Source: {filters.source}
                        <button
                          onClick={() => handleFilterChange("source", "")}
                          className="ml-1 hover:bg-gray-300 rounded-full p-0.5"
                        >
                          ×
                        </button>
                      </Badge>
                    )}
                    {filters.dateRange !== "all" && (
                      <Badge
                        variant="secondary"
                        className="flex items-center gap-1"
                      >
                        Date: {filters.dateRange}
                        <button
                          onClick={() => handleFilterChange("dateRange", "all")}
                          className="ml-1 hover:bg-gray-300 rounded-full p-0.5"
                        >
                          ×
                        </button>
                      </Badge>
                    )}
                    {filters.hasEmail && (
                      <Badge
                        variant="secondary"
                        className="flex items-center gap-1"
                      >
                        Has Email
                        <button
                          onClick={() => handleFilterChange("hasEmail", false)}
                          className="ml-1 hover:bg-gray-300 rounded-full p-0.5"
                        >
                          ×
                        </button>
                      </Badge>
                    )}
                    {filters.hasPhone && (
                      <Badge
                        variant="secondary"
                        className="flex items-center gap-1"
                      >
                        Has Phone
                        <button
                          onClick={() => handleFilterChange("hasPhone", false)}
                          className="ml-1 hover:bg-gray-300 rounded-full p-0.5"
                        >
                          ×
                        </button>
                      </Badge>
                    )}
                    {filters.hasLinkedIn && (
                      <Badge
                        variant="secondary"
                        className="flex items-center gap-1"
                      >
                        Has LinkedIn
                        <button
                          onClick={() =>
                            handleFilterChange("hasLinkedIn", false)
                          }
                          className="ml-1 hover:bg-gray-300 rounded-full p-0.5"
                        >
                          ×
                        </button>
                      </Badge>
                    )}
                  </div>
                </div>
              )}

              <div className="flex gap-2 mb-4">
                <Button
                  variant={selectedLeads.length > 1 ? "default" : "outline"}
                  disabled={selectedLeads.length === 0 || buttonLoading.enrich}
                  onClick={handleEnrich}
                >
                  {buttonLoading.enrich ? (
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  ) : null}
                  {selectedLeads.length > 1 ? "Bulk " : ""}Enrich and Verify
                </Button>
                <Button
                  variant={selectedLeads.length > 1 ? "default" : "outline"}
                  disabled={selectedLeads.length === 0 || buttonLoading.export}
                  onClick={handleExport}
                >
                  {buttonLoading.export ? (
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  ) : null}
                  {selectedLeads.length > 1 ? "Bulk " : ""}Export
                </Button>
                <Button
                  variant={selectedLeads.length > 1 ? "default" : "outline"}
                  disabled={selectedLeads.length === 0}
                  onClick={() => setShowCampaignImport(true)}
                >
                  {selectedLeads.length > 1 ? "Bulk " : ""}Campaign Lead Import
                </Button>
                {/* Only show Remove from Campaign button when viewing a specific campaign */}
                {selectedCampaignId && (
                  <Button
                    variant={
                      selectedLeads.length > 1 ? "destructive" : "outline"
                    }
                    disabled={
                      selectedLeads.length === 0 ||
                      buttonLoading.removeFromCampaign
                    }
                    onClick={handleRemoveFromCampaign}
                  >
                    {buttonLoading.removeFromCampaign ? (
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    ) : null}
                    {selectedLeads.length > 1 ? "Bulk " : ""}Remove from
                    Campaign
                  </Button>
                )}
              </div>

              {loading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="w-6 h-6 animate-spin mr-2" />
                  <span>Loading leads...</span>
                </div>
              ) : error ? (
                <div className="flex items-center justify-center py-8">
                  <div className="text-center">
                    <p className="text-red-600 mb-2">
                      Error loading leads: {error}
                    </p>
                    <Button onClick={refetch} variant="outline">
                      Try Again
                    </Button>
                  </div>
                </div>
              ) : filteredLeads.length === 0 ? (
                <div className="flex items-center justify-center py-8">
                  <div className="text-center">
                    <Users className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                    <p className="text-gray-600 mb-2">
                      {searchTerm || getActiveFiltersCount() > 0
                        ? "No leads match your search or filters"
                        : "No leads found"}
                    </p>
                    {searchTerm || getActiveFiltersCount() > 0 ? (
                      <div className="flex gap-2 justify-center">
                        <Button onClick={resetFilters} variant="outline">
                          Clear All Filters
                        </Button>
                        {!searchTerm && (
                          <Button
                            onClick={() => (window.location.href = "/import")}
                            variant="outline"
                          >
                            Import Leads
                          </Button>
                        )}
                      </div>
                    ) : (
                      <Button
                        onClick={() => (window.location.href = "/import")}
                        variant="outline"
                      >
                        Import Leads
                      </Button>
                    )}
                  </div>
                </div>
              ) : (
                <>
                  {/* Horizontal scrollable table container */}
                  <div className="overflow-x-auto overflow-y-visible">
                    <Table className="min-w-full">
                      <TableHeader>
                        <TableRow>
                          <TableHead className="w-12 sticky left-0 bg-white z-10">
                            <Checkbox
                              checked={
                                selectedLeads.length === filteredLeads.length &&
                                filteredLeads.length > 0
                              }
                              onCheckedChange={handleSelectAll}
                            />
                          </TableHead>
                          <TableHead className="min-w-[120px]">
                            First Name
                          </TableHead>
                          <TableHead className="min-w-[120px]">
                            Last Name
                          </TableHead>
                          <TableHead className="min-w-[150px]">
                            Position
                          </TableHead>
                          <TableHead className="min-w-[150px]">
                            Department
                          </TableHead>
                          <TableHead className="min-w-[200px]">Email</TableHead>
                          <TableHead className="min-w-[100px]">
                            Status
                          </TableHead>
                          {/* New columns between Status and Custom Field */}
                          <TableHead className="min-w-[120px]">Phone</TableHead>
                          <TableHead className="min-w-[150px]">
                            Domain
                          </TableHead>
                          <TableHead className="min-w-[200px]">
                            LinkedIn URL
                          </TableHead>
                          <TableHead className="min-w-[120px]">
                            Source
                          </TableHead>
                          <TableHead className="min-w-[150px]">
                            Custom Field
                          </TableHead>
                          <TableHead className="min-w-[100px]">
                            Created At
                          </TableHead>
                          <TableHead className="min-w-[120px] sticky right-0 bg-white z-10">
                            Actions
                          </TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {filteredLeads.map((lead) => (
                          <TableRow
                            key={lead.id}
                            className="cursor-pointer hover:bg-gray-50"
                          >
                            <TableCell className="sticky left-0 bg-white z-10">
                              <Checkbox
                                checked={selectedLeads.includes(lead.id)}
                                onCheckedChange={(checked) =>
                                  handleSelectLead(lead.id, checked as boolean)
                                }
                              />
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.firstName || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.lastName || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.position || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.department || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.email || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
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
                            {/* New columns between Status and Custom Field */}
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.phone || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.domain || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.linkedinUrl ? (
                                <a
                                  href={lead.linkedinUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="text-blue-600 hover:underline truncate block max-w-[180px]"
                                  onClick={(e) => e.stopPropagation()}
                                >
                                  {lead.linkedinUrl}
                                </a>
                              ) : (
                                "-"
                              )}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.source || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.customTextField || "-"}
                            </TableCell>
                            <TableCell onClick={() => handleLeadClick(lead)}>
                              {lead.createdAt
                                ? new Date(lead.createdAt).toLocaleDateString()
                                : "-"}
                            </TableCell>
                            <TableCell className="sticky right-0 bg-white z-10">
                              <div className="flex gap-1">
                                <Button size="sm" variant="ghost">
                                  <Eye className="w-4 h-4" />
                                </Button>
                                <Button size="sm" variant="ghost">
                                  <Mail className="w-4 h-4" />
                                </Button>
                              </div>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>

                  {/* Pagination Placeholder */}
                  <div className="flex justify-between items-center mt-4">
                    <div className="text-sm text-muted-foreground">
                      Showing {filteredLeads.length} of {leads.length} leads
                      {getActiveFiltersCount() > 0 && (
                        <span className="ml-2 text-blue-600">(filtered)</span>
                      )}
                    </div>
                    <div className="flex gap-2">
                      <Button variant="outline" disabled>
                        Previous
                      </Button>
                      <Button variant="outline" disabled>
                        Next
                      </Button>
                    </div>
                  </div>
                </>
              )}
            </CardContent>
          </Card>

          <hr className="my-8" />

          {/* Activity Tracker Section */}
          <Card className="mb-6">
            <CardHeader>
              <CardTitle>Activity Tracker</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Email Address</TableHead>
                    <TableHead>Operation Status</TableHead>
                    <TableHead>Email Status</TableHead>
                    <TableHead>Timestamp</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {mockActivities.map((activity) => (
                    <TableRow key={activity.id}>
                      <TableCell>{activity.email}</TableCell>
                      <TableCell>
                        {getStatusBadge(activity.operationStatus)}
                      </TableCell>
                      <TableCell>
                        {getStatusBadge(activity.emailStatus)}
                      </TableCell>
                      <TableCell>{activity.timestamp}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <hr className="my-8" />

          {/* Templates Section */}
          <Card className="mb-6">
            <CardHeader>
              <div className="flex justify-between items-center">
                <CardTitle>Recent Templates</CardTitle>
                <Button
                  variant="outline"
                  onClick={() => setShowAllTemplates(true)}
                >
                  View All Templates
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {templatesLoading ? (
                <div className="text-sm text-muted-foreground">
                  Loading templates...
                </div>
              ) : templatesError ? (
                <div className="text-sm text-red-600">{templatesError}</div>
              ) : recentTemplates.length === 0 ? (
                <div className="text-sm text-muted-foreground">
                  No templates yet. Create one in Templates.
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {recentTemplates.map((t) => {
                    const content = parseContent<any>(t.contentJson);
                    return (
                      <div
                        key={t.id}
                        className="p-4 border rounded-lg cursor-pointer hover:bg-gray-50"
                        onClick={() => handleTemplatePreview(t)}
                      >
                        <h3 className="font-medium">{t.name}</h3>
                        {t.platform === "EMAIL" && content?.subject && (
                          <p className="text-xs text-gray-500 mt-1">
                            {content.subject}
                          </p>
                        )}
                        <p className="text-sm text-muted-foreground mt-1 line-clamp-3">
                          {content?.body || ""}
                        </p>
                        <Button size="sm" variant="ghost" className="mt-2">
                          <Eye className="w-4 h-4 mr-1" />
                          Preview
                        </Button>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          <hr className="my-8" />

          {/* Health & Metrics Section */}
          <Card>
            <CardHeader>
              <CardTitle>Health & Metrics</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="text-center p-4 border rounded-lg">
                  <div className="text-2xl font-bold text-blue-600">1,234</div>
                  <div className="text-sm text-muted-foreground">
                    Total Leads
                  </div>
                  {/* Placeholder graph */}
                  <div className="mt-2 h-8 bg-gray-100 rounded flex items-end justify-center">
                    <div className="w-2 h-6 bg-blue-600 rounded"></div>
                    <div className="w-2 h-4 bg-blue-600 rounded ml-1"></div>
                    <div className="w-2 h-5 bg-blue-600 rounded ml-1"></div>
                  </div>
                </div>
                <div className="text-center p-4 border rounded-lg">
                  <div className="text-2xl font-bold text-green-600">85%</div>
                  <div className="text-sm text-muted-foreground">
                    Verified %
                  </div>
                  {/* Placeholder graph */}
                  <div className="mt-2 h-8 bg-gray-100 rounded flex items-end justify-center">
                    <div className="w-2 h-7 bg-green-600 rounded"></div>
                    <div className="w-2 h-5 bg-green-600 rounded ml-1"></div>
                    <div className="w-2 h-6 bg-green-600 rounded ml-1"></div>
                  </div>
                </div>
                <div className="text-center p-4 border rounded-lg">
                  <div className="text-2xl font-bold text-red-600">23</div>
                  <div className="text-sm text-muted-foreground">
                    Suppressed Count
                  </div>
                  {/* Placeholder graph */}
                  <div className="mt-2 h-8 bg-gray-100 rounded flex items-end justify-center">
                    <div className="w-2 h-3 bg-red-600 rounded"></div>
                    <div className="w-2 h-2 bg-red-600 rounded ml-1"></div>
                    <div className="w-2 h-4 bg-red-600 rounded ml-1"></div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Lead Detail Modal */}
        <Dialog open={showLeadModal} onOpenChange={setShowLeadModal}>
          <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>
                Lead Details - {selectedLead?.firstName || "Unknown"}{" "}
                {selectedLead?.lastName || ""}
              </DialogTitle>
            </DialogHeader>
            <Tabs defaultValue="details" className="w-full">
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="details">Details</TabsTrigger>
                <TabsTrigger value="verify">Verify</TabsTrigger>
                <TabsTrigger value="history">History</TabsTrigger>
              </TabsList>
              <TabsContent value="details" className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-sm font-medium">First Name</label>
                    <Input value={selectedLead?.firstName || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Last Name</label>
                    <Input value={selectedLead?.lastName || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Position</label>
                    <Input value={selectedLead?.position || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Position Raw</label>
                    <Input value={selectedLead?.positionRaw || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Seniority</label>
                    <Input value={selectedLead?.seniority || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Department</label>
                    <Input value={selectedLead?.department || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Email</label>
                    <Input value={selectedLead?.email || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Phone</label>
                    <Input value={selectedLead?.phone || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Domain</label>
                    <Input value={selectedLead?.domain || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">LinkedIn URL</label>
                    <Input value={selectedLead?.linkedinUrl || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Twitter</label>
                    <Input value={selectedLead?.twitter || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">
                      Confidence Score
                    </label>
                    <Input
                      value={selectedLead?.confidenceScore?.toString() || ""}
                      readOnly
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Email Type</label>
                    <Input value={selectedLead?.emailType || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Source</label>
                    <Input value={selectedLead?.source || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Custom Field</label>
                    <Input
                      value={selectedLead?.customTextField || ""}
                      readOnly
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">
                      Verified Status
                    </label>
                    <div className="flex items-center gap-2">
                      <Badge
                        variant={
                          selectedLead?.verifiedStatus === "valid"
                            ? "default"
                            : selectedLead?.verifiedStatus === "risky"
                              ? "secondary"
                              : selectedLead?.verifiedStatus === "invalid"
                                ? "destructive"
                                : "outline"
                        }
                      >
                        {selectedLead?.verifiedStatus || "unknown"}
                      </Badge>
                    </div>
                  </div>
                  <div>
                    <label className="text-sm font-medium">Created At</label>
                    <Input
                      value={
                        selectedLead?.createdAt
                          ? new Date(selectedLead.createdAt).toLocaleString()
                          : ""
                      }
                      readOnly
                    />
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    onClick={() => setShowLeadModal(false)}
                  >
                    Close
                  </Button>
                </div>
              </TabsContent>
              <TabsContent value="verify">
                <div className="space-y-4">
                  <p className="text-muted-foreground">
                    Verification options will go here
                  </p>
                  <Button>Verify Email</Button>
                </div>
              </TabsContent>
              <TabsContent value="history">
                <div className="space-y-4">
                  <p className="text-muted-foreground">
                    Enrichment history will be displayed here
                  </p>
                </div>
              </TabsContent>
            </Tabs>
          </DialogContent>
        </Dialog>

        {/* All Templates Modal - Reuse shared browser */}
        <TemplateBrowserModal
          open={showAllTemplates}
          onOpenChange={setShowAllTemplates}
          platform="ALL"
          onSelect={(t: TemplateModel) => setSelectedTemplate(t)}
          onUse={(t: TemplateModel) => handleTemplatePreview(t)}
        />

        {/* Template Preview Modal */}
        <Dialog
          open={showTemplatePreview}
          onOpenChange={setShowTemplatePreview}
        >
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <DialogTitle>
                Template Preview - {selectedTemplate?.name}
              </DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              {selectedTemplate && (
                <div className="space-y-2">
                  {selectedTemplate.platform === "EMAIL" && (
                    <div>
                      <p className="text-xs text-gray-500">Subject</p>
                      <div className="p-3 bg-gray-50 rounded text-sm">
                        {parseContent<any>(selectedTemplate.contentJson)
                          ?.subject || ""}
                      </div>
                    </div>
                  )}
                  <div>
                    <p className="text-xs text-gray-500">Content</p>
                    <div className="p-3 bg-gray-50 rounded text-sm whitespace-pre-wrap">
                      {parseContent<any>(selectedTemplate.contentJson)?.body ||
                        ""}
                    </div>
                  </div>
                </div>
              )}
              <div className="flex gap-2">
                <Button>Use Template</Button>
                <Button variant="outline">Close</Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>

        {/* Campaign Lead Import Modal */}
        <Dialog open={showCampaignImport} onOpenChange={setShowCampaignImport}>
          <DialogContent className="max-w-md">
            <DialogHeader>
              <DialogTitle>Import Leads to Campaign</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium mb-2 block">
                  Select Campaign
                </label>
                <Select
                  value={selectedCampaignForImport}
                  onValueChange={setSelectedCampaignForImport}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Choose a campaign..." />
                  </SelectTrigger>
                  <SelectContent>
                    {campaigns.map((campaign) => (
                      <SelectItem key={campaign.id} value={campaign.id}>
                        {campaign.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="text-sm text-muted-foreground">
                {selectedLeads.length} lead(s) will be assigned to the selected
                campaign.
              </div>
              <div className="flex gap-2 justify-end">
                <Button
                  variant="outline"
                  onClick={() => setShowCampaignImport(false)}
                  disabled={buttonLoading.campaignImport}
                >
                  Cancel
                </Button>
                <Button
                  onClick={handleCampaignImport}
                  disabled={
                    !selectedCampaignForImport || buttonLoading.campaignImport
                  }
                >
                  {buttonLoading.campaignImport ? (
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  ) : null}
                  Import to Campaign
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>

        {/* Create Campaign Modal */}
        <Dialog open={showCreateCampaign} onOpenChange={setShowCreateCampaign}>
          <DialogContent className="max-w-md">
            <DialogHeader>
              <DialogTitle>Create New Campaign</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium mb-2 block">
                  Campaign Name *
                </label>
                <Input
                  placeholder="Enter campaign name..."
                  value={campaignForm.name}
                  onChange={(e) =>
                    setCampaignForm((prev) => ({
                      ...prev,
                      name: e.target.value,
                    }))
                  }
                />
              </div>
              <div>
                <label className="text-sm font-medium mb-2 block">
                  Description
                </label>
                <Input
                  placeholder="Enter campaign description..."
                  value={campaignForm.description}
                  onChange={(e) =>
                    setCampaignForm((prev) => ({
                      ...prev,
                      description: e.target.value,
                    }))
                  }
                />
              </div>
              <div className="flex gap-2 justify-end">
                <Button
                  variant="outline"
                  onClick={() => setShowCreateCampaign(false)}
                >
                  Cancel
                </Button>
                <Button
                  onClick={handleCreateCampaign}
                  disabled={!campaignForm.name.trim()}
                >
                  Create Campaign
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      </DashboardLayout>
    </AuthGuard>
  );
}
