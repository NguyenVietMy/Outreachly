"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Plus,
  Clock,
  Play,
  Pause,
  Trash2,
  Calendar,
  Mail,
  BarChart3,
} from "lucide-react";
import {
  useCampaignCheckpoints,
  CampaignCheckpoint,
} from "@/hooks/useCampaignCheckpoints";

interface CampaignCheckpointsCardProps {
  campaignId: string;
  campaignName: string;
}

type CheckpointStatus = CampaignCheckpoint["status"];
type DayOfWeek = CampaignCheckpoint["dayOfWeek"];

const DAYS_OF_WEEK: { value: DayOfWeek; label: string }[] = [
  { value: "MONDAY", label: "Monday" },
  { value: "TUESDAY", label: "Tuesday" },
  { value: "WEDNESDAY", label: "Wednesday" },
  { value: "THURSDAY", label: "Thursday" },
  { value: "FRIDAY", label: "Friday" },
  { value: "SATURDAY", label: "Saturday" },
  { value: "SUNDAY", label: "Sunday" },
];

function StatusBadge({ status }: { status: CheckpointStatus }) {
  const style = {
    pending: "bg-gray-100 text-gray-800",
    active: "bg-green-100 text-green-800",
    paused: "bg-yellow-100 text-yellow-800",
    completed: "bg-blue-100 text-blue-800",
  }[status];
  return <Badge className={style}>{status}</Badge>;
}

function formatTime(timeString: string): string {
  try {
    const [hours, minutes] = timeString.split(":");
    const hour = parseInt(hours, 10);
    const ampm = hour >= 12 ? "PM" : "AM";
    const displayHour = hour % 12 || 12;
    return `${displayHour}:${minutes} ${ampm}`;
  } catch {
    return timeString;
  }
}

function formatDay(day: DayOfWeek): string {
  return DAYS_OF_WEEK.find((d) => d.value === day)?.label || day;
}

