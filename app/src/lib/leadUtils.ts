import { API_BASE_URL } from "./config";

export interface HunterEmailData {
  value: string;
  type: string;
  confidence: number;
  first_name: string;
  last_name: string;
  position: string;
  position_raw: string;
  seniority: string | null;
  department: string | null;
  linkedin: string | null;
  twitter: string | null;
  phone_number: string | null;
  verification: {
    date: string;
    status: string;
  };
}

export interface LeadData {
  firstName: string;
  lastName: string;
  email: string;
  domain: string;
  position: string;
  positionRaw: string;
  seniority: string | null;
  department: string | null;
  linkedinUrl: string | null;
  confidenceScore: number;
  verifiedStatus: "unknown" | "valid" | "risky" | "invalid";
  emailType: "personal" | "generic" | "role" | "catch_all" | "unknown";
}

export function mapHunterEmailToLead(
  email: HunterEmailData,
  domain: string
): LeadData {
  return {
    firstName: email.first_name || "",
    lastName: email.last_name || "",
    email: email.value,
    domain: domain,
    position: email.position || "",
    positionRaw: email.position_raw || "",
    seniority: email.seniority,
    department: email.department,
    linkedinUrl: email.linkedin,
    confidenceScore: email.confidence,
    verifiedStatus: mapVerificationStatus(email.verification.status),
    emailType: mapEmailType(email.type),
  };
}

function mapVerificationStatus(
  hunterStatus: string
): "unknown" | "valid" | "risky" | "invalid" {
  switch (hunterStatus) {
    case "deliverable":
      return "valid";
    case "accept_all":
      return "risky";
    case "undeliverable":
      return "invalid";
    default:
      return "unknown";
  }
}

function mapEmailType(
  hunterType: string
): "personal" | "generic" | "role" | "catch_all" | "unknown" {
  switch (hunterType.toLowerCase()) {
    case "personal":
      return "personal";
    case "generic":
      return "generic";
    case "role":
      return "role";
    case "catch_all":
    case "catchall":
      return "catch_all";
    default:
      return "unknown";
  }
}

export async function createLeadsFromHunterData(
  emails: HunterEmailData[],
  domain: string,
  campaignId?: string
): Promise<{ success: boolean; leadIds?: string[]; error?: string }> {
  try {
    // Convert Hunter emails to Lead data
    const leadData = emails.map((email) => mapHunterEmailToLead(email, domain));

    // Create leads via API
    const requestBody = {
      leads: leadData,
      campaignId: campaignId || null,
    };

    const response = await fetch(`${API_BASE_URL}/api/leads/bulk-create`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(requestBody),
    });

    console.log("API Response status:", response.status);

    if (!response.ok) {
      const errorData = await response.json();
      console.error("API Error:", errorData);
      throw new Error(errorData.error || "Failed to create leads");
    }

    const result = await response.json();

    return {
      success: true,
      leadIds: result.leadIds || [],
    };
  } catch (error) {
    console.error("Error creating leads from Hunter data:", error);
    return {
      success: false,
      error: error instanceof Error ? error.message : "Unknown error occurred",
    };
  }
}

export async function addLeadsToCampaign(
  leadIds: string[],
  campaignId: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/leads/bulk-campaign`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({
        leadIds: leadIds,
        campaignId: campaignId,
      }),
    });

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.error || "Failed to add leads to campaign");
    }

    return { success: true };
  } catch (error) {
    console.error("Error adding leads to campaign:", error);
    return {
      success: false,
      error: error instanceof Error ? error.message : "Unknown error occurred",
    };
  }
}
