/**
 * Email validation utilities for extracting and validating personalization variables
 */

export interface VariableValidationResult {
  requiredVariables: string[];
  totalVariables: number;
  emailContent: string;
}

export interface LeadValidationResult {
  leadId: string;
  email: string;
  isValid: boolean;
  missingVariables: string[];
  firstName?: string;
  lastName?: string;
  companyName?: string;
}

export interface ValidationResponse {
  validLeads: LeadValidationResult[];
  invalidLeads: LeadValidationResult[];
  totalLeads: number;
  validCount: number;
  invalidCount: number;
}

/**
 * Extract all personalization variables from email content
 * @param content - Email subject and body content
 * @returns Array of unique variable names
 */
export const extractVariables = (content: string): string[] => {
  if (!content) return [];

  // Regex to match {{variableName}} pattern
  const regex = /\{\{([^}]+)\}\}/g;
  const matches = content.match(regex);

  if (!matches) return [];

  // Extract variable names and remove duplicates
  const variables = matches.map((match) => match.slice(2, -2).trim());
  return Array.from(new Set(variables));
};

/**
 * Extract variables from both subject and body
 * @param subject - Email subject
 * @param body - Email body
 * @returns VariableValidationResult with extracted variables
 */
export const extractVariablesFromEmail = (
  subject: string,
  body: string
): VariableValidationResult => {
  const combinedContent = `${subject || ""} ${body || ""}`;
  const requiredVariables = extractVariables(combinedContent);

  return {
    requiredVariables,
    totalVariables: requiredVariables.length,
    emailContent: combinedContent,
  };
};

/**
 * Validate if a lead has all required variables
 * @param lead - Lead object
 * @param requiredVariables - Array of required variable names
 * @returns LeadValidationResult
 */
export const validateLead = (
  lead: any,
  requiredVariables: string[]
): LeadValidationResult => {
  const missingVariables: string[] = [];

  // Check each required variable
  requiredVariables.forEach((variable) => {
    const value = getLeadVariableValue(lead, variable);
    if (!value || value.trim() === "") {
      missingVariables.push(variable);
    }
  });

  return {
    leadId: lead.id,
    email: lead.email,
    isValid: missingVariables.length === 0,
    missingVariables,
    firstName: lead.firstName,
    lastName: lead.lastName,
    companyName: lead.companyName || lead.domain,
  };
};

/**
 * Get the value of a specific variable from a lead
 * @param lead - Lead object
 * @param variable - Variable name
 * @returns Variable value or empty string
 */
const getLeadVariableValue = (lead: any, variable: string): string => {
  switch (variable.toLowerCase()) {
    case "firstname":
      return lead.firstName || "";
    case "lastname":
      return lead.lastName || "";
    case "fullname":
      return `${lead.firstName || ""} ${lead.lastName || ""}`.trim();
    case "companyname":
      return lead.companyName || lead.domain || "";
    case "domain":
      return lead.domain || "";
    case "position":
      return lead.position || "";
    case "title":
      return lead.position || ""; // title is same as position
    case "linkedinurl":
    case "linkedin":
      return lead.linkedinUrl || "";
    case "email":
      return lead.email || "";
    case "phone":
      return lead.phone || "";
    default:
      return "";
  }
};

/**
 * Validate multiple leads against required variables
 * @param leads - Array of lead objects
 * @param requiredVariables - Array of required variable names
 * @returns ValidationResponse with validation results
 */
export const validateLeads = (
  leads: any[],
  requiredVariables: string[]
): ValidationResponse => {
  const results = leads.map((lead) => validateLead(lead, requiredVariables));

  const validLeads = results.filter((result) => result.isValid);
  const invalidLeads = results.filter((result) => !result.isValid);

  return {
    validLeads,
    invalidLeads,
    totalLeads: leads.length,
    validCount: validLeads.length,
    invalidCount: invalidLeads.length,
  };
};

/**
 * Get a human-readable description of missing variables
 * @param missingVariables - Array of missing variable names
 * @returns Formatted string describing missing variables
 */
export const getMissingVariablesDescription = (
  missingVariables: string[]
): string => {
  if (missingVariables.length === 0) return "All required data is available";

  if (missingVariables.length === 1) {
    return `Missing: ${missingVariables[0]}`;
  }

  if (missingVariables.length === 2) {
    return `Missing: ${missingVariables.join(" and ")}`;
  }

  return `Missing: ${missingVariables.slice(0, -1).join(", ")} and ${missingVariables[missingVariables.length - 1]}`;
};
