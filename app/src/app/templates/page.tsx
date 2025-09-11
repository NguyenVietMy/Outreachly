"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  listTemplates,
  createTemplate,
  updateTemplate,
  deleteTemplate,
  parseContent,
  ALLOWED_VARS,
  countWords,
  TemplateModel,
  TemplatePlatform,
} from "@/lib/templates";
import { CheckCircle, FilePlus2, Pencil, Trash2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  TemplateEditor,
  TemplateEditorValue,
} from "@/components/templates/TemplateEditor";

type EditorMode = "create" | "edit";

export default function TemplatesPage() {
  const [templates, setTemplates] = useState<TemplateModel[]>([]);
  const [platformFilter, setPlatformFilter] = useState<
    TemplatePlatform | "ALL"
  >("ALL");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [mode, setMode] = useState<EditorMode>("create");
  const [editingId, setEditingId] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [platform, setPlatform] = useState<TemplatePlatform>("EMAIL");
  const [category, setCategory] = useState("");
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const bodyRef = useRef<HTMLTextAreaElement | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editSaving, setEditSaving] = useState(false);
  const [editValue, setEditValue] = useState<TemplateEditorValue | null>(null);

  const filtered = useMemo(() => {
    if (platformFilter === "ALL") return templates;
    return templates.filter((t) => t.platform === platformFilter);
  }, [templates, platformFilter]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listTemplates();
      setTemplates(data);
    } catch (e: any) {
      setError(e?.message || "Failed to load templates");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const resetEditor = () => {
    setMode("create");
    setEditingId(null);
    setName("");
    setPlatform("EMAIL");
    setCategory("");
    setSubject("");
    setBody("");
  };

  const startEdit = (t: TemplateModel) => {
    setEditingId(t.id);
    const content = parseContent<any>(t.contentJson);
    setEditValue({
      name: t.name,
      platform: t.platform,
      category: t.category || "",
      subject: content.subject || "",
      body: content.body || "",
    });
    setEditOpen(true);
  };

  const handleSave = async () => {
    try {
      setError(null);
      if (mode === "create") {
        await createTemplate({
          name,
          platform,
          category: category || undefined,
          content: platform === "EMAIL" ? { subject, body } : { body },
        });
      } else if (editingId) {
        await updateTemplate(editingId, {
          name,
          category: category || undefined,
          content: platform === "EMAIL" ? { subject, body } : { body },
        });
      }
      resetEditor();
      await load();
    } catch (e: any) {
      setError(e?.message || "Save failed");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Delete this template?")) return;
    try {
      await deleteTemplate(id);
      if (editingId === id) resetEditor();
      await load();
    } catch (e: any) {
      setError(e?.message || "Delete failed");
    }
  };

  const insertVar = (variable: (typeof ALLOWED_VARS)[number]) => {
    const el = bodyRef.current;
    const start = el?.selectionStart ?? body.length;
    const end = el?.selectionEnd ?? body.length;
    const token = `{{${variable}}}`;
    const before = body.slice(0, start);
    const after = body.slice(end);
    const next = `${before}${token}${after}`;
    setBody(next);
    setTimeout(() => {
      if (el) {
        const pos = before.length + token.length;
        el.selectionStart = el.selectionEnd = pos;
        el.focus();
      }
    }, 0);
  };

  const wordCount = useMemo(() => countWords(body), [body]);

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6 max-w-6xl mx-auto">
          <div className="mb-6 mt-[100px] flex items-center justify-between">
            <h1 className="text-2xl font-bold">Templates</h1>
            <div className="flex gap-2">
              <Select
                onValueChange={(v) => setPlatformFilter(v as any)}
                value={platformFilter}
              >
                <SelectTrigger className="w-40">
                  <SelectValue placeholder="Filter platform" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All</SelectItem>
                  <SelectItem value="EMAIL">Email</SelectItem>
                  <SelectItem value="LINKEDIN">LinkedIn</SelectItem>
                </SelectContent>
              </Select>
              <Button variant="outline" onClick={load}>
                Refresh
              </Button>
              <Dialog open={createOpen} onOpenChange={setCreateOpen}>
                <DialogTrigger asChild>
                  <Button>
                    <FilePlus2 className="h-4 w-4 mr-2" /> New Template
                  </Button>
                </DialogTrigger>
                <DialogContent className="sm:max-w-[600px]">
                  <DialogHeader>
                    <DialogTitle>Create Template</DialogTitle>
                  </DialogHeader>
                  <div className="space-y-3">
                    <Select
                      value={platform}
                      onValueChange={(v) => setPlatform(v as TemplatePlatform)}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Platform" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="EMAIL">Email</SelectItem>
                        <SelectItem value="LINKEDIN">LinkedIn</SelectItem>
                      </SelectContent>
                    </Select>
                    <TemplateEditor
                      initial={{
                        name: "",
                        platform,
                        category: "",
                        subject: "",
                        body: "",
                      }}
                      mode="create"
                      showAiButton
                      saving={creating}
                      onSave={async (val: TemplateEditorValue) => {
                        try {
                          setCreating(true);
                          await createTemplate({
                            name: val.name,
                            platform: val.platform,
                            category: val.category || undefined,
                            content:
                              val.platform === "EMAIL"
                                ? { subject: val.subject || "", body: val.body }
                                : { body: val.body },
                          });
                          setCreateOpen(false);
                          resetEditor();
                          await load();
                        } finally {
                          setCreating(false);
                        }
                      }}
                    />
                  </div>
                </DialogContent>
              </Dialog>

              {/* Edit Template Modal */}
              <Dialog open={editOpen} onOpenChange={setEditOpen}>
                <DialogContent className="sm:max-w-[600px]">
                  <DialogHeader>
                    <DialogTitle>Edit Template</DialogTitle>
                  </DialogHeader>
                  {editValue && (
                    <div className="space-y-3">
                      <Select
                        value={editValue.platform}
                        onValueChange={(v) =>
                          setEditValue({
                            ...editValue,
                            platform: v as TemplatePlatform,
                          })
                        }
                      >
                        <SelectTrigger>
                          <SelectValue placeholder="Platform" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="EMAIL">Email</SelectItem>
                          <SelectItem value="LINKEDIN">LinkedIn</SelectItem>
                        </SelectContent>
                      </Select>
                      <TemplateEditor
                        initial={editValue}
                        mode="edit"
                        showAiButton
                        saving={editSaving}
                        onChange={setEditValue}
                        onSave={async (val) => {
                          if (!editingId) return;
                          try {
                            setEditSaving(true);
                            await updateTemplate(editingId, {
                              name: val.name,
                              category: val.category || undefined,
                              platform: val.platform,
                              content:
                                val.platform === "EMAIL"
                                  ? {
                                      subject: val.subject || "",
                                      body: val.body,
                                    }
                                  : { body: val.body },
                            });
                            setEditOpen(false);
                            await load();
                          } finally {
                            setEditSaving(false);
                          }
                        }}
                        onCancel={() => setEditOpen(false)}
                      />
                    </div>
                  )}
                </DialogContent>
              </Dialog>
            </div>
          </div>

          {error && (
            <Alert className="mb-4" variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-4">
              {loading ? (
                <Card>
                  <CardContent className="p-6">Loading...</CardContent>
                </Card>
              ) : (
                filtered.map((t) => (
                  <Card key={t.id}>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0">
                      <div>
                        <CardTitle className="text-base">{t.name}</CardTitle>
                        <div className="text-xs text-muted-foreground">
                          {t.platform} {t.category ? `· ${t.category}` : ""}
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Button
                          variant="outline"
                          size="icon"
                          onClick={() => startEdit(t)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="outline"
                          size="icon"
                          onClick={() => handleDelete(t.id)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </CardHeader>
                    <CardContent>
                      <div className="text-sm text-muted-foreground line-clamp-2">
                        {parseContent<any>(t.contentJson)?.body || ""}
                      </div>
                    </CardContent>
                  </Card>
                ))
              )}
              {filtered.length === 0 && !loading && (
                <Card>
                  <CardContent className="p-6 text-sm text-muted-foreground">
                    No templates yet. Create one on the right.
                  </CardContent>
                </Card>
              )}
            </div>

            <div className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-base flex items-center gap-2">
                    <CheckCircle className="h-4 w-4" /> Template Library
                    (placeholder)
                  </CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">
                  Coming soon: curated templates for common outreach scenarios.
                </CardContent>
              </Card>
            </div>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
