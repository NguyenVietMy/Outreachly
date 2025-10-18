"use client";

import { useState, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Switch } from "@/components/ui/switch";
import { Progress } from "@/components/ui/progress";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Mail,
  Send,
  Shield,
  Clock,
  CheckCircle,
  AlertCircle,
  Loader2,
  Eye,
  EyeOff,
  Lock,
  Users,
  FileText,
  Calendar,
  BarChart3,
  Plus,
  X,
  Search,
  Filter,
  Download,
  Upload,
  Settings,
  Zap,
  Target,
  TrendingUp,
  MessageSquare,
  History,
  Star,
  BookOpen,
  Sparkles,
} from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import { RecipientManager } from "@/components/email/RecipientManager";
import { RichTextEditor } from "@/components/email/RichTextEditor";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { useLeads, Lead } from "@/hooks/useLeads";
import { EmailValidationModal } from "@/components/email/EmailValidationModal";
import {
  extractVariablesFromEmail,
  validateLeads,
  ValidationResponse,
} from "@/lib/emailValidation";
import { useClickTracking } from "@/hooks/useClickTracking";
import { convertToHtmlEmail } from "@/lib/emailConverter";

interface EmailFormData {
  recipients: string[];
  subject: string;
  content: string;
  isHtml: boolean;
  templateId?: string;
  campaignId?: string;
}

interface EmailResponse {
  messageId: string;
  success: boolean;
  message: string;
  timestamp: string;
  totalRecipients: number;
  successfulRecipients: number;
  failedRecipients: string[];
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

interface EmailStats {
  sentToday: number;
  sentThisWeek: number;
  sentThisMonth: number;
  openRate: number;
  clickRate: number;
  replyRate: number;
}

interface ResendResponse {
  success: boolean;
  message: string;
  messageId?: string;
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

export default function SendEmailPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [showSecurityInfo, setShowSecurityInfo] = useState(false);
  const [activeTab, setActiveTab] = useState("compose");
  const [showPreview, setShowPreview] = useState(false);
  const [showTemplates, setShowTemplates] = useState(false);
  const [showHistory, setShowHistory] = useState(false);

  const [rateLimitInfo, setRateLimitInfo] = useState({
    remaining: 100,
    resetTime: null,
  });
  const [verificationStatus, setVerificationStatus] = useState({
    sandboxMode: false,
    message: "",
    instructions: "",
  });
  const [emailStats, setEmailStats] = useState<EmailStats>({
    sentToday: 0,
    sentThisWeek: 0,
    sentThisMonth: 0,
    openRate: 0,
    clickRate: 0,
    replyRate: 0,
  });
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [emailHistory, setEmailHistory] = useState<EmailResponse[]>([]);
  const [selectedLeads, setSelectedLeads] = useState<Lead[]>([]);
  const [showLeadSelection, setShowLeadSelection] = useState(false);
  const [showValidationModal, setShowValidationModal] = useState(false);
  const [validationResults, setValidationResults] =
    useState<ValidationResponse | null>(null);
  const [lastBulkResponse, setLastBulkResponse] =
    useState<BulkEmailResult | null>(null);

  const { leads, loading: leadsLoading } = useLeads();

  const [formData, setFormData] = useState<EmailFormData>({
    recipients: [],
    subject: "",
    content: "",
    isHtml: true,
  });

  // Click tracking
  const { isTrackingEnabled, processedContent } = useClickTracking({
    emailContent: formData.content,
    messageId: "",
    userId: user?.id?.toString() || "",
    campaignId: formData.campaignId,
    recipients: formData.recipients,
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [lastResponse, setLastResponse] = useState<EmailResponse | null>(null);

  const API_URL =
    process.env.NEXT_PUBLIC_API_URL || "https://api.outreach-ly.com";

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/auth");
    }
  }, [user, authLoading, router]);

