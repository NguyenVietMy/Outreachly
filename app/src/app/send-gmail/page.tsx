"use client";

import { useState, useEffect, useMemo } from "react";
import Image from "next/image";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Mail,
  CheckCircle,
  AlertCircle,
  Loader2,
  Users,
  FileText,
  Plus,
  X,
  Zap,
  BookOpen,
  MailCheck,
  XCircle,
  Eye,
} from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import { RecipientManager } from "@/components/email/RecipientManager";
import { RichTextEditor } from "@/components/email/RichTextEditor";
import { convertToHtmlEmail } from "@/lib/emailConverter";
import { API_BASE_URL } from "@/lib/config";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { useLeads, Lead } from "@/hooks/useLeads";
import TemplateBrowserModal from "@/components/templates/TemplateBrowserModal";
import ClickTrackerToggle from "@/components/email/ClickTrackerToggle";
import { useClickTracking } from "@/hooks/useClickTracking";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface GmailFormData {
  recipients: string[];
  subject: string;
  content: string;
  templateId?: string;
  campaignId?: string;
  scheduledAt?: string;
  priority: "low" | "normal" | "high";
}

interface BulkEmailResult {
  success: boolean;
  message: string;
  totalRecipients: number;
  successfulSends: number;
  failedSends: number;
  results?: Array<{
    email: string;
    success: boolean;
    message: string;
  }>;
}

interface GmailResponse {
  success: boolean;
  message: string;
  to?: string;
  subject?: string;
  provider?: string;
}

interface GmailStatus {
  hasGmailAccess: boolean;
  provider: string;
  user: string;
  message: string;
}

interface EmailTemplate {
  id: string;
  name: string;
  subject?: string;
  content?: string;
  category?: string;
  isHtml?: boolean;
  platform: string;
  contentJson: string;
}

