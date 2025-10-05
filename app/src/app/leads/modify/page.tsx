"use client";

import { useEffect, useMemo, useState } from "react";
import AuthGuard from "@/components/AuthGuard";
import DashboardLayout from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Search, Loader2 } from "lucide-react";
import { useLeads, Lead } from "@/hooks/useLeads";
import Link from "next/link";

export default function ModifyLeadsPage() {
  const { leads, loading, error, refetch, createLead, updateLead, deleteLead } =
    useLeads(undefined);

  // Create form state
  const [createForm, setCreateForm] = useState({
    firstName: "",
    lastName: "",
    email: "",
    position: "",
    positionRaw: "",
    seniority: "",
    department: "",
    phone: "",
    domain: "",
    linkedinUrl: "",
    twitter: "",
    customTextField: "",
    emailType: "unknown" as Lead["emailType"],
  });
  const [createError, setCreateError] = useState<string | null>(null);
  const [createLoading, setCreateLoading] = useState(false);

  const [searchTerm, setSearchTerm] = useState("");
  const filteredLeads = useMemo(() => {
    if (!searchTerm) return leads;
    const term = searchTerm.toLowerCase();
    return leads.filter(
      (l) =>
        l.firstName?.toLowerCase().includes(term) ||
        l.lastName?.toLowerCase().includes(term) ||
        l.email?.toLowerCase().includes(term) ||
        l.position?.toLowerCase().includes(term) ||
        l.department?.toLowerCase().includes(term)
    );
  }, [leads, searchTerm]);

  // Edit modal state
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editLoading, setEditLoading] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<Partial<Lead>>({});

  useEffect(() => {
    if (selectedLead) {
      setEditForm({
        firstName: selectedLead.firstName || "",
        lastName: selectedLead.lastName || "",
        position: selectedLead.position || "",
        positionRaw: selectedLead.positionRaw || "",
        seniority: selectedLead.seniority || "",
        department: selectedLead.department || "",
        phone: selectedLead.phone || "",
        domain: selectedLead.domain || "",
        linkedinUrl: selectedLead.linkedinUrl || "",
        twitter: selectedLead.twitter || "",
        customTextField: selectedLead.customTextField || "",
        emailType: selectedLead.emailType || "unknown",
      });
    }
  }, [selectedLead]);

  const handleCreate = async () => {
    setCreateError(null);
    if (!createForm.email.trim() || !createForm.firstName.trim()) {
      setCreateError("Email and first name are required");
      return;
    }
    setCreateLoading(true);
    try {
      await createLead({
        email: createForm.email.trim(),
        firstName: createForm.firstName.trim(),
        lastName: createForm.lastName || undefined,
        position: createForm.position || undefined,
        positionRaw: createForm.positionRaw || undefined,
        seniority: createForm.seniority || undefined,
        department: createForm.department || undefined,
        phone: createForm.phone || undefined,
        domain: createForm.domain || undefined,
        linkedinUrl: createForm.linkedinUrl || undefined,
        twitter: createForm.twitter || undefined,
        customTextField: createForm.customTextField || undefined,
        emailType: createForm.emailType,
      });
      setCreateForm({
        firstName: "",
        lastName: "",
        email: "",
        position: "",
        positionRaw: "",
        seniority: "",
        department: "",
        phone: "",
        domain: "",
        linkedinUrl: "",
        twitter: "",
        customTextField: "",
        emailType: "unknown",
      });
    } catch (e: any) {
      setCreateError(e?.message || "Failed to create lead");
    } finally {
      setCreateLoading(false);
    }
  };

  const handleSaveEdit = async () => {
    if (!selectedLead) return;
    setEditLoading(true);
    setEditError(null);
    try {
      await updateLead(selectedLead.id, {
        firstName: editForm.firstName || "",
        lastName: editForm.lastName || "",
        position: editForm.position || "",
        positionRaw: editForm.positionRaw || "",
        seniority: editForm.seniority || "",
        department: editForm.department || "",
        phone: editForm.phone || "",
        domain: editForm.domain || "",
        linkedinUrl: editForm.linkedinUrl || "",
        twitter: editForm.twitter || "",
        customTextField: editForm.customTextField || "",
        emailType: editForm.emailType || "unknown",
      });
      setEditOpen(false);
    } catch (e: any) {
      setEditError(e?.message || "Failed to save changes");
    } finally {
      setEditLoading(false);
    }
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6 max-w-7xl mx-auto">
          <div className="mb-6 mt-[100px] flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold">Modify Leads</h1>
              <p className="text-sm text-muted-foreground mt-1">
                Create and edit your leads. This view always shows all leads.
              </p>
            </div>
            <Link href="/leads">
              <Button variant="outline">Back to Leads</Button>
            </Link>
          </div>

          {/* Create Lead */}
          <Card className="mb-6">
            <CardHeader>
              <CardTitle>Create Lead</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="text-sm font-medium">Email *</label>
                  <Input
                    placeholder="name@company.com"
                    value={createForm.email}
                    onChange={(e) =>
                      setCreateForm((p) => ({ ...p, email: e.target.value }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">First Name *</label>
                  <Input
                    value={createForm.firstName}
                    onChange={(e) =>
                      setCreateForm((p) => ({
                        ...p,
                        firstName: e.target.value,
                      }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Last Name</label>
                  <Input
                    value={createForm.lastName}
                    onChange={(e) =>
                      setCreateForm((p) => ({ ...p, lastName: e.target.value }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Position</label>
                  <Input
                    value={createForm.position}
                    placeholder="e.g., Head of Growth, Sales Manager"
                    onChange={(e) =>
                      setCreateForm((p) => ({ ...p, position: e.target.value }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Position Raw</label>
                  <Input
                    value={createForm.positionRaw}
                    onChange={(e) =>
                      setCreateForm((p) => ({
                        ...p,
                        positionRaw: e.target.value,
                      }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Seniority</label>
                  <Input
                    value={createForm.seniority}
                    placeholder="e.g., Director, VP, Individual Contributor"
                    onChange={(e) =>
                      setCreateForm((p) => ({
                        ...p,
                        seniority: e.target.value,
                      }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Department</label>
                  <Input
                    value={createForm.department}
                    placeholder="e.g., Marketing, Sales, Engineering"
                    onChange={(e) =>
                      setCreateForm((p) => ({
                        ...p,
                        department: e.target.value,
                      }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Phone</label>
                  <Input
                    value={createForm.phone}
                    onChange={(e) =>
                      setCreateForm((p) => ({ ...p, phone: e.target.value }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Domain</label>
                  <Input
                    value={createForm.domain}
                    onChange={(e) =>
                      setCreateForm((p) => ({ ...p, domain: e.target.value }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">LinkedIn URL</label>
                  <Input
                    value={createForm.linkedinUrl}
                    onChange={(e) =>
                      setCreateForm((p) => ({
                        ...p,
                        linkedinUrl: e.target.value,
                      }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Twitter</label>
                  <Input
                    value={createForm.twitter}
                    onChange={(e) =>
                      setCreateForm((p) => ({ ...p, twitter: e.target.value }))
                    }
                  />
                </div>
                <div>
                  <label className="text-sm font-medium">Custom Field</label>
                  <Input
                    value={createForm.customTextField}
                    onChange={(e) =>
                      setCreateForm((p) => ({
                        ...p,
                        customTextField: e.target.value,
                      }))
                    }
                  />
                </div>
              </div>
              {createError && (
                <div className="text-sm text-red-600 mt-3">{createError}</div>
              )}
              <div className="flex gap-2 mt-4">
                <Button onClick={handleCreate} disabled={createLoading}>
                  {createLoading ? (
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  ) : null}
                  Create Lead
                </Button>
                <Button
                  variant="outline"
                  onClick={() =>
                    setCreateForm({
                      firstName: "",
                      lastName: "",
                      email: "",
                      position: "",
                      positionRaw: "",
                      seniority: "",
                      department: "",
                      phone: "",
                      domain: "",
                      linkedinUrl: "",
                      twitter: "",
                      customTextField: "",
                      emailType: "unknown",
                    })
                  }
                >
                  Reset
                </Button>
              </div>
              <div className="text-xs text-muted-foreground mt-2">
                Email is immutable. Double check since you can't change it later
                unless you create a new lead.
              </div>
            </CardContent>
          </Card>

          {/* Leads Table */}
          <Card>
            <CardHeader>
              <div className="flex justify-between items-center">
                <CardTitle>All Leads</CardTitle>
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
                    onClick={refetch}
                    disabled={loading}
                  >
                    {loading ? (
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    ) : null}
                    Refresh
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent>
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
                <div className="flex items-center justify-center py-8 text-muted-foreground">
                  No leads found
                </div>
              ) : (
                <div className="overflow-x-auto overflow-y-visible">
                  <Table className="min-w-full">
                    <TableHeader>
                      <TableRow>
                        <TableHead className="min-w-[120px]">
                          First Name
                        </TableHead>
                        <TableHead className="min-w-[120px]">
                          Last Name
                        </TableHead>
                        <TableHead className="min-w-[200px]">Email</TableHead>
                        <TableHead className="min-w-[150px]">
                          Position
                        </TableHead>
                        <TableHead className="min-w-[150px]">
                          Department
                        </TableHead>
                        <TableHead className="min-w-[150px]">Phone</TableHead>
                        <TableHead className="min-w-[150px]">Domain</TableHead>
                        <TableHead className="min-w-[150px]">
                          LinkedIn
                        </TableHead>
                        <TableHead className="min-w-[100px]">Status</TableHead>
                        <TableHead className="min-w-[120px]">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredLeads.map((lead) => (
                        <TableRow
                          key={lead.id}
                          className="cursor-pointer hover:bg-gray-50"
                        >
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
                            {lead.firstName || "-"}
                          </TableCell>
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
                            {lead.lastName || "-"}
                          </TableCell>
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
                            {lead.email || "-"}
                          </TableCell>
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
                            {lead.position || "-"}
                          </TableCell>
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
                            {lead.department || "-"}
                          </TableCell>
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
                            {lead.phone || "-"}
                          </TableCell>
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
                            {lead.domain || "-"}
                          </TableCell>
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
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
                          <TableCell
                            onClick={() => {
                              setSelectedLead(lead);
                              setEditOpen(true);
                            }}
                          >
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
                          <TableCell>
                            <div className="flex gap-2">
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                  setSelectedLead(lead);
                                  setEditOpen(true);
                                }}
                              >
                                Edit
                              </Button>
                              <Button
                                size="sm"
                                variant="destructive"
                                onClick={async () => {
                                  try {
                                    await deleteLead(lead.id);
                                  } catch (e) {
                                    /* no toast */
                                  }
                                }}
                              >
                                Delete
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Edit Modal - visually similar to details modal */}
          <Dialog open={editOpen} onOpenChange={setEditOpen}>
            <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>
                  Lead Details - {selectedLead?.firstName || "Unknown"}{" "}
                  {selectedLead?.lastName || ""}
                </DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-sm font-medium">First Name</label>
                    <Input
                      value={(editForm.firstName as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({
                          ...p,
                          firstName: e.target.value,
                        }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Last Name</label>
                    <Input
                      value={(editForm.lastName as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({ ...p, lastName: e.target.value }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Position</label>
                    <Input
                      value={(editForm.position as string) || ""}
                      placeholder="e.g., Head of Growth, Sales Manager"
                      onChange={(e) =>
                        setEditForm((p) => ({ ...p, position: e.target.value }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Position Raw</label>
                    <Input
                      value={(editForm.positionRaw as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({
                          ...p,
                          positionRaw: e.target.value,
                        }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Seniority</label>
                    <Input
                      value={(editForm.seniority as string) || ""}
                      placeholder="e.g., Director, VP, Individual Contributor"
                      onChange={(e) =>
                        setEditForm((p) => ({
                          ...p,
                          seniority: e.target.value,
                        }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Department</label>
                    <Input
                      value={(editForm.department as string) || ""}
                      placeholder="e.g., Marketing, Sales, Engineering"
                      onChange={(e) =>
                        setEditForm((p) => ({
                          ...p,
                          department: e.target.value,
                        }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Email</label>
                    <Input value={selectedLead?.email || ""} readOnly />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Phone</label>
                    <Input
                      value={(editForm.phone as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({ ...p, phone: e.target.value }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Domain</label>
                    <Input
                      value={(editForm.domain as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({ ...p, domain: e.target.value }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">LinkedIn URL</label>
                    <Input
                      value={(editForm.linkedinUrl as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({
                          ...p,
                          linkedinUrl: e.target.value,
                        }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Twitter</label>
                    <Input
                      value={(editForm.twitter as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({ ...p, twitter: e.target.value }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Email Type</label>
                    <Input
                      value={(editForm.emailType as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({
                          ...p,
                          emailType: e.target.value as any,
                        }))
                      }
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Custom Field</label>
                    <Input
                      value={(editForm.customTextField as string) || ""}
                      onChange={(e) =>
                        setEditForm((p) => ({
                          ...p,
                          customTextField: e.target.value,
                        }))
                      }
                    />
                  </div>
                </div>
                {editError && (
                  <div className="text-sm text-red-600">{editError}</div>
                )}
                <div className="flex gap-2">
                  <Button onClick={handleSaveEdit} disabled={editLoading}>
                    {editLoading ? (
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    ) : null}
                    Save
                  </Button>
                  <Button variant="outline" onClick={() => setEditOpen(false)}>
                    Cancel
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
