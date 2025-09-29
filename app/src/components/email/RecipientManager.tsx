"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Plus,
  X,
  Upload,
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
  const [showBulkImport, setShowBulkImport] = useState(false);
  const [bulkEmails, setBulkEmails] = useState("");
  const [filter, setFilter] = useState<"all" | "valid" | "invalid">("all");
  const [searchTerm, setSearchTerm] = useState("");
  const { toast } = useToast();

  // Initialize recipient list from props
  useEffect(() => {
    console.log("RecipientManager useEffect triggered", { recipients });
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

  const addRecipient = (email: string) => {
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

    const newRecipient: Recipient = {
      email: trimmedEmail,
      isValid: validateEmail(trimmedEmail),
      isVerified: false,
    };

    const updatedList = [...recipientList, newRecipient];
    setRecipientList(updatedList);
    onRecipientsChange(updatedList.map((r) => r.email));
    setNewEmail("");
  };

  const removeRecipient = (index: number) => {
    const updatedList = recipientList.filter((_, i) => i !== index);
    setRecipientList(updatedList);
    onRecipientsChange(updatedList.map((r) => r.email));
  };

  const handleBulkImport = () => {
    const emails = bulkEmails
      .split(/[,\n;]/)
      .map((email) => email.trim())
      .filter((email) => email.length > 0);

    if (emails.length === 0) {
      toast({
        title: "No Emails Found",
        description: "Please enter valid email addresses",
        variant: "destructive",
      });
      return;
    }

    const newRecipients: Recipient[] = emails.map((email) => ({
      email,
      isValid: validateEmail(email),
      isVerified: false,
    }));

    const validEmails = newRecipients.filter((r) => r.isValid);
    const invalidEmails = newRecipients.filter((r) => !r.isValid);

    if (validEmails.length > 0) {
      const updatedList = [...recipientList, ...validEmails];
      setRecipientList(updatedList);
      onRecipientsChange(updatedList.map((r) => r.email));

      toast({
        title: "Emails Imported",
        description: `Added ${validEmails.length} valid emails${invalidEmails.length > 0 ? `, ${invalidEmails.length} invalid emails skipped` : ""}`,
      });
    }

    if (invalidEmails.length > 0) {
      toast({
        title: "Invalid Emails",
        description: `${invalidEmails.length} emails were invalid and skipped`,
        variant: "destructive",
      });
    }

    setBulkEmails("");
    setShowBulkImport(false);
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
          <Dialog open={showBulkImport} onOpenChange={setShowBulkImport}>
            <DialogTrigger asChild>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="flex items-center gap-1"
              >
                <Upload className="h-4 w-4" />
                Import
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-md">
              <DialogHeader>
                <DialogTitle>Bulk Import Recipients</DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                <div>
                  <Label htmlFor="bulk-emails">Email Addresses</Label>
                  <Textarea
                    id="bulk-emails"
                    placeholder="Enter emails separated by commas, semicolons, or new lines"
                    value={bulkEmails}
                    onChange={(e) => setBulkEmails(e.target.value)}
                    className="min-h-[120px] mt-2"
                  />
                </div>
                <div className="flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setShowBulkImport(false)}
                  >
                    Cancel
                  </Button>
                  <Button type="button" onClick={handleBulkImport}>
                    Import Emails
                  </Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>

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
    </div>
  );
}