export default function SendGmailPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const { toast } = useToast();
  const [isSending, setIsSending] = useState(false);
  const [showTemplates, setShowTemplates] = useState(false);
  const [gmailStatus, setGmailStatus] = useState<GmailStatus | null>(null);
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [selectedLeads, setSelectedLeads] = useState<Lead[]>([]);
  const [showLeadSelection, setShowLeadSelection] = useState(false);

  const { leads, loading: leadsLoading } = useLeads();

  const [formData, setFormData] = useState<GmailFormData>({
    recipients: [],
    subject: "",
    content: "",
    priority: "normal",
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [lastResponse, setLastResponse] = useState<GmailResponse | null>(null);
  const [lastBulkResponse, setLastBulkResponse] =
    useState<BulkEmailResult | null>(null);
  const [showGmailModal1, setShowGmailModal1] = useState(false);
  const [showGmailModal2, setShowGmailModal2] = useState(false);

  const messageId = useMemo(
    () => `gmail_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    []
  );
  const userId = user?.id?.toString() || "user_123";
  const campaignId = formData.campaignId;
  const orgId = user?.orgId?.toString();

  const {
    isTrackingEnabled,
    detectedUrls,
    processedContent,
    toggleTracking,
    hasUrls,
  } = useClickTracking({
    emailContent: formData.content,
    messageId,
    userId,
    campaignId,
    orgId,
    recipients: formData.recipients,
  });

  const API_URL = API_BASE_URL;

  const connectGmail = () => {
    // Kick off incremental consent flow for Gmail send scope
    window.location.href = `${API_URL}/oauth2/authorization/google-gmail`;
  };

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/auth");
    }
  }, [user, authLoading, router]);

  // Load Gmail status and templates
  useEffect(() => {
    const loadGmailInfo = async () => {
      try {
        // Check Gmail API status
        const statusResponse = await fetch(`${API_URL}/api/gmail/status`, {
          credentials: "include",
        });
        if (statusResponse.ok) {
          const statusData = await statusResponse.json();
          setGmailStatus(statusData);
        }

        // Load templates
        const templatesResponse = await fetch(
          `${API_URL}/api/templates?platform=EMAIL`,
          {
            credentials: "include",
          }
        );
        if (templatesResponse.ok) {
          const templatesData = await templatesResponse.json();
          setTemplates(templatesData);
        }
      } catch (error) {
        console.error("Failed to load Gmail info:", error);
      }
    };

    if (user) {
      loadGmailInfo();
    }
  }, [user, API_URL]);

  const validateEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    // Validate recipients
    const validRecipients = formData.recipients.filter(
      (email) => email.trim() !== ""
    );
    if (validRecipients.length === 0) {
      newErrors.recipients = "At least one recipient is required";
    } else {
      const invalidEmails = validRecipients.filter(
        (email) => !validateEmail(email)
      );
      if (invalidEmails.length > 0) {
        newErrors.recipients = `Invalid email addresses: ${invalidEmails.join(", ")}`;
      }
    }

    // Validate subject
    if (!formData.subject?.trim()) {
      newErrors.subject = "Subject is required";
    } else if (formData.subject.length > 200) {
      newErrors.subject = "Subject must be less than 200 characters";
    }

    // Validate content
    if (!formData.content?.trim()) {
      newErrors.content = "Content is required";
    } else if (formData.content.length > 10000) {
      newErrors.content = "Content must be less than 10,000 characters";
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const loadTemplate = (template: EmailTemplate) => {
    try {
      const contentData = JSON.parse(template.contentJson || "{}");
      const subject = contentData.subject || "";
      const content = contentData.body || "";
      const isHtml = contentData.isHtml || false;

      setFormData((prev) => ({
        ...prev,
        subject: subject,
        content: content,
        isHtml: isHtml,
        templateId: template.id,
      }));
      setShowTemplates(false);

      toast({
        title: "Template Loaded",
        description: `Loaded template: ${template.name}`,
      });
    } catch (error) {
      console.error("Error parsing template content:", error);
      toast({
        title: "Template Error",
        description: "Failed to load template content",
        variant: "destructive",
      });
    }
  };

  const clearTemplate = () => {
    setFormData((prev) => ({
      ...prev,
      templateId: undefined,
    }));
  };

  // Lead selection functions
  const addLeadToRecipients = (lead: Lead) => {
    if (!formData.recipients.includes(lead.email)) {
      setFormData((prev) => ({
        ...prev,
        recipients: [...prev.recipients, lead.email],
      }));
      setSelectedLeads((prev) => [...prev, lead]);
    }
  };

  const removeLeadFromRecipients = (lead: Lead) => {
    setFormData((prev) => ({
      ...prev,
      recipients: prev.recipients.filter((email) => email !== lead.email),
    }));
    setSelectedLeads((prev) => prev.filter((l) => l.id !== lead.id));
  };

  const addAllLeadsToRecipients = () => {
    const newRecipients = leads
      .filter((lead) => !formData.recipients.includes(lead.email))
      .map((lead) => lead.email);

    const newSelectedLeads = leads.filter(
      (lead) => !formData.recipients.includes(lead.email)
    );

    setFormData((prev) => ({
      ...prev,
      recipients: [...prev.recipients, ...newRecipients],
    }));
    setSelectedLeads((prev) => [...prev, ...newSelectedLeads]);
  };

  const clearAllLeads = () => {
    setFormData((prev) => ({
      ...prev,
      recipients: [],
    }));
    setSelectedLeads([]);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      toast({
        title: "Validation Error",
        description: "Please fix the errors before sending",
        variant: "destructive",
      });
      return;
    }

    if (!gmailStatus?.hasGmailAccess) {
      toast({
        title: "Gmail Access Required",
        description: "Please re-authenticate with Google to access Gmail API",
        variant: "destructive",
      });
      return;
    }

    const validRecipients = formData.recipients.filter(
      (email) => email.trim() !== ""
    );

    // Automatically choose between single and bulk sending
    if (validRecipients.length > 1) {
      // Use bulk sending for multiple recipients
      await sendBulkEmails();
    } else {
      // Use single sending for one recipient
      await sendSingleEmail(validRecipients[0]);
    }
  };

  const sendSingleEmail = async (recipient: string) => {
    setIsSending(true);
    setLastResponse(null);
    setLastBulkResponse(null);

    try {
      const contentToSend = isTrackingEnabled
        ? processedContent
        : formData.content;

      const { htmlContent } = convertToHtmlEmail(contentToSend);

      const gmailRequest = {
        to: recipient,
        subject: (formData.subject || "").trim(),
        body: htmlContent,
        html: true, // Always send as HTML now
        from: undefined,
      };

      const response = await fetch(`${API_URL}/api/gmail/send`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify(gmailRequest),
      });

      const result: GmailResponse = await response.json();
      setLastResponse(result);

      if (result.success) {
        toast({
          title: "Email Sent Successfully",
          description: `Email sent to ${recipient} via Gmail API`,
        });

        // Reset form
        setFormData((prev) => ({
          ...prev,
          subject: "",
          content: "",
          recipients: [],
        }));
        setSelectedLeads([]);
      } else {
        toast({
          title: "Email Failed",
          description: result.message || "Failed to send email",
          variant: "destructive",
        });
      }
    } catch (error) {
      console.error("Error sending email via Gmail API:", error);
      toast({
        title: "Error",
        description: "Failed to send email via Gmail API. Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsSending(false);
    }
  };

  const testGmailConnection = async () => {
    try {
      const response = await fetch(`${API_URL}/api/gmail/test`, {
        credentials: "include",
      });
      const data = await response.json();

      if (data.success) {
        toast({
          title: "Gmail API Test Successful",
          description: `Test email sent to ${data.testEmail}`,
        });
      } else {
        toast({
          title: "Gmail API Test Failed",
          description: data.message,
          variant: "destructive",
        });
      }
    } catch (error) {
      console.error("Gmail API test failed:", error);
      toast({
        title: "Test Failed",
        description: "Failed to test Gmail API connection",
        variant: "destructive",
      });
    }
  };

  const sendBulkEmails = async () => {
    if (!user) {
      toast({
        title: "Authentication Required",
        description: "Please log in to send emails.",
        variant: "destructive",
      });
      return;
    }

    if (!gmailStatus?.hasGmailAccess) {
      toast({
        title: "Gmail Access Required",
        description: "Please connect your Gmail account first.",
        variant: "destructive",
      });
      return;
    }

    const validRecipients = formData.recipients.filter(
      (email) => email.trim() !== ""
    );

    if (validRecipients.length === 0) {
      toast({
        title: "No Recipients",
        description: "Please add at least one recipient email address.",
        variant: "destructive",
      });
      return;
    }

    if (!formData.subject?.trim()) {
      toast({
        title: "Subject Required",
        description: "Please enter an email subject.",
        variant: "destructive",
      });
      return;
    }

    if (!formData.content?.trim()) {
      toast({
        title: "Content Required",
        description: "Please enter email content.",
        variant: "destructive",
      });
      return;
    }

    setIsSending(true);
    setLastBulkResponse(null);

    try {
      const contentToSend = isTrackingEnabled
        ? processedContent
        : formData.content;

      const { htmlContent } = convertToHtmlEmail(contentToSend);

      const bulkRequest = {
        recipients: validRecipients,
        subject: formData.subject.trim(),
        body: htmlContent,
        html: true,
        from: undefined,
        campaignId: formData.campaignId,
        userId: user?.id?.toString(),
      };

      const response = await fetch(`${API_URL}/api/gmail/send-bulk`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify(bulkRequest),
      });

      const result: BulkEmailResult = await response.json();
      setLastBulkResponse(result);

      if (result.success) {
        toast({
          title: "Bulk Emails Sent",
          description: `Successfully sent ${result.successfulSends} of ${result.totalRecipients} emails.`,
        });

        // Reset form on successful bulk send
        setFormData((prev) => ({
          ...prev,
          subject: "",
          content: "",
          recipients: [],
        }));
        setSelectedLeads([]);
      } else {
        toast({
          title: "Bulk Send Failed",
          description: result.message || "Failed to send bulk emails.",
          variant: "destructive",
        });
      }
    } catch (error) {
      console.error("Bulk email sending error:", error);
      toast({
        title: "Error",
        description: "Failed to send bulk emails. Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsSending(false);
    }
  };

  if (authLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="min-h-screen bg-gradient-to-br from-slate-50 via-blue-50 to-indigo-50">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
            {/* Header */}
            <div className="mb-6 md:mb-8">
              <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
                <div>
                  <h1 className="text-2xl md:text-3xl font-bold text-gray-900 flex items-center gap-2">
                    <Mail className="h-8 w-8 text-red-500" />
                    Send Email via Gmail
                  </h1>
                  <p className="mt-2 text-base md:text-lg text-gray-600">
                    Send emails using your own Gmail account with OAuth2
                    authentication
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2 md:gap-3"></div>
              </div>
            </div>

            {/* Gmail Status Success */}
            {gmailStatus && gmailStatus.hasGmailAccess && (
              <Alert className="mb-6 border-green-200 bg-green-50">
                <CheckCircle className="h-4 w-4 text-green-600" />
                <AlertDescription className="text-green-800">
                  <div className="font-medium mb-2">Gmail API Connected</div>
                  <p className="text-sm">
                    You're connected to Gmail API and can send emails using your
                    Gmail account.
                  </p>
                </AlertDescription>
              </Alert>
            )}

            {/* Main Content */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 md:gap-6">
              {/* Email Form */}
              <div className="lg:col-span-2">
                <Card className="shadow-lg border-0">
                  <CardHeader className="bg-gradient-to-r from-red-50 to-orange-50 border-b">
                    <div className="flex items-center justify-between">
                      <div>
                        <CardTitle className="flex items-center gap-2 text-xl">
                          <Mail className="h-6 w-6 text-red-600" />
                          Compose Gmail
                        </CardTitle>
                        <CardDescription className="text-base">
                          Send emails using your Gmail account via Gmail API
                        </CardDescription>
                      </div>
                      {formData.templateId && (
                        <Badge
                          variant="secondary"
                          className="flex items-center gap-1"
                        >
                          <FileText className="h-3 w-3" />
                          Template Loaded
                        </Badge>
                      )}
                    </div>
                  </CardHeader>
                  <CardContent className="p-6">
                    <form onSubmit={handleSubmit} className="space-y-6">
                      {/* Lead Selection */}
                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <Label>Import Leads</Label>
                          <div className="flex gap-2">
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() =>
                                setShowLeadSelection(!showLeadSelection)
                              }
                              className="flex items-center gap-1"
                            >
                              <Users className="h-4 w-4" />
                              {showLeadSelection ? "Hide" : "Show"} Leads
                            </Button>
                            {leads.length > 0 && (
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={addAllLeadsToRecipients}
                                disabled={leadsLoading}
                              >
                                Add All ({leads.length})
                              </Button>
                            )}
                            {selectedLeads.length > 0 && (
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={clearAllLeads}
                              >
                                Clear All
                              </Button>
                            )}
                          </div>
                        </div>

                        {showLeadSelection && (
                          <div className="border rounded-lg p-4 bg-gray-50 max-h-60 overflow-y-auto">
                            {leadsLoading ? (
                              <div className="flex items-center justify-center py-4">
                                <Loader2 className="h-4 w-4 animate-spin mr-2" />
                                Loading leads...
                              </div>
                            ) : leads.length > 0 ? (
                              <div className="space-y-2">
                                {leads.map((lead) => {
                                  const isSelected = selectedLeads.some(
                                    (l) => l.id === lead.id
                                  );
                                  return (
                                    <div
                                      key={lead.id}
                                      className={`flex items-center justify-between p-2 rounded border ${
                                        isSelected
                                          ? "bg-blue-50 border-blue-200"
                                          : "bg-white border-gray-200"
                                      }`}
                                    >
                                      <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-2">
                                          <span className="font-medium text-sm truncate">
                                            {lead.firstName} {lead.lastName}
                                          </span>
                                          {lead.position && (
                                            <Badge
                                              variant="secondary"
                                              className="text-xs"
                                            >
                                              {lead.position}
                                            </Badge>
                                          )}
                                        </div>
                                        <div className="text-xs text-gray-500 truncate">
                                          {lead.email}
                                        </div>
                                        {lead.domain && (
                                          <div className="text-xs text-gray-400 truncate">
                                            {lead.domain}
                                          </div>
                                        )}
                                      </div>
                                      <Button
                                        type="button"
                                        variant={
                                          isSelected ? "destructive" : "default"
                                        }
                                        size="sm"
                                        onClick={() =>
                                          isSelected
                                            ? removeLeadFromRecipients(lead)
                                            : addLeadToRecipients(lead)
                                        }
                                        className="ml-2 h-8"
                                      >
                                        {isSelected ? "Remove" : "Add"}
                                      </Button>
                                    </div>
                                  );
                                })}
                              </div>
                            ) : (
                              <div className="text-center py-4 text-gray-500">
                                <Users className="h-8 w-8 mx-auto mb-2 text-gray-400" />
                                <p>No leads available</p>
                                <p className="text-sm">
                                  Import leads from the Leads page first
                                </p>
                              </div>
                            )}
                          </div>
                        )}

                        {selectedLeads.length > 0 && (
                          <div className="mt-2">
                            <div className="text-sm text-gray-600 mb-2">
                              Selected leads ({selectedLeads.length}):
                            </div>
                            <div className="flex flex-wrap gap-1">
                              {selectedLeads.map((lead) => (
                                <Badge
                                  key={lead.id}
                                  variant="secondary"
                                  className="flex items-center gap-1"
                                >
                                  {lead.firstName} {lead.lastName}
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    className="h-4 w-4 p-0 hover:bg-transparent"
                                    onClick={() =>
                                      removeLeadFromRecipients(lead)
                                    }
                                  >
                                    <X className="h-3 w-3" />
                                  </Button>
                                </Badge>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>

                      {/* Recipients */}
                      <RecipientManager
                        recipients={formData.recipients}
                        onRecipientsChange={(recipients) =>
                          setFormData((prev) => ({
                            ...prev,
                            recipients,
                          }))
                        }
                        errors={errors}
                        maxRecipients={50} // Gmail API has different limits
                      />

                      {/* Subject */}
                      <div className="space-y-2">
                        <Label
                          htmlFor="subject"
                          className="text-base font-medium"
                        >
                          Subject *
                        </Label>
                        <Input
                          id="subject"
                          value={formData.subject || ""}
                          onChange={(e) =>
                            setFormData((prev) => ({
                              ...prev,
                              subject: e.target.value,
                            }))
                          }
                          placeholder="Enter email subject"
                          className={`h-11 ${errors.subject ? "border-red-500" : ""}`}
                          maxLength={200}
                        />
                        <div className="flex justify-between text-xs text-gray-500">
                          <span>{errors.subject || ""}</span>
                          <span>{formData.subject?.length || 0}/200</span>
                        </div>
                      </div>

                      {/* Content */}
                      <RichTextEditor
                        value={formData.content || ""}
                        onChange={(content) =>
                          setFormData((prev) => ({
                            ...prev,
                            content,
                          }))
                        }
                        placeholder="Enter your email content... Use **bold**, *italic*, • lists, and [links](url)"
                        maxLength={10000}
                        error={errors.content}
                        onBrowseTemplates={() =>
                          setShowTemplates(!showTemplates)
                        }
                      />

                      {/* Click Tracking Toggle */}
                      <ClickTrackerToggle
                        isEnabled={isTrackingEnabled}
                        onToggle={toggleTracking}
                        detectedUrls={detectedUrls}
                      />

                      {/* Advanced Options */}
                      <div className="space-y-4 p-4 bg-gray-50 rounded-lg">
                        <h4 className="font-medium text-sm text-gray-700">
                          Advanced Options (Coming soon)
                        </h4>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <div className="space-y-2">
                            <Label htmlFor="priority">Priority</Label>
                            <Select
                              value={formData.priority}
                              onValueChange={(
                                value: "low" | "normal" | "high"
                              ) =>
                                setFormData((prev) => ({
                                  ...prev,
                                  priority: value,
                                }))
                              }
                              disabled={true}
                            >
                              <SelectTrigger>
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="low">Low</SelectItem>
                                <SelectItem value="normal">Normal</SelectItem>
                                <SelectItem value="high">High</SelectItem>
                              </SelectContent>
                            </Select>
                          </div>
                          <div className="space-y-2">
                            <Label htmlFor="scheduledAt">
                              Schedule Send (Optional)
                            </Label>
                            <Input
                              id="scheduledAt"
                              type="datetime-local"
                              value={formData.scheduledAt || ""}
                              onChange={(e) =>
                                setFormData((prev) => ({
                                  ...prev,
                                  scheduledAt: e.target.value,
                                }))
                              }
                              disabled={true}
                            />
                          </div>
                        </div>
                      </div>

                      {/* Smart Send Button */}
                      <Button
                        type="submit"
                        disabled={isSending || !gmailStatus?.hasGmailAccess}
                        className="w-full h-12 text-lg font-medium bg-gradient-to-r from-red-600 to-orange-600 hover:from-red-700 hover:to-orange-700"
                      >
                        {isSending ? (
                          <>
                            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                            {formData.recipients.length > 1
                              ? "Sending Bulk Emails..."
                              : "Sending via Gmail..."}
                          </>
                        ) : (
                          <>
                            {formData.recipients.length > 1 ? (
                              <Zap className="mr-2 h-5 w-5" />
                            ) : (
                              <Mail className="mr-2 h-5 w-5" />
                            )}
                            {selectedLeads.length > 0
                              ? formData.scheduledAt
                                ? `Schedule ${selectedLeads.length} Emails via Gmail`
                                : `Send ${selectedLeads.length} Emails via Gmail`
                              : formData.scheduledAt
                                ? "Schedule Email via Gmail"
                                : formData.recipients.length > 1
                                  ? `Send Bulk (${formData.recipients.length} emails)`
                                  : "Send Email via Gmail"}
                          </>
                        )}
                      </Button>

                      {/* Sample Template Button */}
                      <Button
                        type="button"
                        variant="outline"
                        disabled={isSending || !gmailStatus?.hasGmailAccess}
                        className="w-full"
                        onClick={() => {
                          setFormData((prev) => ({
                            ...prev,
                            content: `Hi {{firstName}},

Thank you for choosing our Pickleball Vatic Pro! We appreciate your business and hope you enjoy using our product.

If you have any questions or need assistance, feel free to reach out. We're here to help!

Best regards,
[Your Name]

P.S. Don't forget to check out our latest accessories for an enhanced playing experience!`,
                          }));
                        }}
                      >
                        📧 Load Sample Template
                      </Button>

                      {!gmailStatus?.hasGmailAccess && (
                        <Alert className="border-orange-200 bg-orange-50">
                          <AlertCircle className="h-4 w-4 text-orange-600" />
                          <AlertDescription className="text-orange-800">
                            <div className="flex items-center justify-between">
                              <span>
                                Gmail API access required. Click to grant
                                permissions.
                              </span>
                              <Button
                                type="button"
                                size="sm"
                                onClick={() => setShowGmailModal1(true)}
                              >
                                Connect Gmail
                              </Button>
                            </div>
                          </AlertDescription>
                        </Alert>
                      )}
                    </form>
                  </CardContent>
                </Card>
              </div>

              {/* Status Panel */}
              <div className="space-y-4 md:space-y-6">
                {/* Gmail Status */}
                <Card className="shadow-lg border-0">
                  <CardHeader className="pb-3 bg-gradient-to-r from-red-50 to-orange-50">
                    <CardTitle className="text-sm font-medium flex items-center gap-2">
                      <Mail className="h-4 w-4 text-red-600" />
                      Gmail API Status
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="pt-4">
                    <div className="space-y-3">
                      <div className="flex justify-between items-center">
                        <span className="text-sm font-medium">Status:</span>
                        <Badge
                          variant={
                            gmailStatus?.hasGmailAccess
                              ? "default"
                              : "destructive"
                          }
                          className="text-sm"
                        >
                          {gmailStatus?.hasGmailAccess
                            ? "Connected"
                            : "Not Connected"}
                        </Badge>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-sm font-medium">Provider:</span>
                        <span className="text-sm text-gray-600">Gmail API</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-sm font-medium">Account:</span>
                        <span className="text-sm text-gray-600 truncate">
                          {gmailStatus?.user || "Unknown"}
                        </span>
                      </div>

                      {!gmailStatus?.hasGmailAccess && (
                        <div className="mt-3 p-3 bg-orange-50 border border-orange-200 rounded-lg">
                          <div className="flex items-center gap-2 mb-2">
                            <AlertCircle className="h-4 w-4 text-orange-600" />
                            <span className="text-sm font-medium text-orange-800">
                              Gmail Access Required
                            </span>
                          </div>
                          <p className="text-xs text-orange-700 mb-3">
                            Clicking connect opens Google's consent screen to
                            allow sending email via your Gmail account. This is
                            a standard Google prompt.
                          </p>
                          <Button
                            type="button"
                            size="sm"
                            onClick={() => setShowGmailModal2(true)}
                          >
                            Connect Gmail
                          </Button>
                        </div>
                      )}
                    </div>
                  </CardContent>
                </Card>

                {/* Last Response */}
                {lastResponse && (
                  <Card className="shadow-lg border-0">
                    <CardHeader className="pb-3 bg-gradient-to-r from-blue-50 to-indigo-50">
                      <CardTitle className="text-sm font-medium flex items-center gap-2">
                        {lastResponse.success ? (
                          <CheckCircle className="h-4 w-4 text-green-600" />
                        ) : (
                          <AlertCircle className="h-4 w-4 text-red-600" />
                        )}
                        Last Send Result
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="pt-4">
                      <div className="space-y-3 text-sm">
                        <div className="flex justify-between">
                          <span>Status:</span>
                          <Badge
                            variant={
                              lastResponse.success ? "default" : "destructive"
                            }
                          >
                            {lastResponse.success ? "Success" : "Failed"}
                          </Badge>
                        </div>
                        <div className="flex justify-between">
                          <span>Provider:</span>
                          <span className="font-medium">
                            {lastResponse.provider || "Gmail API"}
                          </span>
                        </div>
                        {lastResponse.to && (
                          <div className="flex justify-between">
                            <span>To:</span>
                            <span className="font-medium truncate">
                              {lastResponse.to}
                            </span>
                          </div>
                        )}
                        {lastResponse.subject && (
                          <div className="flex justify-between">
                            <span>Subject:</span>
                            <span className="font-medium truncate">
                              {lastResponse.subject}
                            </span>
                          </div>
                        )}
                      </div>
                    </CardContent>
                  </Card>
                )}

                {/* Quick Actions */}
                <Card className="shadow-lg border-0">
                  <CardHeader className="pb-3 bg-gradient-to-r from-purple-50 to-pink-50">
                    <CardTitle className="text-sm font-medium flex items-center gap-2">
                      <Zap className="h-4 w-4 text-purple-600" />
                      Quick Actions
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="pt-4">
                    <div className="space-y-2">
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            className="w-full justify-start"
                          >
                            <MailCheck className="h-4 w-4 mr-2" />
                            Test Gmail Connection
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>
                              Send test email?
                            </AlertDialogTitle>
                            <AlertDialogDescription>
                              Confirm sending a test email to address: "
                              {gmailStatus?.user || "your Gmail account"}"
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>Cancel</AlertDialogCancel>
                            <AlertDialogAction onClick={testGmailConnection}>
                              Send test email
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="w-full justify-start"
                        onClick={() => router.push("/send-email")}
                      >
                        <Mail className="h-4 w-4 mr-2" />
                        Use AWS SES email service (Coming soon)
                      </Button>
                    </div>
                  </CardContent>
                </Card>

                {/* Gmail Tips */}
                <Card className="shadow-lg border-0">
                  <CardHeader className="pb-3 bg-gradient-to-r from-orange-50 to-yellow-50">
                    <CardTitle className="text-sm font-medium flex items-center gap-2">
                      <Mail className="h-4 w-4 text-orange-600" />
                      Gmail API Tips
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="pt-4">
                    <ul className="space-y-2 text-xs text-gray-600">
                      <li className="flex items-start gap-2">
                        <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                        Uses your own Gmail account
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                        OAuth2 secure authentication
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                        No daily sending limits
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                        Emails appear in your Sent folder
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                        Full Gmail features available
                      </li>
                    </ul>
                  </CardContent>
                </Card>
              </div>
            </div>
          </div>
        </div>

        {/* Bulk Email Results */}
        {lastBulkResponse && (
          <div className="mt-6 p-6 bg-white rounded-lg shadow-sm border">
            <h3 className="text-lg font-semibold mb-4 flex items-center gap-2">
              <MailCheck className="h-5 w-5 text-blue-600" />
              Bulk Email Results
            </h3>

            <div className="grid grid-cols-3 gap-4 mb-4">
              <div className="text-center p-3 bg-blue-50 rounded-lg">
                <div className="text-2xl font-bold text-blue-600">
                  {lastBulkResponse.totalRecipients || 0}
                </div>
                <div className="text-sm text-gray-600">Total Recipients</div>
              </div>
              <div className="text-center p-3 bg-green-50 rounded-lg">
                <div className="text-2xl font-bold text-green-600">
                  {lastBulkResponse.successfulSends || 0}
                </div>
                <div className="text-sm text-gray-600">Successful</div>
              </div>
              <div className="text-center p-3 bg-red-50 rounded-lg">
                <div className="text-2xl font-bold text-red-600">
                  {lastBulkResponse.failedSends || 0}
                </div>
                <div className="text-sm text-gray-600">Failed</div>
              </div>
            </div>

            {lastBulkResponse.results &&
              lastBulkResponse.results.length > 0 && (
                <div className="space-y-2 max-h-60 overflow-y-auto">
                  <h4 className="font-medium text-gray-700">
                    Individual Results:
                  </h4>
                  {lastBulkResponse.results.map((result, index) => (
                    <div
                      key={index}
                      className="flex items-center justify-between p-2 bg-gray-50 rounded"
                    >
                      <span className="text-sm font-medium">
                        {result.email}
                      </span>
                      <div className="flex items-center gap-2">
                        {result.success ? (
                          <Badge
                            variant="default"
                            className="bg-green-100 text-green-800"
                          >
                            <CheckCircle className="h-3 w-3 mr-1" />
                            Success
                          </Badge>
                        ) : (
                          <Badge variant="destructive">
                            <XCircle className="h-3 w-3 mr-1" />
                            Failed
                          </Badge>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
          </div>
        )}

        <TemplateBrowserModal
          open={showTemplates}
          onOpenChange={setShowTemplates}
          platform="EMAIL"
          selectedTemplateId={formData.templateId || null}
          onSelect={(t: any) =>
            setFormData((prev) => ({
              ...prev,
              templateId: t.id,
            }))
          }
          onUse={(t: any) => loadTemplate(t as unknown as EmailTemplate)}
        />

        {/* Gmail Connect Modals - Using Dialog for better click-outside behavior */}
        <Dialog open={showGmailModal1} onOpenChange={setShowGmailModal1}>
          <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto p-0">
            <div className="p-6 space-y-4">
              <DialogHeader>
                <DialogTitle>Connect your Gmail account</DialogTitle>
                <DialogDescription>
                  You'll be redirected to Google to grant permission to send
                  email from your Gmail account. This consent screen is expected
                  for apps requesting Gmail send access. We never see your
                  password, and you can revoke access anytime in your Google
                  Account settings.
                </DialogDescription>
                <div className="bg-gray-100 border border-gray-300 rounded-lg p-3 mt-3">
                  <p className="text-sm text-gray-700 italic">
                    <strong>Note:</strong> Due to Google's strict verification
                    requirements, I'm actively working on app verification. Any
                    "unverified app" warnings can be safely ignored by following
                    the step-by-step instructions below.
                  </p>
                </div>
              </DialogHeader>
              {/* Screenshots */}
              <div className="space-y-3">
                <Image
                  src="/connect-gmail/Step1.png"
                  alt="Step 1: Open Google consent"
                  width={1200}
                  height={800}
                  className="rounded border"
                />
                <Image
                  src="/connect-gmail/Step2.png"
                  alt="Step 2: Grant gmail.send permission"
                  width={1200}
                  height={800}
                  className="rounded border"
                />
                <Image
                  src="/connect-gmail/Step3.png"
                  alt="Step 3: Redirect back to Outreachly"
                  width={1200}
                  height={800}
                  className="rounded border"
                />
              </div>
              <DialogFooter className="p-6 pt-0">
                <Button
                  variant="outline"
                  onClick={() => setShowGmailModal1(false)}
                >
                  Cancel
                </Button>
                <Button onClick={connectGmail}>Continue to Google</Button>
              </DialogFooter>
            </div>
          </DialogContent>
        </Dialog>

        <Dialog open={showGmailModal2} onOpenChange={setShowGmailModal2}>
          <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto p-0">
            <div className="p-6 space-y-4">
              <DialogHeader>
                <DialogTitle>About the Google consent screen</DialogTitle>
                <DialogDescription>
                  Google will ask you to grant the <code>gmail.send</code>{" "}
                  permission so Outreachly can send the messages you compose.
                  You can disconnect anytime in your Google Account.
                </DialogDescription>
                <div className="bg-gray-100 border border-gray-300 rounded-lg p-3 mt-3">
                  <p className="text-sm text-gray-700 italic">
                    <strong>Note:</strong> Due to Google's strict verification
                    requirements, I am actively working on app verification. Any
                    "unverified app" warnings can be safely ignored by following
                    the step-by-step instructions below.
                  </p>
                </div>
              </DialogHeader>
              <div className="space-y-3">
                <Image
                  src="/connect-gmail/Step1.png"
                  alt="Step 1: Open Google consent"
                  width={1200}
                  height={800}
                  className="rounded border"
                />
                <Image
                  src="/connect-gmail/Step2.png"
                  alt="Step 2: Grant gmail.send permission"
                  width={1200}
                  height={800}
                  className="rounded border"
                />
                <Image
                  src="/connect-gmail/Step3.png"
                  alt="Step 3: Redirect back to Outreachly"
                  width={1200}
                  height={800}
                  className="rounded border"
                />
              </div>
              <DialogFooter className="p-6 pt-0">
                <Button
                  variant="outline"
                  onClick={() => setShowGmailModal2(false)}
                >
                  Cancel
                </Button>
                <Button onClick={connectGmail}>Continue to Google</Button>
              </DialogFooter>
            </div>
          </DialogContent>
        </Dialog>
      </DashboardLayout>
    </AuthGuard>
  );
}
