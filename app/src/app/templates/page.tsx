"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Alert, AlertDescription } from "@/components/ui/alert";
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
  DialogTrigger,
} from "@/components/ui/dialog";
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
import {
  generateTemplate,
  improveTemplate,
  TEMPLATE_CATEGORIES,
  TONE_OPTIONS,
  IMPROVEMENT_TYPES,
  GenerateTemplateRequest,
  ImproveTemplateRequest,
} from "@/lib/aiService";
import {
  CheckCircle,
  FilePlus2,
  Pencil,
  Trash2,
  Sparkles,
  Wand2,
  BookOpen,
  Search,
  Filter,
  RefreshCw,
  Copy,
  Eye,
  Star,
  TrendingUp,
  Users,
  MessageSquare,
  Mail,
  Linkedin,
  Zap,
  Brain,
  Lightbulb,
  Target,
  Clock,
  BarChart3,
  AlertTriangle,
} from "lucide-react";
import { useToast } from "@/components/ui/use-toast";

type EditorMode = "create" | "edit";

export default function TemplatesPage() {
  const { toast } = useToast();
  const [templates, setTemplates] = useState<TemplateModel[]>([]);
  const [platformFilter, setPlatformFilter] = useState<
    TemplatePlatform | "ALL"
  >("ALL");
  const [categoryFilter, setCategoryFilter] = useState<string>("ALL");
  const [searchTerm, setSearchTerm] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState("templates");

  // AI Generation States
  const [aiGenerating, setAiGenerating] = useState(false);
  const [aiPrompt, setAiPrompt] = useState("");
  const [aiPlatform, setAiPlatform] = useState<TemplatePlatform>("EMAIL");
  const [aiCategory, setAiCategory] = useState("");
  const [aiTone, setAiTone] = useState("");
  const [aiGeneratedContent, setAiGeneratedContent] = useState<any>(null);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [showUnsavedDialog, setShowUnsavedDialog] = useState(false);

  // Template Editor States
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
  const [editValue, setEditValue] = useState<any>(null);

  const filtered = useMemo(() => {
    let filteredTemplates = templates;

    if (platformFilter !== "ALL") {
      filteredTemplates = filteredTemplates.filter(
        (t) => t.platform === platformFilter
      );
    }

    if (categoryFilter !== "ALL") {
      filteredTemplates = filteredTemplates.filter(
        (t) => t.category === categoryFilter
      );
    }

    if (searchTerm) {
      filteredTemplates = filteredTemplates.filter(
        (t) =>
          t.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
          t.category?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          parseContent<any>(t.contentJson)
            ?.body?.toLowerCase()
            .includes(searchTerm.toLowerCase())
      );
    }

    return filteredTemplates;
  }, [templates, platformFilter, categoryFilter, searchTerm]);

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

  // Track unsaved changes
  useEffect(() => {
    const hasChanges = aiGeneratedContent !== null;
    setHasUnsavedChanges(hasChanges);
  }, [aiGeneratedContent]);

  // Warn user about unsaved changes
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (hasUnsavedChanges) {
        e.preventDefault();
        e.returnValue =
          "You have unsaved changes. Are you sure you want to leave?";
        return e.returnValue;
      }
    };

    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [hasUnsavedChanges]);

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
      console.log("Creating template:", {
        name,
        platform,
        category,
        subject,
        body,
      });
      if (mode === "create") {
        const result = await createTemplate({
          name,
          platform,
          category: category || undefined,
          content: platform === "EMAIL" ? { subject, body } : { body },
        });
        console.log("Template created successfully:", result);
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
      console.error("Template creation failed:", e);
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

  // AI Generation Functions
  const handleAiGenerate = async () => {
    if (!aiPrompt.trim()) {
      toast({
        title: "Prompt Required",
        description: "Please enter a prompt for AI generation",
        variant: "destructive",
      });
      return;
    }

    setAiGenerating(true);
    try {
      const request: GenerateTemplateRequest = {
        prompt: aiPrompt,
        platform: aiPlatform,
        category: aiCategory || undefined,
        tone: aiTone || undefined,
      };

      const response = await generateTemplate(request);

      if (response.success && response.data) {
        try {
          const parsedData = JSON.parse(response.data);
          setAiGeneratedContent(parsedData);
          toast({
            title: "Template Generated!",
            description: "AI has generated your template. Review and save it.",
          });
        } catch (parseError) {
          toast({
            title: "Parse Error",
            description: "Failed to parse AI response. Please try again.",
            variant: "destructive",
          });
        }
      } else {
        toast({
          title: "Generation Failed",
          description: response.error || "Failed to generate template",
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to generate template. Please try again.",
        variant: "destructive",
      });
    } finally {
      setAiGenerating(false);
    }
  };

  const handleUseAiTemplate = () => {
    if (aiGeneratedContent) {
      setName(`AI Generated - ${aiCategory || "Template"}`);
      setPlatform(aiPlatform);
      setCategory(aiCategory);
      setSubject(aiGeneratedContent.subject || "");
      setBody(aiGeneratedContent.body || "");
      setCreateOpen(true);
      setAiGeneratedContent(null);
      setAiPrompt("");
    }
  };

  const handleTabChange = (newTab: string) => {
    if (hasUnsavedChanges && newTab !== activeTab) {
      setShowUnsavedDialog(true);
      return;
    }
    setActiveTab(newTab);
  };

  const handleDiscardChanges = () => {
    setAiGeneratedContent(null);
    setAiPrompt("");
    setHasUnsavedChanges(false);
    setShowUnsavedDialog(false);
    setActiveTab("templates");
  };

  const handleSaveAndContinue = () => {
    if (aiGeneratedContent) {
      handleUseAiTemplate();
    }
    setShowUnsavedDialog(false);
  };

  const handleImproveTemplate = async (
    template: TemplateModel,
    improvementType: string
  ) => {
    const content = parseContent<any>(template.contentJson);
    const currentTemplate = JSON.stringify({
      subject: content.subject || "",
      body: content.body || "",
    });

    try {
      const request: ImproveTemplateRequest = {
        currentTemplate,
        platform: template.platform,
        improvementType: improvementType as any,
      };

      const response = await improveTemplate(request);

      if (response.success && response.data) {
        try {
          const parsedData = JSON.parse(response.data);
          setEditValue({
            name: template.name,
            platform: template.platform,
            category: template.category || "",
            subject: parsedData.subject || content.subject || "",
            body: parsedData.body || content.body || "",
          });
          setEditOpen(true);
          toast({
            title: "Template Improved!",
            description: `Template has been improved for ${improvementType}`,
          });
        } catch (parseError) {
          toast({
            title: "Parse Error",
            description: "Failed to parse AI response. Please try again.",
            variant: "destructive",
          });
        }
      } else {
        toast({
          title: "Improvement Failed",
          description: response.error || "Failed to improve template",
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to improve template. Please try again.",
        variant: "destructive",
      });
    }
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="min-h-screen bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-50">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
            {/* Header */}
            <div className="mb-6 md:mb-8">
              <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
                <div>
                  <h1 className="text-2xl md:text-4xl font-bold bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent">
                    AI Template Studio
                  </h1>
                  <p className="mt-2 text-base md:text-lg text-gray-600">
                    Create, optimize, and manage professional email and LinkedIn
                    templates with AI
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2 md:gap-3">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={load}
                    className="flex items-center gap-1 md:gap-2 text-xs md:text-sm"
                  >
                    <RefreshCw className="h-3 w-3 md:h-4 md:w-4" />
                    <span className="hidden sm:inline">Refresh</span>
                  </Button>
                  <Dialog open={createOpen} onOpenChange={setCreateOpen}>
                    <DialogTrigger asChild>
                      <Button className="flex items-center gap-1 md:gap-2 text-xs md:text-sm">
                        <FilePlus2 className="h-3 w-3 md:h-4 md:w-4" />
                        <span className="hidden sm:inline">New Template</span>
                      </Button>
                    </DialogTrigger>
                    <DialogContent className="sm:max-w-[600px]">
                      <DialogHeader>
                        <DialogTitle>Create Template</DialogTitle>
                      </DialogHeader>
                      <div className="space-y-3">
                        <Select
                          value={platform}
                          onValueChange={(v) =>
                            setPlatform(v as TemplatePlatform)
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
                        <div className="space-y-3">
                          <Input
                            placeholder="Template Name"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                          />
                          <Input
                            placeholder="Category (optional)"
                            value={category}
                            onChange={(e) => setCategory(e.target.value)}
                          />
                          {platform === "EMAIL" && (
                            <Input
                              placeholder="Subject"
                              value={subject}
                              onChange={(e) => setSubject(e.target.value)}
                            />
                          )}
                          <div className="space-y-2">
                            <div className="flex items-center justify-between">
                              <Label className="text-sm font-medium">
                                Body ({wordCount} words)
                              </Label>
                              <div className="flex gap-1">
                                {ALLOWED_VARS.map((v) => (
                                  <Button
                                    key={v}
                                    type="button"
                                    size="sm"
                                    variant="outline"
                                    onClick={() => insertVar(v)}
                                  >
                                    {`{{${v}}}`}
                                  </Button>
                                ))}
                              </div>
                            </div>
                            <Textarea
                              ref={bodyRef}
                              rows={10}
                              value={body}
                              onChange={(e) => setBody(e.target.value)}
                              placeholder="Enter your template content..."
                            />
                          </div>
                          <div className="flex gap-2">
                            <Button
                              onClick={handleSave}
                              disabled={
                                !name ||
                                !body ||
                                (platform === "EMAIL" && !subject) ||
                                creating
                              }
                            >
                              {creating ? "Saving..." : "Save Template"}
                            </Button>
                            <Button
                              variant="outline"
                              onClick={() => setCreateOpen(false)}
                            >
                              Cancel
                            </Button>
                          </div>
                        </div>
                      </div>
                    </DialogContent>
                  </Dialog>
                </div>
              </div>
            </div>

            {/* Main Content Tabs */}
            <Tabs
              value={activeTab}
              onValueChange={handleTabChange}
              className="space-y-4 md:space-y-6"
            >
              <TabsList className="grid w-full grid-cols-2 md:grid-cols-3 h-auto">
                <TabsTrigger
                  value="templates"
                  className="flex items-center gap-1 md:gap-2 text-xs md:text-sm py-2"
                >
                  <BookOpen className="h-3 w-3 md:h-4 md:w-4" />
                  <span className="hidden sm:inline">My Templates</span>
                </TabsTrigger>
                <TabsTrigger
                  value="ai-generate"
                  className="flex items-center gap-1 md:gap-2 text-xs md:text-sm py-2"
                >
                  <Sparkles className="h-3 w-3 md:h-4 md:w-4" />
                  <span className="hidden sm:inline">AI Generator</span>
                </TabsTrigger>
                <TabsTrigger
                  value="analytics"
                  className="flex items-center gap-1 md:gap-2 text-xs md:text-sm py-2"
                >
                  <BarChart3 className="h-3 w-3 md:h-4 md:w-4" />
                  <span className="hidden sm:inline">Analytics</span>
                </TabsTrigger>
              </TabsList>

              {/* Templates Tab */}
              <TabsContent value="templates" className="space-y-4 md:space-y-6">
                {/* Filters */}
                <Card className="shadow-lg border-0">
                  <CardContent className="p-4">
                    <div className="flex flex-col md:flex-row gap-4">
                      <div className="flex-1">
                        <div className="relative">
                          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                          <Input
                            placeholder="Search templates..."
                            className="pl-9"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                          />
                        </div>
                      </div>
                      <div className="flex gap-2">
                        <Select
                          value={platformFilter}
                          onValueChange={(v) => setPlatformFilter(v as any)}
                        >
                          <SelectTrigger className="w-32">
                            <SelectValue placeholder="Platform" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="ALL">All</SelectItem>
                            <SelectItem value="EMAIL">Email</SelectItem>
                            <SelectItem value="LINKEDIN">LinkedIn</SelectItem>
                          </SelectContent>
                        </Select>
                        <Select
                          value={categoryFilter}
                          onValueChange={setCategoryFilter}
                        >
                          <SelectTrigger className="w-40">
                            <SelectValue placeholder="Category" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="ALL">All Categories</SelectItem>
                            {TEMPLATE_CATEGORIES.map((cat) => (
                              <SelectItem key={cat} value={cat}>
                                {cat}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                    </div>
                  </CardContent>
                </Card>

                {/* Templates Grid */}
                {loading ? (
                  <Card className="shadow-lg border-0">
                    <CardContent className="p-6 text-center">
                      <RefreshCw className="h-8 w-8 animate-spin mx-auto mb-4 text-blue-600" />
                      <p className="text-gray-600">Loading templates...</p>
                    </CardContent>
                  </Card>
                ) : filtered.length > 0 ? (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
                    {filtered.map((template) => {
                      const content = parseContent<any>(template.contentJson);
                      return (
                        <Card
                          key={template.id}
                          className="shadow-lg border-0 hover:shadow-xl transition-shadow"
                        >
                          <CardHeader className="pb-3">
                            <div className="flex items-start justify-between">
                              <div className="flex-1">
                                <CardTitle className="text-lg flex items-center gap-2">
                                  {template.platform === "EMAIL" ? (
                                    <Mail className="h-5 w-5 text-blue-600" />
                                  ) : (
                                    <Linkedin className="h-5 w-5 text-blue-700" />
                                  )}
                                  {template.name}
                                </CardTitle>
                                <div className="flex items-center gap-2 mt-2">
                                  {template.category && (
                                    <Badge
                                      variant="secondary"
                                      className="text-xs"
                                    >
                                      {template.category}
                                    </Badge>
                                  )}
                                  <Badge variant="outline" className="text-xs">
                                    {template.platform}
                                  </Badge>
                                </div>
                              </div>
                              <div className="flex items-center gap-1">
                                <Button
                                  type="button"
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => startEdit(template)}
                                  className="h-8 w-8 p-0"
                                >
                                  <Pencil className="h-4 w-4" />
                                </Button>
                                <Button
                                  type="button"
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => handleDelete(template.id)}
                                  className="h-8 w-8 p-0 text-red-600 hover:text-red-700"
                                >
                                  <Trash2 className="h-4 w-4" />
                                </Button>
                              </div>
                            </div>
                          </CardHeader>
                          <CardContent className="pt-0">
                            <div className="space-y-3">
                              {template.platform === "EMAIL" &&
                                content.subject && (
                                  <div>
                                    <Label className="text-xs font-medium text-gray-500">
                                      Subject
                                    </Label>
                                    <p className="text-sm text-gray-700 line-clamp-1">
                                      {content.subject}
                                    </p>
                                  </div>
                                )}
                              <div>
                                <Label className="text-xs font-medium text-gray-500">
                                  Content
                                </Label>
                                <p className="text-sm text-gray-700 line-clamp-3">
                                  {content.body}
                                </p>
                              </div>
                              <div className="flex items-center justify-between pt-2">
                                <div className="flex gap-1">
                                  {IMPROVEMENT_TYPES.slice(0, 2).map(
                                    (improvement) => (
                                      <Button
                                        key={improvement.value}
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() =>
                                          handleImproveTemplate(
                                            template,
                                            improvement.value
                                          )
                                        }
                                        className="text-xs h-7"
                                      >
                                        <Wand2 className="h-3 w-3 mr-1" />
                                        {improvement.label}
                                      </Button>
                                    )
                                  )}
                                </div>
                                <div className="text-xs text-gray-500">
                                  {countWords(content.body || "")} words
                                </div>
                              </div>
                            </div>
                          </CardContent>
                        </Card>
                      );
                    })}
                  </div>
                ) : (
                  <Card className="shadow-lg border-0">
                    <CardContent className="p-6 text-center">
                      <BookOpen className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                      <p className="text-gray-500 mb-4">No templates found</p>
                      <Button onClick={() => setActiveTab("ai-generate")}>
                        <Sparkles className="h-4 w-4 mr-2" />
                        Generate with AI
                      </Button>
                    </CardContent>
                  </Card>
                )}
              </TabsContent>

              {/* AI Generator Tab */}
              <TabsContent
                value="ai-generate"
                className="space-y-4 md:space-y-6"
              >
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 md:gap-6">
                  {/* AI Generation Form */}
                  <Card className="shadow-lg border-0">
                    <CardHeader className="bg-gradient-to-r from-purple-50 to-pink-50">
                      <CardTitle className="flex items-center gap-2 text-xl">
                        <Brain className="h-6 w-6 text-purple-600" />
                        AI Template Generator
                      </CardTitle>
                      <CardDescription className="text-base">
                        Describe what you want and let AI create the perfect
                        template
                      </CardDescription>
                    </CardHeader>
                    <CardContent className="p-6 space-y-4">
                      <div className="space-y-2">
                        <Label>What kind of template do you want?</Label>
                        <Textarea
                          placeholder="e.g., A follow-up email for prospects who didn't respond to my initial outreach, focusing on providing value and building trust..."
                          value={aiPrompt}
                          onChange={(e) => setAiPrompt(e.target.value)}
                          rows={4}
                        />
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="space-y-2">
                          <Label>Platform</Label>
                          <Select
                            value={aiPlatform}
                            onValueChange={(v) =>
                              setAiPlatform(v as TemplatePlatform)
                            }
                          >
                            <SelectTrigger>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="EMAIL">Email</SelectItem>
                              <SelectItem value="LINKEDIN">LinkedIn</SelectItem>
                            </SelectContent>
                          </Select>
                        </div>
                        <div className="space-y-2">
                          <Label>Category</Label>
                          <Select
                            value={aiCategory || "none"}
                            onValueChange={(value) =>
                              setAiCategory(value === "none" ? "" : value)
                            }
                          >
                            <SelectTrigger>
                              <SelectValue placeholder="Optional" />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="none">None</SelectItem>
                              {TEMPLATE_CATEGORIES.map((cat) => (
                                <SelectItem key={cat} value={cat}>
                                  {cat}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </div>
                        <div className="space-y-2">
                          <Label>Tone</Label>
                          <Select
                            value={aiTone || "default"}
                            onValueChange={(value) =>
                              setAiTone(value === "default" ? "" : value)
                            }
                          >
                            <SelectTrigger>
                              <SelectValue placeholder="Optional" />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="default">Default</SelectItem>
                              {TONE_OPTIONS.map((tone) => (
                                <SelectItem key={tone} value={tone}>
                                  {tone}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </div>
                      </div>

                      <Button
                        onClick={handleAiGenerate}
                        disabled={!aiPrompt.trim() || aiGenerating}
                        className="w-full h-12 text-lg font-medium bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-700 hover:to-pink-700"
                      >
                        {aiGenerating ? (
                          <>
                            <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
                            Generating...
                          </>
                        ) : (
                          <>
                            <Sparkles className="mr-2 h-5 w-5" />
                            Generate Template
                          </>
                        )}
                      </Button>
                    </CardContent>
                  </Card>

                  {/* AI Generated Result */}
                  <Card className="shadow-lg border-0">
                    <CardHeader className="bg-gradient-to-r from-green-50 to-emerald-50">
                      <CardTitle className="flex items-center gap-2 text-xl">
                        <Lightbulb className="h-6 w-6 text-green-600" />
                        Generated Template
                      </CardTitle>
                      <CardDescription className="text-base">
                        Review and customize your AI-generated template
                      </CardDescription>
                    </CardHeader>
                    <CardContent className="p-6">
                      {aiGeneratedContent ? (
                        <div className="space-y-4">
                          {aiPlatform === "EMAIL" &&
                            aiGeneratedContent.subject && (
                              <div>
                                <Label className="text-sm font-medium text-gray-700">
                                  Subject
                                </Label>
                                <div className="p-3 bg-gray-50 rounded-lg text-sm">
                                  {aiGeneratedContent.subject}
                                </div>
                              </div>
                            )}
                          <div>
                            <Label className="text-sm font-medium text-gray-700">
                              Content
                            </Label>
                            <div className="p-3 bg-gray-50 rounded-lg text-sm whitespace-pre-wrap">
                              {aiGeneratedContent.body}
                            </div>
                          </div>
                          <div className="flex gap-2">
                            <Button
                              onClick={handleUseAiTemplate}
                              className="flex-1"
                            >
                              <FilePlus2 className="h-4 w-4 mr-2" />
                              Use This Template
                            </Button>
                            <Button
                              variant="outline"
                              onClick={() => setAiGeneratedContent(null)}
                            >
                              <RefreshCw className="h-4 w-4 mr-2" />
                              Regenerate
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <div className="text-center py-8">
                          <Brain className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                          <p className="text-gray-500">
                            Generate a template to see it here
                          </p>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                </div>
              </TabsContent>

              {/* Analytics Tab */}
              <TabsContent value="analytics" className="space-y-4 md:space-y-6">
                <Card className="shadow-lg border-0">
                  <CardHeader className="bg-gradient-to-r from-blue-50 to-indigo-50">
                    <CardTitle className="flex items-center gap-2 text-xl">
                      <BarChart3 className="h-6 w-6 text-blue-600" />
                      Template Analytics
                    </CardTitle>
                    <CardDescription className="text-base">
                      Track your template performance and usage
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="p-6">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                      <div className="text-center">
                        <div className="text-3xl font-bold text-blue-600">
                          {templates.length}
                        </div>
                        <div className="text-sm text-gray-500">
                          Total Templates
                        </div>
                      </div>
                      <div className="text-center">
                        <div className="text-3xl font-bold text-green-600">
                          {
                            templates.filter((t) => t.platform === "EMAIL")
                              .length
                          }
                        </div>
                        <div className="text-sm text-gray-500">
                          Email Templates
                        </div>
                      </div>
                      <div className="text-center">
                        <div className="text-3xl font-bold text-purple-600">
                          {
                            templates.filter((t) => t.platform === "LINKEDIN")
                              .length
                          }
                        </div>
                        <div className="text-sm text-gray-500">
                          LinkedIn Templates
                        </div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>
            </Tabs>

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
                    <div className="space-y-3">
                      <Input
                        placeholder="Template Name"
                        value={editValue.name}
                        onChange={(e) =>
                          setEditValue({ ...editValue, name: e.target.value })
                        }
                      />
                      <Input
                        placeholder="Category (optional)"
                        value={editValue.category || ""}
                        onChange={(e) =>
                          setEditValue({
                            ...editValue,
                            category: e.target.value,
                          })
                        }
                      />
                      {editValue.platform === "EMAIL" && (
                        <Input
                          placeholder="Subject"
                          value={editValue.subject || ""}
                          onChange={(e) =>
                            setEditValue({
                              ...editValue,
                              subject: e.target.value,
                            })
                          }
                        />
                      )}
                      <div className="space-y-2">
                        <Label className="text-sm font-medium">
                          Body ({countWords(editValue.body || "")} words)
                        </Label>
                        <Textarea
                          rows={10}
                          value={editValue.body || ""}
                          onChange={(e) =>
                            setEditValue({ ...editValue, body: e.target.value })
                          }
                          placeholder="Enter your template content..."
                        />
                      </div>
                      <div className="flex gap-2">
                        <Button
                          onClick={async () => {
                            if (!editingId) return;
                            try {
                              setEditSaving(true);
                              await updateTemplate(editingId, {
                                name: editValue.name,
                                category: editValue.category || undefined,
                                platform: editValue.platform,
                                content:
                                  editValue.platform === "EMAIL"
                                    ? {
                                        subject: editValue.subject || "",
                                        body: editValue.body,
                                      }
                                    : { body: editValue.body },
                              });
                              setEditOpen(false);
                              await load();
                            } finally {
                              setEditSaving(false);
                            }
                          }}
                          disabled={
                            !editValue.name ||
                            !editValue.body ||
                            (editValue.platform === "EMAIL" &&
                              !editValue.subject) ||
                            editSaving
                          }
                        >
                          {editSaving ? "Saving..." : "Save Changes"}
                        </Button>
                        <Button
                          variant="outline"
                          onClick={() => setEditOpen(false)}
                        >
                          Cancel
                        </Button>
                      </div>
                    </div>
                  </div>
                )}
              </DialogContent>
            </Dialog>

            {/* Unsaved Changes Dialog */}
            <Dialog
              open={showUnsavedDialog}
              onOpenChange={setShowUnsavedDialog}
            >
              <DialogContent className="sm:max-w-[400px]">
                <DialogHeader>
                  <DialogTitle className="flex items-center gap-2">
                    <AlertTriangle className="h-5 w-5 text-amber-500" />
                    Unsaved Changes
                  </DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                  <p className="text-sm text-gray-600">
                    You have an unsaved AI-generated template. What would you
                    like to do?
                  </p>
                  <div className="flex gap-2">
                    <Button onClick={handleSaveAndContinue} className="flex-1">
                      <FilePlus2 className="h-4 w-4 mr-2" />
                      Save & Continue
                    </Button>
                    <Button
                      variant="outline"
                      onClick={handleDiscardChanges}
                      className="flex-1"
                    >
                      <Trash2 className="h-4 w-4 mr-2" />
                      Discard
                    </Button>
                  </div>
                  <Button
                    variant="ghost"
                    onClick={() => setShowUnsavedDialog(false)}
                    className="w-full"
                  >
                    Cancel
                  </Button>
                </div>
              </DialogContent>
            </Dialog>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
