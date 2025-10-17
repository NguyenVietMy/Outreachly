import { useState } from "react";
import { API_BASE_URL } from "@/lib/config";
import { ValidationResponse } from "@/lib/emailValidation";

interface VariableExtractionRequest {
  subject: string;
  body: string;
}

interface VariableExtractionResponse {
  requiredVariables: string[];
  totalVariables: number;
  emailContent: string;
}

interface LeadValidationRequest {
  leadIds: string[];
  orgId: string;
  requiredVariables: string[];
}

export function useEmailValidation() {
  const [isValidating, setIsValidating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const extractVariables = async (
    subject: string,
    body: string
  ): Promise<VariableExtractionResponse | null> => {
    try {
      setIsValidating(true);
      setError(null);

      const response = await fetch(
        `${API_BASE_URL}/api/emails/extract-variables`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify({ subject, body }),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to extract variables: ${response.statusText}`);
      }

      const data = await response.json();
      return data;
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to extract variables"
      );
      return null;
    } finally {
      setIsValidating(false);
    }
  };

  const validateLeads = async (
    leadIds: string[],
    orgId: string,
    requiredVariables: string[]
  ): Promise<ValidationResponse | null> => {
    try {
      setIsValidating(true);
      setError(null);

      const response = await fetch(
        `${API_BASE_URL}/api/emails/validate-leads`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify({
            leadIds,
            orgId,
            requiredVariables,
          }),
        }
      );

      if (!response.ok) {
        throw new Error(`Failed to validate leads: ${response.statusText}`);
      }

      const data = await response.json();
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to validate leads");
      return null;
    } finally {
      setIsValidating(false);
    }
  };

  return {
    extractVariables,
    validateLeads,
    isValidating,
    error,
  };
}
