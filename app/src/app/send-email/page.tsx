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
} from "lucide-react";
import { useToast } from "@/components/ui/use-toast";

interface EmailFormData {
  recipients: string[];
  subject: string;
  content: string;
  isHtml: boolean;
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

export default function SendEmailPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [showSecurityInfo, setShowSecurityInfo] = useState(false);
  const [rateLimitInfo, setRateLimitInfo] = useState({
    remaining: 100,
    resetTime: null,
  });
  const [verificationStatus, setVerificationStatus] = useState({
    sandboxMode: false,
    message: "",
    instructions: "",
  });

  const [formData, setFormData] = useState<EmailFormData>({
    recipients: [""],
    subject: "",
    content: "",
    isHtml: true,
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [lastResponse, setLastResponse] = useState<EmailResponse | null>(null);

  const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  // Redirect if not authenticated
  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/auth");
    }
  }, [user, authLoading, router]);

  // Load rate limit info and verification status
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
    if (!formData.subject.trim()) {
      newErrors.subject = "Subject is required";
    } else if (formData.subject.length > 200) {
      newErrors.subject = "Subject must be less than 200 characters";
    }

    // Validate content
    if (!formData.content.trim()) {
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

      const emailRequest = {
        recipients: validRecipients,
        subject: formData.subject.trim(),
        content: formData.content.trim(),
        replyTo: null,
        isHtml: formData.isHtml,
        campaignId: null,
      };

      const response = await fetch(`${API_URL}/api/email/send`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify(emailRequest),
      });

      const data: EmailResponse = await response.json();
      setLastResponse(data);

      if (data.success) {
        toast({
          title: "Email Sent Successfully",
          description: `Email sent to ${data.successfulRecipients} recipient(s)`,
        });

        // Reset form
        setFormData((prev) => ({
          ...prev,
          subject: "",
          content: "",
          recipients: [""],
        }));
      } else {
        // Check if it's a SES verification error
        if (data.message && data.message.includes("not verified")) {
          toast({
            title: "Email Address Not Verified",
            description:
              "The recipient email address needs to be verified in AWS SES. This is required in sandbox mode.",
            variant: "destructive",
          });
        } else {
          toast({
            title: "Failed to Send Email",
            description: data.message,
            variant: "destructive",
          });
        }
      }
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
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">Send Email</h1>
              <p className="mt-2 text-gray-600">
                Compose and send secure emails to your leads
              </p>
            </div>
            <Button
              variant="outline"
              onClick={() => setShowSecurityInfo(!showSecurityInfo)}
              className="flex items-center gap-2"
            >
              <Shield className="h-4 w-4" />
              {showSecurityInfo ? "Hide" : "Show"} Security
            </Button>
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

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Email Form */}
          <div className="lg:col-span-2">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Mail className="h-5 w-5" />
                  Compose Email
                </CardTitle>
                <CardDescription>
                  Create and send secure emails to your recipients
                </CardDescription>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleSubmit} className="space-y-6">
                  {/* Recipients */}
                  <div className="space-y-2">
                    <Label htmlFor="recipients">Recipients *</Label>
                    {formData.recipients.map((email, index) => (
                      <div key={index} className="flex gap-2">
                        <Input
                          type="email"
                          value={email}
                          onChange={(e) =>
                            updateRecipient(index, e.target.value)
                          }
                          placeholder="recipient@example.com"
                          className={errors.recipients ? "border-red-500" : ""}
                        />
                        {formData.recipients.length > 1 && (
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => removeRecipient(index)}
                            className="px-3"
                          >
                            ×
                          </Button>
                        )}
                      </div>
                    ))}
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={addRecipient}
                      className="w-full"
                    >
                      + Add Recipient
                    </Button>
                    {errors.recipients && (
                      <p className="text-sm text-red-600">
                        {errors.recipients}
                      </p>
                    )}
                  </div>

                  {/* Subject */}
                  <div className="space-y-2">
                    <Label htmlFor="subject">Subject *</Label>
                    <Input
                      id="subject"
                      value={formData.subject}
                      onChange={(e) =>
                        setFormData((prev) => ({
                          ...prev,
                          subject: e.target.value,
                        }))
                      }
                      placeholder="Enter email subject"
                      className={errors.subject ? "border-red-500" : ""}
                      maxLength={200}
                    />
                    <div className="flex justify-between text-xs text-gray-500">
                      <span>{errors.subject || ""}</span>
                      <span>{formData.subject.length}/200</span>
                    </div>
                  </div>

                  {/* Content */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="content">Content *</Label>
                      <div className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          id="isHtml"
                          checked={formData.isHtml}
                          onChange={(e) =>
                            setFormData((prev) => ({
                              ...prev,
                              isHtml: e.target.checked,
                            }))
                          }
                          className="rounded"
                        />
                        <Label htmlFor="isHtml" className="text-sm">
                          HTML Format
                        </Label>
                      </div>
                    </div>
                    <Textarea
                      id="content"
                      value={formData.content}
                      onChange={(e) =>
                        setFormData((prev) => ({
                          ...prev,
                          content: e.target.value,
                        }))
                      }
                      placeholder="Enter your email content..."
                      className={`min-h-[200px] ${errors.content ? "border-red-500" : ""}`}
                      maxLength={10000}
                    />
                    <div className="flex justify-between text-xs text-gray-500">
                      <span>{errors.content || ""}</span>
                      <span>{formData.content.length}/10,000</span>
                    </div>
                  </div>

                  {/* Submit Button */}
                  <Button
                    type="submit"
                    disabled={isSending || rateLimitInfo.remaining === 0}
                    className="w-full"
                  >
                    {isSending ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Sending...
                      </>
                    ) : (
                      <>
                        <Send className="mr-2 h-4 w-4" />
                        Send Email
                      </>
                    )}
                  </Button>

                  {rateLimitInfo.remaining === 0 && (
                    <Alert>
                      <AlertCircle className="h-4 w-4" />
                      <AlertDescription>
                        Rate limit reached. Please wait before sending more
                        emails.
                      </AlertDescription>
                    </Alert>
                  )}
                </form>
              </CardContent>
            </Card>
          </div>

          {/* Status Panel */}
          <div className="space-y-6">
            {/* Rate Limit Status */}
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <Clock className="h-4 w-4" />
                  Rate Limit
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span>Remaining:</span>
                    <Badge
                      variant={
                        rateLimitInfo.remaining > 10 ? "default" : "destructive"
                      }
                    >
                      {rateLimitInfo.remaining}
                    </Badge>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className={`h-2 rounded-full ${
                        rateLimitInfo.remaining > 10
                          ? "bg-green-500"
                          : "bg-red-500"
                      }`}
                      style={{
                        width: `${(rateLimitInfo.remaining / 100) * 100}%`,
                      }}
                    />
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Last Response */}
            {lastResponse && (
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-medium flex items-center gap-2">
                    {lastResponse.success ? (
                      <CheckCircle className="h-4 w-4 text-green-600" />
                    ) : (
                      <AlertCircle className="h-4 w-4 text-red-600" />
                    )}
                    Last Send Result
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-0">
                  <div className="space-y-2 text-sm">
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
                      <span>Sent:</span>
                      <span>{lastResponse.successfulRecipients}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Failed:</span>
                      <span>{lastResponse.failedRecipients.length}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Total:</span>
                      <span>{lastResponse.totalRecipients}</span>
                    </div>
                    {lastResponse.messageId && (
                      <div className="pt-2 border-t">
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
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-sm font-medium">
                  Security Tips
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-0">
                <ul className="space-y-2 text-xs text-gray-600">
                  <li>• Always verify recipient emails</li>
                  <li>• Use clear, professional subjects</li>
                  <li>• Avoid suspicious attachments</li>
                  <li>• Respect recipient privacy</li>
                  <li>• Follow anti-spam guidelines</li>
                </ul>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
