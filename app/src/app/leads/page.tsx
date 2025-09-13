"use client";

import { useState } from "react";
import DashboardLayout from "@/components/DashboardLayout";
import AuthGuard from "@/components/AuthGuard";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Search,
  Filter,
  Plus,
  Mail,
  Eye,
  History,
  Download,
  Users,
} from "lucide-react";

// Mock data for demonstration
const mockLeads = [
  {
    id: "1",
    firstName: "John",
    lastName: "Doe",
    company: "Tech Corp",
    title: "CEO",
    email: "john.doe@techcorp.com",
    customTextField: "VIP Client",
  },
  {
    id: "2",
    firstName: "Jane",
    lastName: "Smith",
    company: "Design Studio",
    title: "Designer",
    email: "jane@designstudio.com",
    customTextField: "",
  },
  {
    id: "3",
    firstName: "Bob",
    lastName: "Wilson",
    company: "Marketing Inc",
    title: "Marketing Manager",
    email: "bob.wilson@marketinginc.com",
    customTextField: "Cold Lead",
  },
];

const mockTemplates = [
  { id: "1", name: "Welcome Email", description: "Initial outreach template" },
  { id: "2", name: "Follow-up Email", description: "Second touch template" },
  { id: "3", name: "Product Demo", description: "Demo request template" },
];

const mockActivities = [
  {
    id: "1",
    email: "john.doe@techcorp.com",
    operationStatus: "sent",
    emailStatus: "delivered",
    timestamp: "2024-01-15 10:30 AM",
  },
  {
    id: "2",
    email: "jane@designstudio.com",
    operationStatus: "sending",
    emailStatus: "pending",
    timestamp: "2024-01-15 11:15 AM",
  },
  {
    id: "3",
    email: "bob.wilson@marketinginc.com",
    operationStatus: "sent",
    emailStatus: "bounced",
    timestamp: "2024-01-15 09:45 AM",
  },
];

