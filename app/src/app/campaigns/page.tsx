"use client";

import { useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  ArrowUpDown,
  Plus,
  Pencil,
  Trash2,
  Filter,
  Search,
} from "lucide-react";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { useCampaigns, Campaign as CampaignModel } from "@/hooks/useCampaigns";

type CampaignStatus = CampaignModel["status"];

function StatusBadge({ status }: { status: CampaignStatus }) {
  const style = {
    active: "bg-green-100 text-green-800",
    paused: "bg-yellow-100 text-yellow-800",
    completed: "bg-blue-100 text-blue-800",
    inactive: "bg-gray-100 text-gray-800",
  }[status];
  return <Badge className={style}>{status}</Badge>;
}

export default function CampaignsPage() {
  const {
    campaigns,
    loading,
    error,
    createCampaign,
    updateCampaign,
    deleteCampaign,
  } = useCampaigns();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | CampaignStatus>(
    "all"
  );
  const [sortBy, setSortBy] = useState<"name" | "createdAt">("createdAt");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);

  const [isCreating, setIsCreating] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const [draft, setDraft] = useState<
    Pick<CampaignModel, "name" | "description" | "status">
  >({
    name: "",
    description: "",
    status: "active",
  });
  const [editingId, setEditingId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    return (campaigns || [])
      .filter((c) =>
        [c.name, c.description || ""]
          .join(" ")
          .toLowerCase()
          .includes(search.toLowerCase())
      )
      .filter((c) =>
        statusFilter === "all" ? true : c.status === statusFilter
      )
      .sort((a, b) => {
        const dir = sortDir === "asc" ? 1 : -1;
        if (sortBy === "name") return a.name.localeCompare(b.name) * dir;
        return (
          (new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()) *
          dir
        );
      });
  }, [campaigns, search, statusFilter, sortBy, sortDir]);

  const campaignToDelete = useMemo(
    () => (campaigns || []).find((c) => c.id === editingId) || null,
    [campaigns, editingId]
  );

  const openCreate = () => {
    setDraft({ name: "", description: "", status: "active" });
    setIsCreateOpen(true);
  };

  const openEdit = (id: string) => {
    const existing = (campaigns || []).find((c) => c.id === id);
    if (!existing) return;
    setEditingId(id);
    setDraft({
      name: existing.name,
      description: existing.description,
      status: existing.status,
    });
    setIsEditOpen(true);
  };

  const openDelete = (id: string) => {
    setEditingId(id);
    setIsDeleteOpen(true);
  };

  const toggleSort = (key: "name" | "createdAt") => {
    if (sortBy === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortBy(key);
      setSortDir("asc");
    }
  };

  // Real create/edit/delete using hook
  const handleCreate = async () => {
    try {
      setIsCreating(true);
      const name = draft.name.trim() || "Untitled Campaign";
      const description = draft.description?.trim() || undefined;
      await createCampaign(name, description);
      setIsCreateOpen(false);
    } finally {
      setIsCreating(false);
    }
  };

  const handleEdit = async () => {
    if (!editingId) return;
    try {
      setIsSaving(true);
      const updates: Partial<CampaignModel> = {
        status: draft.status,
      };
      const trimmedName = draft.name.trim();
      if (trimmedName) updates.name = trimmedName;
      const trimmedDesc = draft.description?.trim();
      if (trimmedDesc) updates.description = trimmedDesc;
      await updateCampaign(editingId, updates);
      setIsEditOpen(false);
      setEditingId(null);
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!editingId) return;
    try {
      setIsDeleting(true);
      await deleteCampaign(editingId);
      setIsDeleteOpen(false);
      setEditingId(null);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6 max-w-7xl mx-auto">
          <div className="mb-6 mt-[100px] flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-semibold">Campaigns</h1>
              <p className="text-sm text-muted-foreground">
                Organize leads and sequences by initiative.
              </p>
            </div>
            <Button onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" /> New Campaign
            </Button>
          </div>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-base">Browse</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-3">
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    placeholder="Search campaigns..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="pl-9"
                  />
                </div>
                <div className="flex items-center gap-2">
                  <Filter className="h-4 w-4 text-muted-foreground" />
                  <Select
                    value={statusFilter}
                    onValueChange={(v) => setStatusFilter(v as any)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Status" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All statuses</SelectItem>
                      <SelectItem value="active">Active</SelectItem>
                      <SelectItem value="paused">Paused</SelectItem>
                      <SelectItem value="completed">Completed</SelectItem>
                      <SelectItem value="inactive">Inactive</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => toggleSort("createdAt")}
                  >
                    <ArrowUpDown className="mr-2 h-4 w-4" /> Sort by created
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => toggleSort("name")}
                  >
                    <ArrowUpDown className="mr-2 h-4 w-4" /> Sort by name
                  </Button>
                </div>
              </div>

              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Created</TableHead>
                      <TableHead>Description</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {loading && (
                      <TableRow>
                        <TableCell
                          colSpan={5}
                          className="h-24 text-center text-muted-foreground"
                        >
                          Loading campaigns...
                        </TableCell>
                      </TableRow>
                    )}
                    {!loading &&
                      filtered.map((c) => (
                        <TableRow key={c.id}>
                          <TableCell className="font-medium">
                            {c.name}
                          </TableCell>
                          <TableCell>
                            <StatusBadge status={c.status} />
                          </TableCell>
                          <TableCell>
                            {new Date(c.createdAt).toLocaleString()}
                          </TableCell>
                          <TableCell className="max-w-[420px] truncate text-muted-foreground">
                            {c.description || "—"}
                          </TableCell>
                          <TableCell className="text-right">
                            <div className="flex items-center justify-end gap-2">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => openEdit(c.id)}
                              >
                                <Pencil className="mr-2 h-4 w-4" /> Edit
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => openDelete(c.id)}
                              >
                                <Trash2 className="mr-2 h-4 w-4" /> Delete
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    {!loading && filtered.length === 0 && (
                      <TableRow>
                        <TableCell
                          colSpan={5}
                          className="h-24 text-center text-muted-foreground"
                        >
                          {error
                            ? String(error)
                            : "No campaigns match your filters."}
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>

          {/* Create dialog */}
          <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
            <DialogContent className="sm:max-w-[520px]">
              <DialogHeader>
                <DialogTitle>New Campaign</DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="name">Name</Label>
                  <Input
                    id="name"
                    value={draft.name}
                    onChange={(e) =>
                      setDraft((d) => ({ ...d, name: e.target.value }))
                    }
                    placeholder="e.g., Q2 Outbound Push"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="status">Status</Label>
                  <Select
                    value={draft.status}
                    onValueChange={(v) =>
                      setDraft((d) => ({ ...d, status: v as CampaignStatus }))
                    }
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="active">Active</SelectItem>
                      <SelectItem value="paused">Paused</SelectItem>
                      <SelectItem value="completed">Completed</SelectItem>
                      <SelectItem value="inactive">Inactive</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="desc">Description</Label>
                  <Textarea
                    id="desc"
                    value={draft.description}
                    onChange={(e) =>
                      setDraft((d) => ({ ...d, description: e.target.value }))
                    }
                    placeholder="What is this campaign about?"
                  />
                </div>
                <div className="flex justify-end gap-2">
                  <Button
                    variant="outline"
                    onClick={() => setIsCreateOpen(false)}
                  >
                    Cancel
                  </Button>
                  <Button onClick={handleCreate}>Create</Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>

          {/* Edit dialog */}
          <Dialog open={isEditOpen} onOpenChange={setIsEditOpen}>
            <DialogContent className="sm:max-w-[520px]">
              <DialogHeader>
                <DialogTitle>Edit Campaign</DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="name-edit">Name</Label>
                  <Input
                    id="name-edit"
                    value={draft.name}
                    onChange={(e) =>
                      setDraft((d) => ({ ...d, name: e.target.value }))
                    }
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="status-edit">Status</Label>
                  <Select
                    value={draft.status}
                    onValueChange={(v) =>
                      setDraft((d) => ({ ...d, status: v as CampaignStatus }))
                    }
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="active">Active</SelectItem>
                      <SelectItem value="paused">Paused</SelectItem>
                      <SelectItem value="completed">Completed</SelectItem>
                      <SelectItem value="inactive">Inactive</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="desc-edit">Description</Label>
                  <Textarea
                    id="desc-edit"
                    value={draft.description}
                    onChange={(e) =>
                      setDraft((d) => ({ ...d, description: e.target.value }))
                    }
                  />
                </div>
                <div className="flex justify-end gap-2">
                  <Button
                    variant="outline"
                    onClick={() => setIsEditOpen(false)}
                  >
                    Cancel
                  </Button>
                  <Button onClick={handleEdit}>Save</Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>

          {/* Delete dialog */}
          <Dialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
            <DialogContent className="sm:max-w-[480px]">
              <DialogHeader>
                <DialogTitle>Delete campaign</DialogTitle>
              </DialogHeader>
              <p className="text-sm text-muted-foreground">
                Are you sure you want to delete{" "}
                {campaignToDelete?.name ? (
                  <span className="font-medium">"{campaignToDelete.name}"</span>
                ) : (
                  "this campaign"
                )}
                ? This action cannot be undone.
              </p>
              <div className="flex justify-end gap-2">
                <Button
                  variant="outline"
                  onClick={() => setIsDeleteOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  variant="destructive"
                  onClick={handleDelete}
                  disabled={isDeleting}
                >
                  {isDeleting ? "Deleting..." : "Delete"}
                </Button>
              </div>
            </DialogContent>
          </Dialog>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