export default function CampaignCheckpointsCard({
  campaignId,
  campaignName,
}: CampaignCheckpointsCardProps) {
  const {
    checkpoints,
    templates,
    loading,
    error,
    createCheckpoint,
    updateCheckpoint,
    deleteCheckpoint,
    activateCheckpoint,
    pauseCheckpoint,
  } = useCampaignCheckpoints(campaignId);

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState<string | null>(null);
  const [isActivating, setIsActivating] = useState<string | null>(null);

  const [draft, setDraft] = useState<{
    name: string;
    dayOfWeek: DayOfWeek;
    timeOfDay: string;
    emailTemplateId: string | null;
  }>({
    name: "",
    dayOfWeek: "MONDAY",
    timeOfDay: "09:00",
    emailTemplateId: null,
  });

  const handleCreate = async () => {
    try {
      await createCheckpoint({
        name: draft.name,
        dayOfWeek: draft.dayOfWeek,
        timeOfDay: draft.timeOfDay + ":00", // Add seconds
        emailTemplateId: draft.emailTemplateId || undefined,
      });
      setIsCreateOpen(false);
      setDraft({
        name: "",
        dayOfWeek: "MONDAY",
        timeOfDay: "09:00",
        emailTemplateId: null,
      });
    } catch (err) {
      console.error("Failed to create checkpoint:", err);
    }
  };

  const handleDelete = async (checkpointId: string) => {
    try {
      setIsDeleting(checkpointId);
      await deleteCheckpoint(checkpointId);
    } catch (err) {
      console.error("Failed to delete checkpoint:", err);
    } finally {
      setIsDeleting(null);
    }
  };

  const handleActivate = async (checkpointId: string) => {
    try {
      setIsActivating(checkpointId);
      await activateCheckpoint(checkpointId);
    } catch (err) {
      console.error("Failed to activate checkpoint:", err);
    } finally {
      setIsActivating(null);
    }
  };

  const handlePause = async (checkpointId: string) => {
    try {
      setIsActivating(checkpointId);
      await pauseCheckpoint(checkpointId);
    } catch (err) {
      console.error("Failed to pause checkpoint:", err);
    } finally {
      setIsActivating(null);
    }
  };

  const handleResume = async (checkpointId: string) => {
    try {
      setIsActivating(checkpointId);
      await activateCheckpoint(checkpointId);
    } catch (err) {
      console.error("Failed to resume checkpoint:", err);
    } finally {
      setIsActivating(null);
    }
  };

  const getSelectedTemplate = () => {
    return draft.emailTemplateId
      ? templates.find((t) => t.id === draft.emailTemplateId)
      : null;
  };

  return (
    <>
      <Card className="mt-6">
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base flex items-center gap-2">
              <Calendar className="h-4 w-4" />
              Campaign Checkpoints
            </CardTitle>
            <Button size="sm" onClick={() => setIsCreateOpen(true)}>
              <Plus className="mr-2 h-4 w-4" /> Add Checkpoint
            </Button>
          </div>
          <p className="text-sm text-muted-foreground">
            Schedule emails to be sent on specific days and times for "
            {campaignName}"
          </p>
        </CardHeader>
        <CardContent>
          {loading && (
            <div className="text-center py-8 text-muted-foreground">
              Loading checkpoints...
            </div>
          )}

          {error && (
            <div className="text-center py-8 text-red-600">{error}</div>
          )}

          {!loading && !error && checkpoints.length === 0 && (
            <div className="text-center py-8 text-muted-foreground">
              <Calendar className="h-12 w-12 mx-auto mb-4 text-gray-400" />
              <p className="font-medium">No checkpoints yet</p>
              <p className="text-sm">
                Create your first checkpoint to start scheduling emails
              </p>
            </div>
          )}

          {!loading && !error && checkpoints.length > 0 && (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Schedule</TableHead>
                    <TableHead>Template</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {checkpoints.map((checkpoint) => {
                    const template = templates.find(
                      (t) => t.id === checkpoint.emailTemplateId
                    );
                    return (
                      <TableRow key={checkpoint.id}>
                        <TableCell className="font-medium">
                          {checkpoint.name}
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2 text-sm">
                            <Calendar className="h-4 w-4 text-muted-foreground" />
                            {formatDay(checkpoint.dayOfWeek)}
                            <Clock className="h-4 w-4 text-muted-foreground ml-2" />
                            {formatTime(checkpoint.timeOfDay)}
                          </div>
                        </TableCell>
                        <TableCell>
                          {template ? (
                            <div className="flex items-center gap-2 text-sm">
                              <Mail className="h-4 w-4 text-muted-foreground" />
                              {template.name}
                            </div>
                          ) : (
                            <span className="text-muted-foreground text-sm">
                              No template
                            </span>
                          )}
                        </TableCell>
                        <TableCell>
                          <StatusBadge status={checkpoint.status} />
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex items-center justify-end gap-2">
                            {checkpoint.status === "pending" && (
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleActivate(checkpoint.id)}
                                disabled={isActivating === checkpoint.id}
                              >
                                <Play className="mr-2 h-4 w-4" />
                                {isActivating === checkpoint.id
                                  ? "Activating..."
                                  : "Activate"}
                              </Button>
                            )}
                            {checkpoint.status === "active" && (
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handlePause(checkpoint.id)}
                                disabled={isActivating === checkpoint.id}
                              >
                                <Pause className="mr-2 h-4 w-4" />
                                {isActivating === checkpoint.id
                                  ? "Pausing..."
                                  : "Pause"}
                              </Button>
                            )}
                            {checkpoint.status === "paused" && (
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleResume(checkpoint.id)}
                                disabled={isActivating === checkpoint.id}
                              >
                                <Play className="mr-2 h-4 w-4" />
                                {isActivating === checkpoint.id
                                  ? "Resuming..."
                                  : "Resume"}
                              </Button>
                            )}
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleDelete(checkpoint.id)}
                              disabled={isDeleting === checkpoint.id}
                            >
                              <Trash2 className="mr-2 h-4 w-4" />
                              {isDeleting === checkpoint.id
                                ? "Deleting..."
                                : "Delete"}
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Create Checkpoint Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle>Create New Checkpoint</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="checkpoint-name">Checkpoint Name</Label>
              <Input
                id="checkpoint-name"
                value={draft.name}
                onChange={(e) =>
                  setDraft((d) => ({ ...d, name: e.target.value }))
                }
                placeholder="e.g., Welcome Email, Follow-up Day 1"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="day-of-week">Day of Week</Label>
                <Select
                  value={draft.dayOfWeek}
                  onValueChange={(v) =>
                    setDraft((d) => ({ ...d, dayOfWeek: v as DayOfWeek }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {DAYS_OF_WEEK.map((day) => (
                      <SelectItem key={day.value} value={day.value}>
                        {day.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="time-of-day">Time</Label>
                <Input
                  id="time-of-day"
                  type="time"
                  value={draft.timeOfDay}
                  onChange={(e) =>
                    setDraft((d) => ({ ...d, timeOfDay: e.target.value }))
                  }
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="email-template">Email Template</Label>
              <Select
                value={draft.emailTemplateId || undefined}
                onValueChange={(v) =>
                  setDraft((d) => ({ ...d, emailTemplateId: v || null }))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select a template (optional)" />
                </SelectTrigger>
                <SelectContent>
                  {templates.map((template) => (
                    <SelectItem key={template.id} value={template.id}>
                      {template.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {getSelectedTemplate() && (
                <p className="text-sm text-muted-foreground">
                  Selected: {getSelectedTemplate()?.name}
                </p>
              )}
              {!draft.emailTemplateId && (
                <p className="text-sm text-muted-foreground">
                  No template selected - emails will be sent without a template
                </p>
              )}
            </div>

            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button onClick={handleCreate} disabled={!draft.name.trim()}>
                Create Checkpoint
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