  // Load email info, templates, and history
  useEffect(() => {
    const loadEmailInfo = async () => {
      try {
        // Load rate limit
        const rateLimitResponse = await fetch(
          `${API_URL}/api/email/rate-limit`,
          {
            credentials: "include",
          }
        );
        if (rateLimitResponse.ok) {
          const rateLimitData = await rateLimitResponse.json();
          setRateLimitInfo(rateLimitData);
        }

        // Load verification status
        const verificationResponse = await fetch(
          `${API_URL}/api/email/verification-status`,
          {
            credentials: "include",
          }
        );
        if (verificationResponse.ok) {
          const verificationData = await verificationResponse.json();
          setVerificationStatus(verificationData);
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
        } else {
          console.error(
            "Failed to load templates:",
            templatesResponse.status,
            templatesResponse.statusText
          );
        }

        // Load email history
        const historyResponse = await fetch(`${API_URL}/api/email/history`, {
          credentials: "include",
        });
        if (historyResponse.ok) {
          const historyData = await historyResponse.json();
          setEmailHistory(historyData);
        }

        // Load email stats
        const statsResponse = await fetch(`${API_URL}/api/email/stats`, {
          credentials: "include",
        });
        if (statsResponse.ok) {
          const statsData = await statsResponse.json();
          setEmailStats(statsData);
        }
      } catch (error) {
        console.error("Failed to load email info:", error);
      }
    };

    if (user) {
      loadEmailInfo();
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

  const addRecipient = () => {
    setFormData((prev) => ({
      ...prev,
      recipients: [...prev.recipients, ""],
    }));
  };

  const removeRecipient = (index: number) => {
    if (formData.recipients.length > 1) {
      setFormData((prev) => ({
        ...prev,
        recipients: prev.recipients.filter((_, i) => i !== index),
      }));
    }
  };

  const updateRecipient = (index: number, value: string) => {
    setFormData((prev) => ({
      ...prev,
      recipients: prev.recipients.map((email, i) =>
        i === index ? value : email
      ),
    }));
  };

  const loadTemplate = (template: EmailTemplate) => {
    try {
      // Parse the JSON content to extract subject and body
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

  // Helper function to convert LeadValidationResult to Lead format for personalization
  const convertValidationResultToLead = (validationResult: any): Lead => {
    return {
      id: validationResult.leadId,
      firstName: validationResult.firstName || "",
      lastName: validationResult.lastName || "",
      domain: validationResult.companyName || "",
      email: validationResult.email,
      phone: "",
      position: "",
      positionRaw: "",
      seniority: "",
      department: "",
      linkedinUrl: "",
      twitter: "",
      confidenceScore: 0,
      emailType: "unknown",
      customTextField: "",
      source: "",
      verifiedStatus: "unknown",
      enrichedJson: "",
      enrichmentHistory: "",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      orgId: user?.orgId || "",
      listId: "",
      campaigns: [],
    };
  };

  // Validation modal handlers
  const handleProceedWithAll = async () => {
    console.log("handleProceedWithAll called");
    setShowValidationModal(false);

    if (!validationResults) {
      console.log("No validation results found");
      return;
    }

    console.log("Validation results:", validationResults);
    setIsSending(true);
    setLastResponse(null);
    setLastBulkResponse(null);

    try {
      // Send personalized emails to all leads (both valid and invalid)
      const allLeads = [
        ...validationResults.validLeads,
        ...validationResults.invalidLeads,
      ];

      if (allLeads.length === 0) {
        console.log("No leads found to send emails to");
        toast({
          title: "No Leads",
          description: "No leads found to send emails to.",
          variant: "destructive",
        });
        return;
      }

      console.log("Sending emails to", allLeads.length, "leads");

      // Send personalized emails to each lead
      const personalizedEmails = allLeads.map((lead) => {
        // Apply personalization using the lead data
        const leadForPersonalization = convertValidationResultToLead(lead);

        // Personalize subject and content for THIS specific lead
        const personalizedSubject = substituteTemplateVariables(
          formData.subject,
          leadForPersonalization
        );
        const personalizedContent = substituteTemplateVariables(
          formData.content,
          leadForPersonalization
        );

        // Apply personalization to the content that will be sent
        const contentToSend = isTrackingEnabled
          ? substituteTemplateVariables(
              processedContent,
              leadForPersonalization
            )
          : personalizedContent;

        const { htmlContent } = convertToHtmlEmail(contentToSend);

        return {
          subject: personalizedSubject.trim(),
          content: htmlContent,
          recipients: [lead.email],
          isHtml: true,
          replyTo: null,
          campaignId: formData.campaignId,
        };
      });

      // Send each personalized email using Email API
      console.log("Starting to send emails via Email API");
      const emailPromises = personalizedEmails.map(async (emailRequest) => {
        console.log("Sending email to:", emailRequest.recipients[0]);
        const response = await fetch(`${API_URL}/api/email/send`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify(emailRequest),
        });
        const result = await response.json();
        console.log("Email send result:", result);
        return result;
      });

      console.log("Waiting for all emails to complete...");
      const results = await Promise.all(emailPromises);
      console.log("All email results:", results);
      const successfulCount = results.filter((r) => r.success).length;
      const failedCount = results.length - successfulCount;

      toast({
        title: "Personalized Emails Sent",
        description: `Sent ${successfulCount} personalized emails, ${failedCount} failed`,
      });

      // Reset form
      setFormData((prev) => ({
        ...prev,
        subject: "",
        content: "",
        recipients: [],
      }));
      setSelectedLeads([]);
    } catch (error) {
      console.error("Error sending personalized emails:", error);
      toast({
        title: "Error",
        description: "Failed to send personalized emails. Please try again.",
        variant: "destructive",
      });
    } finally {
      console.log("Email sending process completed");
      setIsSending(false);
    }
  };

  const handleSkipInvalid = async () => {
    if (!validationResults) return;

    setShowValidationModal(false);

    setIsSending(true);
    setLastResponse(null);
    setLastBulkResponse(null);

    try {
      // Send personalized emails only to valid leads
      const validLeads = validationResults.validLeads;

      if (validLeads.length === 0) {
        toast({
          title: "No Valid Leads",
          description: "No valid leads found to send emails to.",
          variant: "destructive",
        });
        return;
      }

      // Send personalized emails to each valid lead
      const personalizedEmails = validLeads.map((lead) => {
        // Apply personalization using the lead data
        const leadForPersonalization = convertValidationResultToLead(lead);

        // Personalize subject and content for THIS specific lead
        const personalizedSubject = substituteTemplateVariables(
          formData.subject,
          leadForPersonalization
        );
        const personalizedContent = substituteTemplateVariables(
          formData.content,
          leadForPersonalization
        );

        // Apply personalization to the content that will be sent
        const contentToSend = isTrackingEnabled
          ? substituteTemplateVariables(
              processedContent,
              leadForPersonalization
            )
          : personalizedContent;

        const { htmlContent } = convertToHtmlEmail(contentToSend);

        return {
          subject: personalizedSubject.trim(),
          content: htmlContent,
          recipients: [lead.email],
          isHtml: true,
          replyTo: null,
          campaignId: formData.campaignId,
        };
      });

      // Send each personalized email using Email API
      console.log("Starting to send emails via Email API");
      const emailPromises = personalizedEmails.map(async (emailRequest) => {
        const response = await fetch(`${API_URL}/api/email/send`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify(emailRequest),
        });
        return response.json();
      });

      const results = await Promise.all(emailPromises);
      const successfulCount = results.filter((r) => r.success).length;
      const failedCount = results.length - successfulCount;

      toast({
        title: "Valid Emails Sent",
        description: `Sent ${successfulCount} personalized emails to valid leads, ${failedCount} failed`,
      });

      // Reset form
      setFormData((prev) => ({
        ...prev,
        subject: "",
        content: "",
        recipients: [],
      }));
      setSelectedLeads([]);
    } catch (error) {
      console.error("Error sending emails to valid leads:", error);
      toast({
        title: "Error",
        description: "Failed to send emails to valid leads. Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsSending(false);
    }
  };

  const handleCancelValidation = () => {
    setShowValidationModal(false);
    setValidationResults(null);
  };

  const sendSingleEmail = async (recipient: string) => {
    setIsSending(true);
    setLastResponse(null);
    setLastBulkResponse(null);

    try {
      let personalizedSubject = formData.subject;
      let personalizedContent = formData.content;

      // Find the lead for personalization
      const lead =
        selectedLeads.find((l) => l.email === recipient) ||
        leads.find((l) => l.email === recipient);

      if (lead) {
        // If there's a template, use template personalization
        if (formData.templateId) {
          const template = templates.find((t) => t.id === formData.templateId);
          if (template) {
            const personalized = getPersonalizedContent(template, lead);
            personalizedSubject = personalized.subject;
            personalizedContent = personalized.content;
          }
        } else {
          // Use direct variable substitution
          personalizedSubject = substituteTemplateVariables(
            formData.subject,
            lead
          );
          personalizedContent = substituteTemplateVariables(
            formData.content,
            lead
          );
        }
      }

      const contentToSend = isTrackingEnabled
        ? processedContent
        : personalizedContent;

      const { htmlContent } = convertToHtmlEmail(contentToSend);

      const resendRequest = {
        to: recipient,
        subject: (personalizedSubject || "").trim(),
        body: htmlContent,
        html: true,
        from: undefined,
      };

      const response = await fetch(`${API_URL}/api/resend/send`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify(resendRequest),
      });

      const result: ResendResponse = await response.json();
      // Convert ResendResponse to EmailResponse format for compatibility
      const emailResponse: EmailResponse = {
        messageId: result.messageId || "",
        success: result.success,
        message: result.message,
        timestamp: new Date().toISOString(),
        totalRecipients: 1,
        successfulRecipients: result.success ? 1 : 0,
        failedRecipients: result.success ? [] : [recipient],
      };
      setLastResponse(emailResponse);

      if (result.success) {
        toast({
          title: "Email Sent Successfully",
          description: `Email sent to ${recipient} via Resend API`,
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
      console.error("Error sending email via Resend API:", error);
      toast({
        title: "Error",
        description: "Failed to send email via Resend API. Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsSending(false);
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
      // If we have selected leads, send personalized emails individually
      if (selectedLeads.length > 0) {
        const personalizedEmails = selectedLeads.map((lead) => {
          let personalizedSubject = formData.subject;
          let personalizedContent = formData.content;

          // If there's a template, use template personalization
          if (formData.templateId) {
            const template = templates.find(
              (t) => t.id === formData.templateId
            );
            if (template) {
              const personalized = getPersonalizedContent(template, lead);
              personalizedSubject = personalized.subject;
              personalizedContent = personalized.content;
            }
          } else {
            // Use direct variable substitution
            personalizedSubject = substituteTemplateVariables(
              formData.subject,
              lead
            );
            personalizedContent = substituteTemplateVariables(
              formData.content,
              lead
            );
          }

          const contentToSend = isTrackingEnabled
            ? processedContent
            : personalizedContent;

          const { htmlContent } = convertToHtmlEmail(contentToSend);

          return {
            to: lead.email,
            subject: personalizedSubject.trim(),
            body: htmlContent,
            html: true,
            from: undefined,
          };
        });

        // Send each personalized email individually
        const emailPromises = personalizedEmails.map(async (emailRequest) => {
          const response = await fetch(`${API_URL}/api/email/send`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
            },
            credentials: "include",
            body: JSON.stringify(emailRequest),
          });
          return response.json();
        });

        const results = await Promise.all(emailPromises);
        const successfulCount = results.filter((r) => r.success).length;
        const failedCount = results.length - successfulCount;

        toast({
          title: "Personalized Emails Sent",
          description: `Sent ${successfulCount} personalized emails, ${failedCount} failed`,
        });

        // Reset form
        setFormData((prev) => ({
          ...prev,
          subject: "",
          content: "",
          recipients: [],
        }));
        setSelectedLeads([]);
        return;
      }

      // Fallback to regular bulk sending (no personalization)
      const contentToSend = isTrackingEnabled
        ? processedContent
        : formData.content;

      const { htmlContent } = convertToHtmlEmail(contentToSend);

      const bulkRequest = {
        subject: formData.subject.trim(),
        content: htmlContent,
        recipients: validRecipients,
        isHtml: true,
        replyTo: null,
        campaignId: formData.campaignId,
      };

      const response = await fetch(`${API_URL}/api/email/send-bulk`, {
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

  // Template variable substitution
  const substituteTemplateVariables = (text: string, lead: Lead): string => {
    if (!text || !lead) return text;

    let result = text
      // Handle firstName variations
      .replace(/\{\{\s*first_name\s*\}\}/gi, lead.firstName || "")
      .replace(/\{\{\s*firstName\s*\}\}/gi, lead.firstName || "")
      .replace(/\{\{\s*firstname\s*\}\}/gi, lead.firstName || "")
      // Handle lastName variations
      .replace(/\{\{\s*last_name\s*\}\}/gi, lead.lastName || "")
      .replace(/\{\{\s*lastName\s*\}\}/gi, lead.lastName || "")
      .replace(/\{\{\s*lastname\s*\}\}/gi, lead.lastName || "")
      // Handle fullName (combination)
      .replace(
        /\{\{\s*full_name\s*\}\}/gi,
        `${lead.firstName || ""} ${lead.lastName || ""}`.trim()
      )
      .replace(
        /\{\{\s*fullName\s*\}\}/gi,
        `${lead.firstName || ""} ${lead.lastName || ""}`.trim()
      )
      .replace(
        /\{\{\s*fullname\s*\}\}/gi,
        `${lead.firstName || ""} ${lead.lastName || ""}`.trim()
      )
      // Handle email
      .replace(/\{\{\s*email\s*\}\}/gi, lead.email || "")
      // Handle company variations
      .replace(/\{\{\s*company\s*\}\}/gi, lead.domain || "")
      .replace(/\{\{\s*domain\s*\}\}/gi, lead.domain || "")
      // Handle position variations
      .replace(/\{\{\s*title\s*\}\}/gi, lead.position || "")
      .replace(/\{\{\s*position\s*\}\}/gi, lead.position || "")
      .replace(/\{\{\s*job_title\s*\}\}/gi, lead.position || "")
      // Handle other fields
      .replace(/\{\{\s*department\s*\}\}/gi, lead.department || "")
      .replace(/\{\{\s*seniority\s*\}\}/gi, lead.seniority || "")
      .replace(/\{\{\s*phone\s*\}\}/gi, lead.phone || "")
      .replace(/\{\{\s*linkedin\s*\}\}/gi, lead.linkedinUrl || "")
      .replace(/\{\{\s*twitter\s*\}\}/gi, lead.twitter || "");

    // Only remove variables that couldn't be matched
    const remainingVariables = result.match(/\{\{\s*[^}]+\s*\}\}/g);
    if (remainingVariables) {
      result = result.replace(/\{\{\s*[^}]+\s*\}\}/g, ""); // Remove any remaining {{variable}} patterns
    }
    return result;
  };

  const getPersonalizedContent = (template: EmailTemplate, lead: Lead) => {
    try {
      const contentData = JSON.parse(template.contentJson || "{}");
      const subject = contentData.subject || "";
      const content = contentData.body || "";

      return {
        subject: substituteTemplateVariables(subject, lead),
        content: substituteTemplateVariables(content, lead),
        isHtml: contentData.isHtml || false,
      };
    } catch (error) {
      console.error("Error parsing template for personalization:", error);
      return {
        subject: "",
        content: "",
        isHtml: false,
      };
    }
  };

  const addRecipientsFromCSV = () => {
    // This would open a file picker and parse CSV
    toast({
      title: "Feature Coming Soon",
      description: "CSV import functionality will be available soon.",
    });
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

    setIsSending(true);
    setLastResponse(null);

    try {
      const validRecipients = formData.recipients.filter(
        (email) => email.trim() !== ""
      );

      // Extract variables from email content
      const variableResult = extractVariablesFromEmail(
        formData.subject,
        formData.content
      );
      // If there are personalization variables, validate leads
      if (variableResult.totalVariables > 0) {
        // Use selectedLeads if available, otherwise find leads by email
        const leadsToValidate =
          selectedLeads.length > 0
            ? selectedLeads
            : leads.filter((lead) => validRecipients.includes(lead.email));

        if (leadsToValidate.length > 0) {
          const validation = validateLeads(
            leadsToValidate,
            variableResult.requiredVariables
          );
          setValidationResults(validation);
          setShowValidationModal(true);
          setIsSending(false);
          return;
        }
      }

      // If we have selected leads, send personalized emails
      if (selectedLeads.length > 0) {
        // Send personalized emails to each lead
        const personalizedEmails = selectedLeads.map((lead) => {
          let personalizedSubject = formData.subject;
          let personalizedContent = formData.content;

          // If there's a template, use template personalization
          if (formData.templateId) {
            const template = templates.find(
              (t) => t.id === formData.templateId
            );
            if (template) {
              const personalized = getPersonalizedContent(template, lead);
              personalizedSubject = personalized.subject;
              personalizedContent = personalized.content;
            }
          } else {
            // Use direct variable substitution
            personalizedSubject = substituteTemplateVariables(
              formData.subject,
              lead
            );
            personalizedContent = substituteTemplateVariables(
              formData.content,
              lead
            );
          }

          return {
            recipients: [lead.email],
            subject: personalizedSubject,
            content: personalizedContent,
            replyTo: null,
            isHtml: formData.isHtml,
            campaignId: formData.campaignId,
          };
        });

        // Send each personalized email using Resend API
        const emailPromises = personalizedEmails.map(async (emailRequest) => {
          const contentToSend = isTrackingEnabled
            ? processedContent
            : emailRequest.content;

          const { htmlContent } = convertToHtmlEmail(contentToSend);

          const resendRequest = {
            to: emailRequest.recipients[0],
            subject: emailRequest.subject.trim(),
            body: htmlContent,
            html: true,
            from: undefined,
          };

          const response = await fetch(`${API_URL}/api/email/send`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
            },
            credentials: "include",
            body: JSON.stringify(resendRequest),
          });
          return response.json();
        });

        const results = await Promise.all(emailPromises);
        const successfulCount = results.filter((r) => r.success).length;
        const failedCount = results.length - successfulCount;

        toast({
          title: "Personalized Emails Sent",
          description: `Sent ${successfulCount} personalized emails, ${failedCount} failed`,
        });

        // Reset form
        setFormData((prev) => ({
          ...prev,
          subject: "",
          content: "",
          recipients: [],
        }));
        setSelectedLeads([]);
        return;
      }

      // Fallback to regular email sending using Resend API
      const contentToSend = isTrackingEnabled
        ? processedContent
        : formData.content;

      const { htmlContent } = convertToHtmlEmail(contentToSend);

      if (validRecipients.length > 1) {
        // Use bulk sending for multiple recipients
        const bulkRequest = {
          recipients: validRecipients,
          subject: formData.subject.trim(),
          body: htmlContent,
          html: true,
          from: undefined,
          campaignId: formData.campaignId,
          userId: user?.id?.toString(),
        };

        const response = await fetch(`${API_URL}/api/email/send-bulk`, {
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
        } else {
          toast({
            title: "Bulk Send Failed",
            description: result.message || "Failed to send bulk emails.",
            variant: "destructive",
          });
        }
      } else {
        // Use single email sending for one recipient
        const emailRequest = {
          subject: formData.subject.trim(),
          content: htmlContent,
          recipients: [validRecipients[0]],
          isHtml: true,
          replyTo: null,
          campaignId: formData.campaignId,
        };

        const response = await fetch(`${API_URL}/api/email/send`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify(emailRequest),
        });

        const result: ResendResponse = await response.json();
        // Convert ResendResponse to EmailResponse format for compatibility
        const emailResponse: EmailResponse = {
          messageId: result.messageId || "",
          success: result.success,
          message: result.message,
          timestamp: new Date().toISOString(),
          totalRecipients: 1,
          successfulRecipients: result.success ? 1 : 0,
          failedRecipients: result.success ? [] : [validRecipients[0]],
        };
        setLastResponse(emailResponse);

        if (result.success) {
          toast({
            title: "Email Sent Successfully",
            description: `Email sent to ${validRecipients[0]} via Resend API`,
          });
        } else {
          toast({
            title: "Email Failed",
            description: result.message || "Failed to send email",
            variant: "destructive",
          });
        }
      }

      // Reset form
      setFormData((prev) => ({
        ...prev,
        subject: "",
        content: "",
        recipients: [],
      }));
      setSelectedLeads([]);
    } catch (error) {
      console.error("Error sending email:", error);
      toast({
        title: "Error",
        description: "Failed to send email. Please try again.",
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
                  <p className="mt-2 text-base md:text-lg text-gray-600">
                    Create, send, and track professional email campaigns
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2 md:gap-3">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setShowTemplates(!showTemplates)}
                    className="flex items-center gap-1 md:gap-2 text-xs md:text-sm"
                  >
                    <BookOpen className="h-3 w-3 md:h-4 md:w-4" />
                    <span className="hidden sm:inline">Templates</span>
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setShowHistory(!showHistory)}
                    className="flex items-center gap-1 md:gap-2 text-xs md:text-sm"
                  >
                    <History className="h-3 w-3 md:h-4 md:w-4" />
                    <span className="hidden sm:inline">History</span>
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setShowSecurityInfo(!showSecurityInfo)}
                    className="flex items-center gap-1 md:gap-2 text-xs md:text-sm"
                  >
                    <Shield className="h-3 w-3 md:h-4 md:w-4" />
                    <span className="hidden sm:inline">
                      {showSecurityInfo ? "Hide" : "Show"} Security
                    </span>
                  </Button>
                </div>
              </div>
            </div>

            {/* Sandbox Mode Warning */}
            {verificationStatus.sandboxMode && (
              <Alert className="mb-6 border-orange-200 bg-orange-50">
                <AlertCircle className="h-4 w-4 text-orange-600" />
                <AlertDescription className="text-orange-800">
                  <div className="font-medium mb-2">AWS SES Sandbox Mode</div>
                  <p className="text-sm mb-2">{verificationStatus.message}</p>
                  <p className="text-xs">{verificationStatus.instructions}</p>
                </AlertDescription>
              </Alert>
            )}

            {/* Security Info Panel */}
            {showSecurityInfo && (
              <Card className="mb-6 border-blue-200 bg-blue-50">
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-medium text-blue-900 flex items-center gap-2">
                    <Lock className="h-4 w-4" />
                    Security Features
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-0">
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                    <div className="flex items-center gap-2">
                      <CheckCircle className="h-4 w-4 text-green-600" />
                      <span>OAuth2 Authentication</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <CheckCircle className="h-4 w-4 text-green-600" />
                      <span>Rate Limiting</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <CheckCircle className="h-4 w-4 text-green-600" />
                      <span>Input Validation</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <CheckCircle className="h-4 w-4 text-green-600" />
                      <span>CSRF Protection</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <CheckCircle className="h-4 w-4 text-green-600" />
                      <span>Email Suppression</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <CheckCircle className="h-4 w-4 text-green-600" />
                      <span>HTTPS Only</span>
                    </div>
                  </div>
                  <div className="mt-3 pt-3 border-t border-blue-200">
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-blue-700">Rate Limit:</span>
                      <Badge variant="outline" className="text-blue-700">
                        {rateLimitInfo.remaining} emails remaining
                      </Badge>
                    </div>
                  </div>
                </CardContent>
              </Card>
            )}

            {/* Main Content Tabs */}
            <Tabs
              value={activeTab}
              onValueChange={setActiveTab}
              className="space-y-4 md:space-y-6"
            >
              <TabsList className="grid w-full grid-cols-2 md:grid-cols-4 h-auto">
                <TabsTrigger
                  value="compose"
                  className="flex items-center gap-1 md:gap-2 text-xs md:text-sm py-2"
                >
                  <Mail className="h-3 w-3 md:h-4 md:w-4" />
                  <span className="hidden sm:inline">Compose</span>
                </TabsTrigger>
                <TabsTrigger
                  value="templates"
                  className="flex items-center gap-1 md:gap-2 text-xs md:text-sm py-2"
                >
                  <BookOpen className="h-3 w-3 md:h-4 md:w-4" />
                  <span className="hidden sm:inline">Templates</span>
                </TabsTrigger>
                <TabsTrigger
                  value="history"
                  className="flex items-center gap-1 md:gap-2 text-xs md:text-sm py-2"
                >
                  <History className="h-3 w-3 md:h-4 md:w-4" />
                  <span className="hidden sm:inline">History</span>
                </TabsTrigger>
                <TabsTrigger
                  value="analytics"
                  className="flex items-center gap-1 md:gap-2 text-xs md:text-sm py-2"
                >
                  <BarChart3 className="h-3 w-3 md:h-4 md:w-4" />
                  <span className="hidden sm:inline">Analytics</span>
                </TabsTrigger>
              </TabsList>

              {/* Compose Tab */}
              <TabsContent value="compose" className="space-y-4 md:space-y-6">
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 md:gap-6">
                  {/* Email Form */}
                  <div className="lg:col-span-2">
                    <Card className="shadow-lg border-0">
                      <CardHeader className="bg-gradient-to-r from-blue-50 to-indigo-50 border-b">
                        <div className="flex items-center justify-between">
                          <div>
                            <CardTitle className="flex items-center gap-2 text-xl">
                              <Mail className="h-6 w-6 text-blue-600" />
                              Compose Email
                            </CardTitle>
                            <CardDescription className="text-base">
                              Create and send professional emails to your
                              recipients
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
                          {/* Template Selection */}
                          {templates.length > 0 && (
                            <div className="space-y-2">
                              <Label>Email Template</Label>
                              <div className="flex gap-2">
                                <Select
                                  value={formData.templateId || ""}
                                  onValueChange={(value) => {
                                    if (value) {
                                      const template = templates.find(
                                        (t) => t.id === value
                                      );
                                      if (template) loadTemplate(template);
                                    } else {
                                      clearTemplate();
                                    }
                                  }}
                                >
                                  <SelectTrigger>
                                    <SelectValue placeholder="Select a template (optional)" />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {templates.map((template) => (
                                      <SelectItem
                                        key={template.id}
                                        value={template.id}
                                      >
                                        {template.name}{" "}
                                        {template.category &&
                                          `(${template.category})`}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                                {formData.templateId && (
                                  <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    onClick={clearTemplate}
                                  >
                                    <X className="h-4 w-4" />
                                  </Button>
                                )}
                              </div>
                            </div>
                          )}

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
                                              isSelected
                                                ? "destructive"
                                                : "default"
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
                            maxRecipients={100}
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
                            placeholder="Enter your email content..."
                            maxLength={10000}
                            error={errors.content}
                          />

                          {/* Template Preview with Lead Data */}
                          {selectedLeads.length > 0 && formData.templateId && (
                            <div className="space-y-2">
                              <Label>Preview with Lead Data</Label>
                              <div className="border rounded-lg p-4 bg-blue-50">
                                <div className="text-sm text-blue-600 mb-2">
                                  Preview with: {selectedLeads[0].firstName}{" "}
                                  {selectedLeads[0].lastName}
                                </div>
                                <div className="space-y-2">
                                  <div>
                                    <span className="font-medium text-sm">
                                      Subject:{" "}
                                    </span>
                                    <span className="text-sm">
                                      {(() => {
                                        const template = templates.find(
                                          (t) => t.id === formData.templateId
                                        );
                                        if (template) {
                                          const personalized =
                                            getPersonalizedContent(
                                              template,
                                              selectedLeads[0]
                                            );
                                          return personalized.subject;
                                        }
                                        return formData.subject;
                                      })()}
                                    </span>
                                  </div>
                                  <div>
                                    <span className="font-medium text-sm">
                                      Content:{" "}
                                    </span>
                                    <div className="text-sm mt-1 p-2 bg-white rounded border max-h-32 overflow-y-auto">
                                      {(() => {
                                        const template = templates.find(
                                          (t) => t.id === formData.templateId
                                        );
                                        if (template) {
                                          const personalized =
                                            getPersonalizedContent(
                                              template,
                                              selectedLeads[0]
                                            );
                                          return personalized.content;
                                        }
                                        return formData.content;
                                      })()}
                                    </div>
                                  </div>
                                </div>
                              </div>
                            </div>
                          )}

                          {/* Submit Button */}
                          <Button
                            type="submit"
                            disabled={
                              isSending || rateLimitInfo.remaining === 0
                            }
                            className="w-full h-12 text-lg font-medium bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700"
                          >
                            {isSending ? (
                              <>
                                <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                                Sending...
                              </>
                            ) : (
                              <>
                                <Send className="mr-2 h-5 w-5" />
                                {selectedLeads.length > 0 && formData.templateId
                                  ? `Send ${selectedLeads.length} Personalized Emails`
                                  : "Send Email"}
                              </>
                            )}
                          </Button>

                          {rateLimitInfo.remaining === 0 && (
                            <Alert>
                              <AlertCircle className="h-4 w-4" />
                              <AlertDescription>
                                Rate limit reached. Please wait before sending
                                more emails.
                              </AlertDescription>
                            </Alert>
                          )}
                        </form>
                      </CardContent>
                    </Card>
                  </div>

                  {/* Status Panel */}
                  <div className="space-y-4 md:space-y-6">
                    {/* Rate Limit Status */}
                    <Card className="shadow-lg border-0">
                      <CardHeader className="pb-3 bg-gradient-to-r from-green-50 to-emerald-50">
                        <CardTitle className="text-sm font-medium flex items-center gap-2">
                          <Clock className="h-4 w-4 text-green-600" />
                          Rate Limit Status
                        </CardTitle>
                      </CardHeader>
                      <CardContent className="pt-4">
                        <div className="space-y-3">
                          <div className="flex justify-between items-center">
                            <span className="text-sm font-medium">
                              Remaining:
                            </span>
                            <Badge
                              variant={
                                rateLimitInfo.remaining > 10
                                  ? "default"
                                  : "destructive"
                              }
                              className="text-sm"
                            >
                              {rateLimitInfo.remaining} emails
                            </Badge>
                          </div>
                          <Progress
                            value={(rateLimitInfo.remaining / 100) * 100}
                            className="h-2"
                          />
                          <div className="text-xs text-gray-500">
                            {rateLimitInfo.remaining > 10 ? "Good" : "Low"}{" "}
                            remaining
                          </div>
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
                                  lastResponse.success
                                    ? "default"
                                    : "destructive"
                                }
                              >
                                {lastResponse.success ? "Success" : "Failed"}
                              </Badge>
                            </div>
                            <div className="flex justify-between">
                              <span>Sent:</span>
                              <span className="font-medium">
                                {lastResponse.successfulRecipients}
                              </span>
                            </div>
                            <div className="flex justify-between">
                              <span>Failed:</span>
                              <span className="font-medium">
                                {lastResponse.failedRecipients?.length || 0}
                              </span>
                            </div>
                            <div className="flex justify-between">
                              <span>Total:</span>
                              <span className="font-medium">
                                {lastResponse.totalRecipients || 0}
                              </span>
                            </div>
                            {lastResponse.messageId && (
                              <div className="pt-3 border-t">
                                <p className="text-xs text-gray-500 break-all">
                                  ID: {lastResponse.messageId}
                                </p>
                              </div>
                            )}
                          </div>
                        </CardContent>
                      </Card>
                    )}
                    {/* Security Tips */}
                    <Card className="shadow-lg border-0">
                      <CardHeader className="pb-3 bg-gradient-to-r from-orange-50 to-yellow-50">
                        <CardTitle className="text-sm font-medium flex items-center gap-2">
                          <Shield className="h-4 w-4 text-orange-600" />
                          Security Tips
                        </CardTitle>
                      </CardHeader>
                      <CardContent className="pt-4">
                        <ul className="space-y-2 text-xs text-gray-600">
                          <li className="flex items-start gap-2">
                            <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                            Always verify recipient emails
                          </li>
                          <li className="flex items-start gap-2">
                            <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                            Use clear, professional subjects
                          </li>
                          <li className="flex items-start gap-2">
                            <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                            Avoid suspicious attachments
                          </li>
                          <li className="flex items-start gap-2">
                            <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                            Respect recipient privacy
                          </li>
                          <li className="flex items-start gap-2">
                            <CheckCircle className="h-3 w-3 text-green-500 mt-0.5 flex-shrink-0" />
                            Follow anti-spam guidelines
                          </li>
                        </ul>
                      </CardContent>
                    </Card>
                  </div>
                </div>
              </TabsContent>

              {/* Templates Tab */}
              <TabsContent value="templates" className="space-y-6">
                <Card className="shadow-lg border-0">
                  <CardHeader className="bg-gradient-to-r from-green-50 to-emerald-50">
                    <CardTitle className="flex items-center gap-2 text-xl">
                      <BookOpen className="h-6 w-6 text-green-600" />
                      Email Templates
                    </CardTitle>
                    <CardDescription className="text-base">
                      Choose from pre-built templates or create your own
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="p-6">
                    {templates.length > 0 ? (
                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {templates.map((template) => (
                          <Card
                            key={template.id}
                            className="hover:shadow-md transition-shadow cursor-pointer"
                          >
                            <CardHeader className="pb-3">
                              <div className="flex items-center justify-between">
                                <CardTitle className="text-base">
                                  {template.name}
                                </CardTitle>
                                <Button
                                  type="button"
                                  size="sm"
                                  onClick={() => loadTemplate(template)}
                                  className="h-8"
                                >
                                  Use
                                </Button>
                              </div>
                              {template.category && (
                                <Badge variant="secondary" className="w-fit">
                                  {template.category}
                                </Badge>
                              )}
                            </CardHeader>
                            <CardContent className="pt-0">
                              <p className="text-sm text-gray-600 line-clamp-2">
                                {(() => {
                                  try {
                                    const contentData = JSON.parse(
                                      template.contentJson || "{}"
                                    );
                                    return contentData.subject || "No subject";
                                  } catch {
                                    return "No subject";
                                  }
                                })()}
                              </p>
                            </CardContent>
                          </Card>
                        ))}
                      </div>
                    ) : (
                      <div className="text-center py-8">
                        <BookOpen className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                        <p className="text-gray-500">No templates available</p>
                        <Button
                          type="button"
                          className="mt-4"
                          onClick={() => router.push("/templates")}
                        >
                          Create Template
                        </Button>
                      </div>
                    )}
                  </CardContent>
                </Card>
              </TabsContent>

              {/* History Tab */}
              <TabsContent value="history" className="space-y-6">
                <Card className="shadow-lg border-0">
                  <CardHeader className="bg-gradient-to-r from-blue-50 to-indigo-50">
                    <CardTitle className="flex items-center gap-2 text-xl">
                      <History className="h-6 w-6 text-blue-600" />
                      Email History
                    </CardTitle>
                    <CardDescription className="text-base">
                      View your recent email campaigns and their status
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="p-6">
                    {emailHistory.length > 0 ? (
                      <div className="space-y-4">
                        {emailHistory.map((email, index) => (
                          <div
                            key={index}
                            className="border rounded-lg p-4 hover:bg-gray-50"
                          >
                            <div className="flex items-center justify-between">
                              <div>
                                <div className="flex items-center gap-2">
                                  <Badge
                                    variant={
                                      email.success ? "default" : "destructive"
                                    }
                                  >
                                    {email.success ? "Success" : "Failed"}
                                  </Badge>
                                  <span className="text-sm text-gray-500">
                                    {new Date(email.timestamp).toLocaleString()}
                                  </span>
                                </div>
                                <p className="text-sm text-gray-600 mt-1">
                                  Sent to {email.successfulRecipients}{" "}
                                  recipients
                                </p>
                              </div>
                              <div className="text-right">
                                <p className="text-sm font-medium">
                                  {email.totalRecipients} total
                                </p>
                                {email.failedRecipients.length > 0 && (
                                  <p className="text-xs text-red-500">
                                    {email.failedRecipients.length} failed
                                  </p>
                                )}
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-center py-8">
                        <History className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                        <p className="text-gray-500">No email history yet</p>
                        <p className="text-sm text-gray-400 mt-2">
                          Send your first email to see it here
                        </p>
                      </div>
                    )}
                  </CardContent>
                </Card>
              </TabsContent>

              {/* Analytics Tab */}
              <TabsContent value="analytics" className="space-y-6">
                <Card className="shadow-lg border-0">
                  <CardHeader className="bg-gradient-to-r from-purple-50 to-pink-50">
                    <CardTitle className="flex items-center gap-2 text-xl">
                      <BarChart3 className="h-6 w-6 text-purple-600" />
                      Email Analytics
                    </CardTitle>
                    <CardDescription className="text-base">
                      Track your email performance and engagement metrics
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="p-6">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                      <div className="text-center">
                        <div className="text-3xl font-bold text-green-600">
                          {emailStats.sentToday}
                        </div>
                        <div className="text-sm text-gray-500">Sent Today</div>
                      </div>
                      <div className="text-center">
                        <div className="text-3xl font-bold text-blue-600">
                          {emailStats.sentThisWeek}
                        </div>
                        <div className="text-sm text-gray-500">
                          Sent This Week
                        </div>
                      </div>
                      <div className="text-center">
                        <div className="text-3xl font-bold text-purple-600">
                          {emailStats.sentThisMonth}
                        </div>
                        <div className="text-sm text-gray-500">
                          Sent This Month
                        </div>
                      </div>
                    </div>
                    <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6">
                      <div className="text-center">
                        <div className="text-2xl font-bold text-green-600">
                          {emailStats.openRate}%
                        </div>
                        <div className="text-sm text-gray-500">Open Rate</div>
                      </div>
                      <div className="text-center">
                        <div className="text-2xl font-bold text-blue-600">
                          {emailStats.clickRate}%
                        </div>
                        <div className="text-sm text-gray-500">Click Rate</div>
                      </div>
                      <div className="text-center">
                        <div className="text-2xl font-bold text-purple-600">
                          {emailStats.replyRate}%
                        </div>
                        <div className="text-sm text-gray-500">Reply Rate</div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>
            </Tabs>
          </div>
        </div>

        {/* Email Validation Modal */}
        {showValidationModal && validationResults && (
          <EmailValidationModal
            open={showValidationModal}
            onOpenChange={setShowValidationModal}
            onProceed={handleProceedWithAll}
            onCancel={handleCancelValidation}
            onSkipInvalid={handleSkipInvalid}
            validationResults={validationResults}
          />
        )}
      </DashboardLayout>
    </AuthGuard>
  );
}
