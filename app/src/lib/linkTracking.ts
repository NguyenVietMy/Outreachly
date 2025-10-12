/**
 * Utility functions for link detection and URL wrapping with double masking
 */

import { shortenUrl } from "./linkShortener";

// URL regex that handles common patterns including punctuation
const URL_REGEX = /https?:\/\/[^\s<>"{}|\\^`\[\]]+/gi;

/**
 * Detect URLs in text content
 * @param text - The text to scan for URLs
 * @returns Array of detected URLs
 */
export function detectUrls(text: string): string[] {
  if (!text || typeof text !== "string") return [];

  const matches = text.match(URL_REGEX);
  if (!matches) return [];

  // Clean up URLs and remove duplicates
  const cleanedUrls = matches
    .map((url) => {
      // Remove trailing punctuation that might have been captured
      return url.replace(/[.,;:!?]+$/, "").trim();
    })
    .filter((url) => url.length > 0)
    .filter((url) => {
      try {
        new URL(url);
        return true;
      } catch {
        return false;
      }
    });
  return Array.from(new Set(cleanedUrls)); // Remove duplicates
}

/**
 * Check if text contains any URLs
 * @param text - The text to check
 * @returns true if URLs are found
 */
export function hasUrls(text: string): boolean {
  return detectUrls(text).length > 0;
}

/**
 * Replace URLs in text with double-masked tracked versions (async)
 * @param text - The original text
 * @param messageId - The message ID for tracking
 * @param userId - The user ID for tracking
 * @param campaignId - Optional campaign ID
 * @param orgId - Optional organization ID
 * @returns Promise that resolves to text with URLs replaced with double-masked tracked versions
 */
export async function replaceUrlsWithTracking(
  text: string,
  messageId: string,
  userId: string,
  campaignId?: string,
  orgId?: string,
  recipientEmail?: string
): Promise<string> {
  const urlMatches = text.match(URL_REGEX);
  if (!urlMatches) return text;

  let processedText = text;

  for (const url of urlMatches) {
    // Clean the URL before wrapping (remove trailing punctuation)
    const cleanUrl = url.replace(/[.,;:!?]+$/, "").trim();

    // Validate the URL before wrapping
    try {
      new URL(cleanUrl);
      // Use double masking: original URL -> short URL -> tracking URL
      const trackingUrl = await shortenUrl(
        cleanUrl,
        messageId,
        userId,
        campaignId,
        orgId,
        recipientEmail
      );
      processedText = processedText.replace(url, trackingUrl);
    } catch {
      // If URL is invalid, keep original
      continue;
    }
  }

  return processedText;
}

/**
 * Restore original URLs from tracked versions
 * @param text - Text with tracked URLs
 * @returns Text with original URLs restored
 */
export function restoreOriginalUrls(text: string): string {
  // This would need to parse the tracking URLs and extract original URLs
  // For now, return as-is - this could be enhanced later
  return text;
}
