export type TemplatePlatform = "EMAIL" | "LINKEDIN";

export interface TemplateContentEmail {
  subject: string;
  body: string;
}

export interface TemplateContentLinkedIn {
  body: string;
}

export interface TemplateModel {
  id: string;
  orgId: string;
  name: string;
  platform: TemplatePlatform;
  category?: string | null;
  contentJson: string; // JSON string from API
  createdAt: string;
  updatedAt: string;
}

import { API_BASE_URL } from "./config";

export async function listTemplates(
  platform?: TemplatePlatform
): Promise<TemplateModel[]> {
  const url = new URL(`${API_BASE_URL}/api/templates`);
  if (platform) url.searchParams.set("platform", platform);
  const res = await fetch(url.toString(), { credentials: "include" });
  if (!res.ok) throw new Error("Failed to load templates");
  return res.json();
}

export async function getTemplate(id: string): Promise<TemplateModel> {
  const res = await fetch(`${API_BASE_URL}/api/templates/${id}`, {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to load template");
  return res.json();
}

export async function createTemplate(input: {
  name: string;
  platform: TemplatePlatform;
  category?: string;
  content: TemplateContentEmail | TemplateContentLinkedIn;
}): Promise<TemplateModel> {
  const res = await fetch(`${API_BASE_URL}/api/templates`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data?.error || "Failed to create template");
  return data;
}

export async function updateTemplate(
  id: string,
  input: {
    name?: string;
    category?: string;
    platform?: TemplatePlatform;
    content?: TemplateContentEmail | TemplateContentLinkedIn;
  }
): Promise<TemplateModel> {
  const res = await fetch(`${API_BASE_URL}/api/templates/${id}`, {
    method: "PUT",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data?.error || "Failed to update template");
  return data;
}

export async function deleteTemplate(id: string): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/api/templates/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to delete template");
}

export function parseContent<T = any>(contentJson: string): T {
  try {
    return JSON.parse(contentJson) as T;
  } catch {
    return {} as T;
  }
}

export const ALLOWED_VARS = [
  "first_name",
  "last_name",
  "company",
  "title",
] as const;
export type AllowedVar = (typeof ALLOWED_VARS)[number];

export function insertVariable(
  text: string,
  variable: AllowedVar,
  selectionStart: number,
  selectionEnd: number
): {
  nextText: string;
  nextCursor: number;
} {
  const token = `{{${variable}}}`;
  const before = text.slice(0, selectionStart);
  const after = text.slice(selectionEnd);
  const nextText = `${before}${token}${after}`;
  const nextCursor = before.length + token.length;
  return { nextText, nextCursor };
}

export function countWords(text: string): number {
  return (text.trim().match(/\b\w+\b/g) || []).length;
}
