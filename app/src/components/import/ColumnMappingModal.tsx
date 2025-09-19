"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { CheckCircle, XCircle, AlertCircle, Settings } from "lucide-react";

interface CsvColumn {
  name: string;
  displayName: string;
  sampleValue: string;
  isRequired: boolean;
  currentMapping: string | null;
}

interface FieldOption {
  value: string;
  label: string;
  description: string;
  isRequired: boolean;
  category: string;
}

interface ColumnMappingData {
  detectedColumns: CsvColumn[];
  availableFields: FieldOption[];
  mapping: Record<string, string>;
  hasRequiredFields: boolean;
  missingRequiredFields: string[];
}

interface ColumnMappingModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (mapping: Record<string, string>) => void;
  data: ColumnMappingData | null;
  isLoading: boolean;
}

export function ColumnMappingModal({
  isOpen,
  onClose,
  onConfirm,
  data,
  isLoading,
}: ColumnMappingModalProps) {
  const [mapping, setMapping] = useState<Record<string, string>>({});
  const [hasChanges, setHasChanges] = useState(false);

  useEffect(() => {
    if (data) {
      setMapping(data.mapping);
      setHasChanges(false);
    }
  }, [data]);

  const handleMappingChange = (columnName: string, fieldValue: string) => {
    // Check if this field is already used by another column
    const isAlreadyUsed = Object.entries(mapping).some(
      ([col, field]) =>
        col !== columnName &&
        field === fieldValue &&
        fieldValue !== "custom_field" &&
        fieldValue !== "skip"
    );

    if (
      isAlreadyUsed &&
      fieldValue !== "custom_field" &&
      fieldValue !== "skip"
    ) {
      // Don't allow duplicate standard fields
      return;
    }

    const newMapping = { ...mapping, [columnName]: fieldValue };

    // Double-check: ensure no duplicates after the change
    const usedFields = new Set<string>();
    const hasDuplicates = Object.values(newMapping).some((value) => {
      if (value && value !== "custom_field" && value !== "skip") {
        if (usedFields.has(value)) {
          return true; // Found duplicate
        }
        usedFields.add(value);
      }
      return false;
    });

    if (hasDuplicates) {
      // Don't apply the change if it creates duplicates
      return;
    }

    setMapping(newMapping);
    setHasChanges(true);
  };

  const handleAutoMapExtraFields = () => {
    if (!data) return;

    // Start fresh - only keep valid existing mappings
    const newMapping: Record<string, string> = {};
    const usedFields = new Set<string>();

    // First, preserve any valid existing mappings
    data.detectedColumns.forEach((column) => {
      const currentValue = mapping[column.name];
      if (
        currentValue &&
        currentValue !== "" &&
        data.availableFields.some((field) => field.value === currentValue) &&
        !usedFields.has(currentValue)
      ) {
        newMapping[column.name] = currentValue;
        usedFields.add(currentValue);
      }
    });

    // Then, assign unmapped columns to available fields
    data.detectedColumns.forEach((column) => {
      if (!newMapping[column.name]) {
        // Find next available standard field
        const availableStandardFields = data.availableFields.filter(
          (field) =>
            field.value !== "custom_field" &&
            field.value !== "skip" &&
            !usedFields.has(field.value)
        );

        if (availableStandardFields.length > 0) {
          // Use the first available standard field
          const fieldToUse = availableStandardFields[0];
          newMapping[column.name] = fieldToUse.value;
          usedFields.add(fieldToUse.value);
        } else {
          // Fall back to custom_field
          newMapping[column.name] = "custom_field";
        }
      }
    });

    setMapping(newMapping);
    setHasChanges(true);
  };

  const handleConfirm = () => {
    onConfirm(mapping);
    setHasChanges(false);
  };

  const getFieldOptionByValue = (value: string) => {
    return data?.availableFields.find((field) => field.value === value);
  };

  const getCategoryColor = (category: string) => {
    switch (category) {
      case "required":
        return "bg-red-100 text-red-800 border-red-200";
      case "personal":
        return "bg-blue-100 text-blue-800 border-blue-200";
      case "company":
        return "bg-purple-100 text-purple-800 border-purple-200";
      case "location":
        return "bg-green-100 text-green-800 border-green-200";
      case "social":
        return "bg-pink-100 text-pink-800 border-pink-200";
      case "technical":
        return "bg-orange-100 text-orange-800 border-orange-200";
      case "verification":
        return "bg-yellow-100 text-yellow-800 border-yellow-200";
      case "meta":
        return "bg-indigo-100 text-indigo-800 border-indigo-200";
      case "custom":
        return "bg-emerald-100 text-emerald-800 border-emerald-200";
      case "skip":
        return "bg-gray-100 text-gray-800 border-gray-200";
      default:
        return "bg-gray-100 text-gray-800 border-gray-200";
    }
  };

  const getRequiredFieldsStatus = () => {
    if (!data) return { hasAllRequired: false, missing: [] };

    const requiredFields = data.availableFields.filter(
      (field) => field.isRequired
    );
    const mappedRequiredFields = requiredFields.filter((field) =>
      Object.values(mapping).includes(field.value)
    );

    return {
      hasAllRequired: mappedRequiredFields.length === requiredFields.length,
      missing: requiredFields.filter(
        (field) => !Object.values(mapping).includes(field.value)
      ),
    };
  };

  const requiredStatus = getRequiredFieldsStatus();

  if (!data) return null;

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Settings className="h-5 w-5" />
            Map CSV Columns to Fields
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-6">
          {/* Status Alert */}
          {!requiredStatus.hasAllRequired && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>
                <div>
                  <p className="font-medium mb-2">Required fields missing:</p>
                  <ul className="list-disc list-inside space-y-1">
                    {requiredStatus.missing.map((field) => (
                      <li key={field.value} className="text-sm">
                        {field.label}
                      </li>
                    ))}
                  </ul>
                </div>
              </AlertDescription>
            </Alert>
          )}

          {requiredStatus.hasAllRequired && (
            <Alert>
              <CheckCircle className="h-4 w-4" />
              <AlertDescription>
                All required fields are mapped! Ready to import.
              </AlertDescription>
            </Alert>
          )}

          {/* Auto Map Button */}
          <div className="flex justify-between items-center">
            <div>
              <h3 className="font-medium">Column Mapping</h3>
              <p className="text-sm text-muted-foreground">
                Map your CSV columns to our field types. Unmapped columns will
                be stored as custom data.
              </p>
            </div>
            <Button
              onClick={handleAutoMapExtraFields}
              variant="outline"
              size="sm"
            >
              Auto Map Extra Fields
            </Button>
          </div>

          {/* Mapping Table */}
          <div className="border rounded-lg overflow-hidden">
            <div className="bg-muted px-4 py-3 border-b">
              <div className="grid grid-cols-10 gap-4 text-sm font-medium">
                <div className="col-span-4">Your CSV Column</div>
                <div className="col-span-2">Sample Value</div>
                <div className="col-span-4">Map to Field</div>
              </div>
            </div>
            <div className="divide-y">
              {data.detectedColumns.map((column, index) => {
                const currentField = getFieldOptionByValue(
                  mapping[column.name]
                );
                const currentValue = mapping[column.name];
                const isMapped =
                  currentValue &&
                  currentValue !== "" &&
                  currentValue !== "custom_field" &&
                  currentValue !== "skip" &&
                  data.availableFields.some(
                    (field) => field.value === currentValue
                  );
                return (
                  <div
                    key={index}
                    className="px-4 py-3 grid grid-cols-10 gap-4 items-center"
                  >
                    <div className="col-span-4">
                      <div className="font-medium">{column.displayName}</div>
                      {column.isRequired && (
                        <Badge variant="destructive" className="text-xs mt-1">
                          Required
                        </Badge>
                      )}
                    </div>
                    <div className="col-span-2 text-sm text-muted-foreground truncate">
                      {column.sampleValue || "—"}
                    </div>
                    <div className="col-span-4">
                      <Select
                        value={
                          mapping[column.name] && mapping[column.name] !== ""
                            ? mapping[column.name]
                            : undefined
                        }
                        onValueChange={(value) =>
                          handleMappingChange(column.name, value)
                        }
                      >
                        <SelectTrigger
                          className={
                            isMapped ? "border-green-500 bg-green-50" : ""
                          }
                        >
                          <SelectValue placeholder="Select field..." />
                        </SelectTrigger>
                        <SelectContent>
                          {data.availableFields
                            .sort((a, b) => {
                              // Sort by category first, then by label
                              const categoryOrder = {
                                required: 0,
                                personal: 1,
                                company: 2,
                                location: 3,
                                social: 4,
                                technical: 5,
                                verification: 6,
                                meta: 7,
                                custom: 8,
                                skip: 9,
                              };
                              const categoryA =
                                categoryOrder[
                                  a.category as keyof typeof categoryOrder
                                ] ?? 999;
                              const categoryB =
                                categoryOrder[
                                  b.category as keyof typeof categoryOrder
                                ] ?? 999;

                              if (categoryA !== categoryB) {
                                return categoryA - categoryB;
                              }
                              return a.label.localeCompare(b.label);
                            })
                            .map((field) => {
                              const isSelected = Object.values(
                                mapping
                              ).includes(field.value);
                              const isCurrentlySelected =
                                mapping[column.name] === field.value;
                              const shouldShowCheckmark =
                                isSelected && field.value !== "custom_field";
                              const isDisabled =
                                isSelected &&
                                !isCurrentlySelected &&
                                field.value !== "custom_field" &&
                                field.value !== "skip";

                              return (
                                <SelectItem
                                  key={field.value}
                                  value={field.value}
                                  disabled={isDisabled}
                                >
                                  <div className="flex items-center justify-between w-full">
                                    <div className="flex items-center gap-2">
                                      <span
                                        className={
                                          isDisabled ? "text-gray-400" : ""
                                        }
                                      >
                                        {field.label}
                                      </span>
                                      <Badge
                                        variant="outline"
                                        className={`text-xs ${getCategoryColor(field.category)}`}
                                      >
                                        {field.category}
                                      </Badge>
                                      {shouldShowCheckmark && (
                                        <span className="text-green-600">
                                          ✅
                                        </span>
                                      )}
                                    </div>
                                    {field.isRequired && (
                                      <Badge
                                        variant="destructive"
                                        className="text-xs ml-2"
                                      >
                                        Required
                                      </Badge>
                                    )}
                                  </div>
                                </SelectItem>
                              );
                            })}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex justify-between items-center pt-4 border-t">
            <div className="text-sm text-muted-foreground">
              {data.detectedColumns.length} columns detected
            </div>
            <div className="flex gap-2">
              <Button variant="outline" onClick={onClose}>
                Cancel
              </Button>
              <Button
                onClick={handleConfirm}
                disabled={!requiredStatus.hasAllRequired || isLoading}
              >
                {isLoading ? "Processing..." : "Confirm & Import"}
              </Button>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