export default function LeadsPage() {
  const [selectedLeads, setSelectedLeads] = useState<string[]>([]);
  const [showLeadModal, setShowLeadModal] = useState(false);
  const [selectedLead, setSelectedLead] = useState<any>(null);
  const [showTemplatePreview, setShowTemplatePreview] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<any>(null);
  const [searchTerm, setSearchTerm] = useState("");

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedLeads(mockLeads.map((lead) => lead.id));
    } else {
      setSelectedLeads([]);
    }
  };

  const handleSelectLead = (leadId: string, checked: boolean) => {
    if (checked) {
      setSelectedLeads((prev) => [...prev, leadId]);
    } else {
      setSelectedLeads((prev) => prev.filter((id) => id !== leadId));
    }
  };

  const handleLeadClick = (lead: any) => {
    setSelectedLead(lead);
    setShowLeadModal(true);
  };

  const handleTemplatePreview = (template: any) => {
    setSelectedTemplate(template);
    setShowTemplatePreview(true);
  };

  const getStatusBadge = (status: string) => {
    const variants: any = {
      sent: "default",
      sending: "secondary",
      delivered: "default",
      bounced: "destructive",
      pending: "secondary",
    };
    return <Badge variant={variants[status] || "secondary"}>{status}</Badge>;
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6 max-w-7xl mx-auto">
          {/* Header with Create Campaign Button */}
          <div className="flex justify-between items-center mb-6 mt-[100px]">
            <div>
              <h1 className="text-3xl font-bold">Leads</h1>
              <p className="text-sm text-muted-foreground mt-1">
                Manage your leads, track activities, and create campaigns
              </p>
            </div>
            <Button className="bg-blue-600 hover:bg-blue-700">
              <Plus className="w-4 h-4 mr-2" />
              Create Campaign
            </Button>
          </div>

          {/* Leads Table Section */}
          <Card className="mb-6">
            <CardHeader>
              <div className="flex justify-between items-center">
                <CardTitle>Your Leads</CardTitle>
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
                  <Button variant="outline">
                    <Filter className="w-4 h-4 mr-2" />
                    Filter
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex gap-2 mb-4">
                <Button
                  variant={selectedLeads.length > 1 ? "default" : "outline"}
                  disabled={selectedLeads.length === 0}
                >
                  {selectedLeads.length > 1 ? "Bulk " : ""}Enrich
                </Button>
                <Button
                  variant={selectedLeads.length > 1 ? "default" : "outline"}
                  disabled={selectedLeads.length === 0}
                >
                  {selectedLeads.length > 1 ? "Bulk " : ""}Verify
                </Button>
                <Button
                  variant={selectedLeads.length > 1 ? "default" : "outline"}
                  disabled={selectedLeads.length === 0}
                >
                  {selectedLeads.length > 1 ? "Bulk " : ""}Export
                </Button>
              </div>

              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-12">
                      <Checkbox
                        checked={selectedLeads.length === mockLeads.length}
                        onCheckedChange={handleSelectAll}
                      />
                    </TableHead>
                    <TableHead>First Name</TableHead>
                    <TableHead>Last Name</TableHead>
                    <TableHead>Company</TableHead>
                    <TableHead>Title</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Custom Field</TableHead>
                    <TableHead>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {mockLeads.map((lead) => (
                    <TableRow
                      key={lead.id}
                      className="cursor-pointer hover:bg-gray-50"
                    >
                      <TableCell>
                        <Checkbox
                          checked={selectedLeads.includes(lead.id)}
                          onCheckedChange={(checked) =>
                            handleSelectLead(lead.id, checked as boolean)
                          }
                        />
                      </TableCell>
                      <TableCell onClick={() => handleLeadClick(lead)}>
                        {lead.firstName}
                      </TableCell>
                      <TableCell onClick={() => handleLeadClick(lead)}>
                        {lead.lastName}
                      </TableCell>
                      <TableCell onClick={() => handleLeadClick(lead)}>
                        {lead.company}
                      </TableCell>
                      <TableCell onClick={() => handleLeadClick(lead)}>
                        {lead.title}
                      </TableCell>
                      <TableCell onClick={() => handleLeadClick(lead)}>
                        {lead.email}
                      </TableCell>
                      <TableCell onClick={() => handleLeadClick(lead)}>
                        {lead.customTextField || "-"}
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-1">
                          <Button size="sm" variant="ghost">
                            <Eye className="w-4 h-4" />
                          </Button>
                          <Button size="sm" variant="ghost">
                            <Mail className="w-4 h-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Pagination Placeholder */}
              <div className="flex justify-between items-center mt-4">
                <div className="text-sm text-muted-foreground">
                  Showing 1-3 of 3 leads
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" disabled>
                    Previous
                  </Button>
                  <Button variant="outline" disabled>
                    Next
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>

          <hr className="my-8" />

          {/* Activity Tracker Section */}
          <Card className="mb-6">
            <CardHeader>
              <CardTitle>Activity Tracker</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Email Address</TableHead>
                    <TableHead>Operation Status</TableHead>
                    <TableHead>Email Status</TableHead>
                    <TableHead>Timestamp</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {mockActivities.map((activity) => (
                    <TableRow key={activity.id}>
                      <TableCell>{activity.email}</TableCell>
                      <TableCell>
                        {getStatusBadge(activity.operationStatus)}
                      </TableCell>
                      <TableCell>
                        {getStatusBadge(activity.emailStatus)}
                      </TableCell>
                      <TableCell>{activity.timestamp}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <hr className="my-8" />

          {/* Templates Section */}
          <Card className="mb-6">
            <CardHeader>
              <div className="flex justify-between items-center">
                <CardTitle>Recent Templates</CardTitle>
                <Button variant="outline">View All Templates</Button>
              </div>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {mockTemplates.map((template) => (
                  <div
                    key={template.id}
                    className="p-4 border rounded-lg cursor-pointer hover:bg-gray-50"
                    onClick={() => handleTemplatePreview(template)}
                  >
                    <h3 className="font-medium">{template.name}</h3>
                    <p className="text-sm text-muted-foreground mt-1">
                      {template.description}
                    </p>
                    <Button size="sm" variant="ghost" className="mt-2">
                      <Eye className="w-4 h-4 mr-1" />
                      Preview
                    </Button>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          <hr className="my-8" />

          {/* Health & Metrics Section */}
          <Card>
            <CardHeader>
              <CardTitle>Health & Metrics</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="text-center p-4 border rounded-lg">
                  <div className="text-2xl font-bold text-blue-600">1,234</div>
                  <div className="text-sm text-muted-foreground">
                    Total Leads
                  </div>
                  {/* Placeholder graph */}
                  <div className="mt-2 h-8 bg-gray-100 rounded flex items-end justify-center">
                    <div className="w-2 h-6 bg-blue-600 rounded"></div>
                    <div className="w-2 h-4 bg-blue-600 rounded ml-1"></div>
                    <div className="w-2 h-5 bg-blue-600 rounded ml-1"></div>
                  </div>
                </div>
                <div className="text-center p-4 border rounded-lg">
                  <div className="text-2xl font-bold text-green-600">85%</div>
                  <div className="text-sm text-muted-foreground">
                    Verified %
                  </div>
                  {/* Placeholder graph */}
                  <div className="mt-2 h-8 bg-gray-100 rounded flex items-end justify-center">
                    <div className="w-2 h-7 bg-green-600 rounded"></div>
                    <div className="w-2 h-5 bg-green-600 rounded ml-1"></div>
                    <div className="w-2 h-6 bg-green-600 rounded ml-1"></div>
                  </div>
                </div>
                <div className="text-center p-4 border rounded-lg">
                  <div className="text-2xl font-bold text-red-600">23</div>
                  <div className="text-sm text-muted-foreground">
                    Suppressed Count
                  </div>
                  {/* Placeholder graph */}
                  <div className="mt-2 h-8 bg-gray-100 rounded flex items-end justify-center">
                    <div className="w-2 h-3 bg-red-600 rounded"></div>
                    <div className="w-2 h-2 bg-red-600 rounded ml-1"></div>
                    <div className="w-2 h-4 bg-red-600 rounded ml-1"></div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Lead Detail Modal */}
        <Dialog open={showLeadModal} onOpenChange={setShowLeadModal}>
          <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>
                Lead Details - {selectedLead?.firstName}{" "}
                {selectedLead?.lastName}
              </DialogTitle>
            </DialogHeader>
            <Tabs defaultValue="details" className="w-full">
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="details">Details</TabsTrigger>
                <TabsTrigger value="verify">Verify</TabsTrigger>
                <TabsTrigger value="history">History</TabsTrigger>
              </TabsList>
              <TabsContent value="details" className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-sm font-medium">First Name</label>
                    <Input value={selectedLead?.firstName || ""} />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Last Name</label>
                    <Input value={selectedLead?.lastName || ""} />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Company</label>
                    <Input value={selectedLead?.company || ""} />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Title</label>
                    <Input value={selectedLead?.title || ""} />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Email</label>
                    <Input value={selectedLead?.email || ""} />
                  </div>
                  <div>
                    <label className="text-sm font-medium">Custom Field</label>
                    <Input value={selectedLead?.customTextField || ""} />
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button>Save Changes</Button>
                  <Button variant="outline">Cancel</Button>
                </div>
              </TabsContent>
              <TabsContent value="verify">
                <div className="space-y-4">
                  <p className="text-muted-foreground">
                    Verification options will go here
                  </p>
                  <Button>Verify Email</Button>
                </div>
              </TabsContent>
              <TabsContent value="history">
                <div className="space-y-4">
                  <p className="text-muted-foreground">
                    Enrichment history will be displayed here
                  </p>
                </div>
              </TabsContent>
            </Tabs>
          </DialogContent>
        </Dialog>

        {/* Template Preview Modal */}
        <Dialog
          open={showTemplatePreview}
          onOpenChange={setShowTemplatePreview}
        >
          <DialogContent className="max-w-2xl">
            <DialogHeader>
              <DialogTitle>
                Template Preview - {selectedTemplate?.name}
              </DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <p className="text-muted-foreground">
                {selectedTemplate?.description}
              </p>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm">
                  [Template content would be displayed here]
                </p>
              </div>
              <div className="flex gap-2">
                <Button>Use Template</Button>
                <Button variant="outline">Close</Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      </DashboardLayout>
    </AuthGuard>
  );
}
