"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Plus,
  X,
  Download,
  Users,
  CheckCircle,
  AlertCircle,
  Mail,
  FileText,
  Search,
  Filter,
} from "lucide-react";
import { useToast } from "@/components/ui/use-toast";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { API_BASE_URL } from "@/lib/config";

interface Recipient {
  email: string;
  name?: string;
  isValid: boolean;
  isVerified?: boolean;
  tags?: string[];
}

interface RecipientManagerProps {
  recipients: string[];
  onRecipientsChange: (recipients: string[]) => void;
  errors?: Record<string, string>;
  maxRecipients?: number;
}

export function RecipientManager({
  recipients,
  onRecipientsChange,
  errors,
  maxRecipients = 100,
}: RecipientManagerProps) {
  const [recipientList, setRecipientList] = useState<Recipient[]>([]);
  const [newEmail, setNewEmail] = useState("");
  const [filter, setFilter] = useState<"all" | "valid" | "invalid">("all");
  const [searchTerm, setSearchTerm] = useState("");
  const [showAddLeadModal, setShowAddLeadModal] = useState(false);
  const [pendingEmail, setPendingEmail] = useState("");
  const [leadFormData, setLeadFormData] = useState({
    firstName: "",
    lastName: "",
    domain: "",
    phone: "",
    linkedinUrl: "",
    position: "",
    positionRaw: "",
    seniority: "",
    department: "",
    twitter: "",
  });
  const [isCreatingLead, setIsCreatingLead] = useState(false);
  const { toast } = useToast();

  // Initialize recipient list from props
  useEffect(() => {
    const recipientsWithValidation = recipients.map((email) => ({
      email,
      isValid: validateEmail(email),
      isVerified: false, // This would come from an API
    }));
    setRecipientList(recipientsWithValidation);
  }, [recipients]);

  const validateEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const addRecipient = async (email: string) => {
    if (!email.trim()) return;

    const trimmedEmail = email.trim();

    if (recipientList.some((r) => r.email === trimmedEmail)) {
      toast({
        title: "Duplicate Email",
        description: "This email is already in the list",
        variant: "destructive",
      });
      return;
    }

    if (recipientList.length >= maxRecipients) {
      toast({
        title: "Limit Reached",
        description: `Maximum ${maxRecipients} recipients allowed`,
        variant: "destructive",
      });
      return;
    }

    // Check if email exists in Lead DB
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/leads/check-email?email=${encodeURIComponent(trimmedEmail)}`,
        {
          method: "GET",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
        }
      );

      if (response.ok) {
        const data = await response.json();

        if (data.exists) {
          const exactEmail = data.exactEmail || trimmedEmail;

          // If global exists but org mapping doesn't, create org_leads mapping
          if (data.existsGlobal && !data.existsOrg) {
            try {
              await fetch(`${API_BASE_URL}/api/leads/create-from-email`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({
                  email: trimmedEmail,
                  firstName: data.lead?.firstName || null,
                  lastName: data.lead?.lastName || null,
                  domain: data.lead?.domain || null,
                }),
              });
            } catch (e) {
              // non-fatal; continue to add recipient
            }
          }

          const newRecipient: Recipient = {
            email: exactEmail,
            isValid: validateEmail(exactEmail),
            isVerified: false,
          };

          const updatedList = [...recipientList, newRecipient];
          setRecipientList(updatedList);
          onRecipientsChange(updatedList.map((r) => r.email));
          setNewEmail("");

          toast({
            title: data.existsOrg ? "Lead Found" : "Lead Mapped",
            description: data.existsOrg
              ? `Added ${data.lead?.firstName || ""} ${data.lead?.lastName || ""} (${exactEmail}) from your leads`
              : `Created org mapping and added ${exactEmail}`,
          });
        } else {
          // Email doesn't exist, show modal to add lead
          setPendingEmail(trimmedEmail);
          setLeadFormData({
            firstName: "",
            lastName: "",
            domain: trimmedEmail.split("@")[1] || "",
            phone: "",
            linkedinUrl: "",
            position: "",
            positionRaw: "",
            seniority: "",
            department: "",
            twitter: "",
          });
          setShowAddLeadModal(true);
        }
      } else {
        // Fallback: add without checking
        const newRecipient: Recipient = {
          email: trimmedEmail,
          isValid: validateEmail(trimmedEmail),
          isVerified: false,
        };

        const updatedList = [...recipientList, newRecipient];
        setRecipientList(updatedList);
        onRecipientsChange(updatedList.map((r) => r.email));
        setNewEmail("");
      }
    } catch (error) {
      // Fallback: add without checking
      const newRecipient: Recipient = {
        email: trimmedEmail,
        isValid: validateEmail(trimmedEmail),
        isVerified: false,
      };

      const updatedList = [...recipientList, newRecipient];
      setRecipientList(updatedList);
      onRecipientsChange(updatedList.map((r) => r.email));
      setNewEmail("");
    }
  };

  const removeRecipient = (index: number) => {
    const updatedList = recipientList.filter((_, i) => i !== index);
    setRecipientList(updatedList);
    onRecipientsChange(updatedList.map((r) => r.email));
  };

  const createLeadAndAddRecipient = async () => {
    if (!pendingEmail.trim()) return;

    setIsCreatingLead(true);
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/leads/create-from-email`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify({
            email: pendingEmail,
            firstName: leadFormData.firstName.trim() || null,
            lastName: leadFormData.lastName.trim() || null,
            domain: leadFormData.domain.trim() || null,
            phone: leadFormData.phone.trim() || null,
            linkedinUrl: leadFormData.linkedinUrl.trim() || null,
            position: leadFormData.position.trim() || null,
            positionRaw: leadFormData.positionRaw.trim() || null,
            seniority: leadFormData.seniority.trim() || null,
            department: leadFormData.department.trim() || null,
            twitter: leadFormData.twitter.trim() || null,
          }),
        }
      );

      if (response.ok) {
        const data = await response.json();

        // Add recipient to list
        const newRecipient: Recipient = {
          email: pendingEmail,
          isValid: validateEmail(pendingEmail),
          isVerified: false,
        };

        const updatedList = [...recipientList, newRecipient];
        setRecipientList(updatedList);
        onRecipientsChange(updatedList.map((r) => r.email));
        setNewEmail("");

        // Close modal and reset form
        setShowAddLeadModal(false);
        setPendingEmail("");
        setLeadFormData({
          firstName: "",
          lastName: "",
          domain: "",
          phone: "",
          linkedinUrl: "",
          position: "",
          positionRaw: "",
          seniority: "",
          department: "",
          twitter: "",
        });

        toast({
          title: "Lead Created",
          description: `Successfully created lead for ${pendingEmail}`,
        });
      } else {
        const errorData = await response.json();
        toast({
          title: "Error Creating Lead",
          description: errorData.error || "Failed to create lead",
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Error Creating Lead",
        description: "Failed to create lead. Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsCreatingLead(false);
    }
  };

  const cancelAddLead = () => {
    setShowAddLeadModal(false);
    setPendingEmail("");
    setLeadFormData({
      firstName: "",
      lastName: "",
      domain: "",
      phone: "",
      linkedinUrl: "",
      position: "",
      positionRaw: "",
      seniority: "",
      department: "",
      twitter: "",
    });
  };

  const exportRecipients = () => {
    const csvContent = recipientList
      .map(
        (r) =>
          `"${r.email}","${r.name || ""}","${r.isValid ? "valid" : "invalid"}"`
      )
      .join("\n");

    const blob = new Blob([`Email,Name,Status\n${csvContent}`], {
      type: "text/csv",
    });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "recipients.csv";
    a.click();
    window.URL.revokeObjectURL(url);
  };

  const filteredRecipients = recipientList.filter((recipient) => {
    const matchesFilter =
      filter === "all" ||
      (filter === "valid" && recipient.isValid) ||
      (filter === "invalid" && !recipient.isValid);

    const matchesSearch =
      searchTerm === "" ||
      recipient.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (recipient.name &&
        recipient.name.toLowerCase().includes(searchTerm.toLowerCase()));

    return matchesFilter && matchesSearch;
  });

  const validCount = recipientList.filter((r) => r.isValid).length;
  const invalidCount = recipientList.filter((r) => !r.isValid).length;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <Label className="text-base font-medium">
          Recipients ({recipientList.length}/{maxRecipients})
        </Label>
        <div className="flex gap-2">
          {recipientList.length > 0 && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={exportRecipients}
              className="flex items-center gap-1"
            >
              <Download className="h-4 w-4" />
              Export
            </Button>
          )}
        </div>
      </div>

      {/* Add Single Recipient */}
      <div className="flex gap-2">
        <Input
          type="email"
          value={newEmail}
          onChange={(e) => setNewEmail(e.target.value)}
          placeholder="recipient@example.com"
          onKeyPress={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              addRecipient(newEmail);
            }
          }}
          className="flex-1"
        />
        <Button
          type="button"
          onClick={() => addRecipient(newEmail)}
          disabled={!newEmail.trim() || recipientList.length >= maxRecipients}
        >
          <Plus className="h-4 w-4" />
        </Button>
      </div>

      {/* Stats and Filters */}
      {recipientList.length > 0 && (
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <Badge variant="default" className="flex items-center gap-1">
                <CheckCircle className="h-3 w-3" />
                {validCount} Valid
              </Badge>
              {invalidCount > 0 && (
                <Badge
                  variant="destructive"
                  className="flex items-center gap-1"
                >
                  <AlertCircle className="h-3 w-3" />
                  {invalidCount} Invalid
                </Badge>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="h-4 w-4 absolute left-2 top-1/2 transform -translate-y-1/2 text-gray-400" />
              <Input
                placeholder="Search recipients..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-8 w-48"
              />
            </div>
            <Select
              value={filter}
              onValueChange={(value: any) => setFilter(value)}
            >
              <SelectTrigger className="w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All</SelectItem>
                <SelectItem value="valid">Valid</SelectItem>
                <SelectItem value="invalid">Invalid</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      )}

      {/* Recipients List */}
      {filteredRecipients.length > 0 ? (
        <Card>
          <CardContent className="p-4">
            <div className="space-y-2 max-h-60 overflow-y-auto">
              {filteredRecipients.map((recipient, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between p-2 rounded-lg border hover:bg-gray-50"
                >
                  <div className="flex items-center gap-3">
                    <div className="flex items-center gap-2">
                      {recipient.isValid ? (
                        <CheckCircle className="h-4 w-4 text-green-500" />
                      ) : (
                        <AlertCircle className="h-4 w-4 text-red-500" />
                      )}
                      <Mail className="h-4 w-4 text-gray-400" />
                    </div>
                    <div>
                      <div className="font-medium text-sm">
                        {recipient.email}
                      </div>
                      {recipient.name && (
                        <div className="text-xs text-gray-500">
                          {recipient.name}
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {recipient.tags && recipient.tags.length > 0 && (
                      <div className="flex gap-1">
                        {recipient.tags.map((tag, tagIndex) => (
                          <Badge
                            key={tagIndex}
                            variant="secondary"
                            className="text-xs"
                          >
                            {tag}
                          </Badge>
                        ))}
                      </div>
                    )}
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => removeRecipient(index)}
                      className="h-8 w-8 p-0"
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      ) : recipientList.length === 0 ? (
        <Card>
          <CardContent className="p-8 text-center">
            <Users className="h-12 w-12 text-gray-400 mx-auto mb-4" />
            <p className="text-gray-500 mb-2">No recipients added yet</p>
            <p className="text-sm text-gray-400">
              Add email addresses above or import from a file
            </p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="p-8 text-center">
            <Search className="h-12 w-12 text-gray-400 mx-auto mb-4" />
            <p className="text-gray-500">No recipients match your search</p>
          </CardContent>
        </Card>
      )}

      {/* Error Display */}
      {errors?.recipients && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{errors.recipients}</AlertDescription>
        </Alert>
      )}

      {/* Add Lead Modal */}
      <Dialog open={showAddLeadModal} onOpenChange={setShowAddLeadModal}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Add New Lead</DialogTitle>
            <DialogDescription>
              The email <strong>{pendingEmail}</strong> is not in your leads
              database. Would you like to add this person as a new lead?
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4 max-h-96 overflow-y-auto">
            <div className="space-y-2">
              <Label htmlFor="modal-email">Email</Label>
              <Input
                id="modal-email"
                value={pendingEmail}
                disabled
                className="bg-gray-50"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="modal-firstName">First Name</Label>
                <Input
                  id="modal-firstName"
                  value={leadFormData.firstName}
                  onChange={(e) =>
                    setLeadFormData((prev) => ({
                      ...prev,
                      firstName: e.target.value,
                    }))
                  }
                  placeholder="Enter first name"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="modal-lastName">Last Name</Label>
                <Input
                  id="modal-lastName"
                  value={leadFormData.lastName}
                  onChange={(e) =>
                    setLeadFormData((prev) => ({
                      ...prev,
                      lastName: e.target.value,
                    }))
                  }
                  placeholder="Enter last name"
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="modal-domain">Company Domain</Label>
              <Input
                id="modal-domain"
                value={leadFormData.domain}
                onChange={(e) =>
                  setLeadFormData((prev) => ({
                    ...prev,
                    domain: e.target.value,
                  }))
                }
                placeholder="Enter company domain"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="modal-phone">Phone</Label>
              <Input
                id="modal-phone"
                value={leadFormData.phone}
                onChange={(e) =>
                  setLeadFormData((prev) => ({
                    ...prev,
                    phone: e.target.value,
                  }))
                }
                placeholder="Enter phone number"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="modal-linkedin">LinkedIn URL</Label>
              <Input
                id="modal-linkedin"
                value={leadFormData.linkedinUrl}
                onChange={(e) =>
                  setLeadFormData((prev) => ({
                    ...prev,
                    linkedinUrl: e.target.value,
                  }))
                }
                placeholder="https://linkedin.com/in/username"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="modal-position">Position</Label>
                <Input
                  id="modal-position"
                  value={leadFormData.position}
                  onChange={(e) =>
                    setLeadFormData((prev) => ({
                      ...prev,
                      position: e.target.value,
                    }))
                  }
                  placeholder="e.g., Software Engineer"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="modal-positionRaw">Position (Raw)</Label>
                <Input
                  id="modal-positionRaw"
                  value={leadFormData.positionRaw}
                  onChange={(e) =>
                    setLeadFormData((prev) => ({
                      ...prev,
                      positionRaw: e.target.value,
                    }))
                  }
                  placeholder="e.g., Senior Software Engineer"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="modal-seniority">Seniority</Label>
                <Input
                  id="modal-seniority"
                  value={leadFormData.seniority}
                  onChange={(e) =>
                    setLeadFormData((prev) => ({
                      ...prev,
                      seniority: e.target.value,
                    }))
                  }
                  placeholder="e.g., Senior, Junior, Manager"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="modal-department">Department</Label>
                <Input
                  id="modal-department"
                  value={leadFormData.department}
                  onChange={(e) =>
                    setLeadFormData((prev) => ({
                      ...prev,
                      department: e.target.value,
                    }))
                  }
                  placeholder="e.g., Engineering, Sales, Marketing"
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="modal-twitter">Twitter</Label>
              <Input
                id="modal-twitter"
                value={leadFormData.twitter}
                onChange={(e) =>
                  setLeadFormData((prev) => ({
                    ...prev,
                    twitter: e.target.value,
                  }))
                }
                placeholder="@username or https://twitter.com/username"
              />
            </div>
          </div>

          <DialogFooter className="flex gap-2">
            <Button
              variant="outline"
              onClick={cancelAddLead}
              disabled={isCreatingLead}
            >
              Cancel
            </Button>
            <Button
              onClick={createLeadAndAddRecipient}
              disabled={isCreatingLead}
            >
              {isCreatingLead ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                  Creating...
                </>
              ) : (
                "Create Lead & Add"
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
