"use client";

import React from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  CheckCircle,
  XCircle,
  AlertTriangle,
  Mail,
  Users,
  X,
} from "lucide-react";
import {
  LeadValidationResult,
  ValidationResponse,
  getMissingVariablesDescription,
} from "@/lib/emailValidation";

interface EmailValidationModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  validationResults: ValidationResponse | null;
  onProceed: () => void;
  onCancel: () => void;
  onSkipInvalid: () => void;
}

export function EmailValidationModal({
  open,
  onOpenChange,
  validationResults,
  onProceed,
  onCancel,
  onSkipInvalid,
}: EmailValidationModalProps) {
  if (!validationResults) return null;

  const { validLeads, invalidLeads, totalLeads, validCount, invalidCount } = validationResults;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Mail className="h-5 w-5 text-blue-600" />
            Email Validation Results
          </DialogTitle>
          <DialogDescription>
            Review the validation results before sending emails. Some leads may be missing required personalization data.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {/* Summary Stats */}
          <div className="grid grid-cols-3 gap-4">
            <div className="text-center p-4 bg-blue-50 rounded-lg">
              <div className="text-2xl font-bold text-blue-600">{totalLeads}</div>
              <div className="text-sm text-gray-600">Total Leads</div>
            </div>
            <div className="text-center p-4 bg-green-50 rounded-lg">
              <div className="text-2xl font-bold text-green-600">{validCount}</div>
              <div className="text-sm text-gray-600">Valid</div>
            </div>
            <div className="text-center p-4 bg-red-50 rounded-lg">
              <div className="text-2xl font-bold text-red-600">{invalidCount}</div>
              <div className="text-sm text-gray-600">Invalid</div>
            </div>
          </div>

          {/* Warning for invalid leads */}
          {invalidCount > 0 && (
            <Alert className="border-orange-200 bg-orange-50">
              <AlertTriangle className="h-4 w-4 text-orange-600" />
              <AlertDescription className="text-orange-800">
                <strong>Warning:</strong> {invalidCount} lead{invalidCount > 1 ? 's' : ''} {invalidCount > 1 ? 'are' : 'is'} missing required personalization data. 
                These leads will receive emails with empty placeholders (e.g., "Hi ," instead of "Hi John,").
              </AlertDescription>
            </Alert>
          )}

          {/* Valid Leads Table */}
          {validLeads.length > 0 && (
            <div className="space-y-3">
              <h3 className="text-lg font-semibold flex items-center gap-2 text-green-700">
                <CheckCircle className="h-5 w-5" />
                Valid Leads ({validCount})
              </h3>
              <div className="border rounded-lg overflow-hidden">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Email</TableHead>
                      <TableHead>Company</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {validLeads.map((lead) => (
                      <TableRow key={lead.leadId}>
                        <TableCell className="font-medium">
                          {lead.firstName} {lead.lastName}
                        </TableCell>
                        <TableCell>{lead.email}</TableCell>
                        <TableCell>{lead.companyName}</TableCell>
                        <TableCell>
                          <Badge variant="default" className="bg-green-100 text-green-800">
                            <CheckCircle className="h-3 w-3 mr-1" />
                            Ready
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          )}

          {/* Invalid Leads Table */}
          {invalidLeads.length > 0 && (
            <div className="space-y-3">
              <h3 className="text-lg font-semibold flex items-center gap-2 text-red-700">
                <XCircle className="h-5 w-5" />
                Invalid Leads ({invalidCount})
              </h3>
              <div className="border rounded-lg overflow-hidden">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Email</TableHead>
                      <TableHead>Company</TableHead>
                      <TableHead>Missing Data</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {invalidLeads.map((lead) => (
                      <TableRow key={lead.leadId}>
                        <TableCell className="font-medium">
                          {lead.firstName} {lead.lastName}
                        </TableCell>
                        <TableCell>{lead.email}</TableCell>
                        <TableCell>{lead.companyName}</TableCell>
                        <TableCell>
                          <span className="text-sm text-red-600">
                            {getMissingVariablesDescription(lead.missingVariables)}
                          </span>
                        </TableCell>
                        <TableCell>
                          <Badge variant="destructive">
                            <XCircle className="h-3 w-3 mr-1" />
                            Missing Data
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          )}

          {/* No leads selected */}
          {totalLeads === 0 && (
            <div className="text-center py-8 text-gray-500">
              <Users className="h-12 w-12 mx-auto mb-4 text-gray-400" />
              <p className="text-lg font-medium">No leads selected</p>
              <p className="text-sm">Please select leads to send emails to.</p>
            </div>
          )}
        </div>

        <DialogFooter className="flex gap-2">
          <Button variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          {invalidCount > 0 && (
            <Button variant="secondary" onClick={onSkipInvalid}>
              Send to Valid Only ({validCount})
            </Button>
          )}
          <Button onClick={onProceed}>
            {invalidCount > 0 ? `Send to All (${totalLeads})` : `Send to All (${validCount})`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
