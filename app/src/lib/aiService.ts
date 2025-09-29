const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface GenerateTemplateRequest {
  prompt: string;
  platform: "EMAIL" | "LINKEDIN";
  category?: string;
  tone?: string;
}

export interface ImproveTemplateRequest {
  currentTemplate: string;
  platform: "EMAIL" | "LINKEDIN";
  improvementType:
    | "shorter"
    | "longer"
    | "more professional"
    | "more casual"
    | "higher conversion";
}

export interface AiResponse {
  success: boolean;
  data?: string;
  error?: string;
}

export async function generateTemplate(
  request: GenerateTemplateRequest
): Promise<AiResponse> {
  try {
    const response = await fetch(`${API_URL}/api/ai/generate-template`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(request),
    });

    const data = await response.json();
    return data;
  } catch (error) {
    console.error("Error generating template:", error);
    return {
      success: false,
      error: "Failed to generate template. Please try again.",
    };
  }
}

export async function improveTemplate(
  request: ImproveTemplateRequest
): Promise<AiResponse> {
  try {
    const response = await fetch(`${API_URL}/api/ai/improve-template`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(request),
    });

    const data = await response.json();
    return data;
  } catch (error) {
    console.error("Error improving template:", error);
    return {
      success: false,
      error: "Failed to improve template. Please try again.",
    };
  }
}

export const TEMPLATE_CATEGORIES = [
  "Cold Outreach",
  "Follow-up",
  "Thank You",
  "Sales",
  "Marketing",
  "Partnership",
  "Event Invitation",
  "Product Demo",
  "Newsletter",
  "Customer Support",
] as const;

export const TONE_OPTIONS = [
  "Professional",
  "Casual",
  "Friendly",
  "Urgent",
  "Formal",
  "Conversational",
  "Enthusiastic",
  "Direct",
] as const;

export const IMPROVEMENT_TYPES = [
  { value: "shorter", label: "Make Shorter" },
  { value: "longer", label: "Make Longer" },
  { value: "more professional", label: "More Professional" },
  { value: "more casual", label: "More Casual" },
  { value: "higher conversion", label: "Higher Conversion" },
] as const;
