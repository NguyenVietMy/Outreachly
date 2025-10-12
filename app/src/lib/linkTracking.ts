/**
 * Utility functions for link detection and URL wrapping
 */

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
 * Wrap URLs with tracking parameters
 * @param originalUrl - The original URL to wrap
 * @param messageId - The message ID for tracking
 * @param userId - The user ID for tracking
 * @param campaignId - Optional campaign ID
 * @param orgId - Optional organization ID
 * @returns Wrapped tracking URL
 */
export function wrapUrlWithTracking(
  originalUrl: string,
  messageId: string,
  userId: string,
  campaignId?: string,
  orgId?: string
): string {
  const baseUrl = `${window.location.origin}/track/click`;
  const params = new URLSearchParams({
    url: originalUrl,
    msg: messageId,
    user: userId,
  });

  if (campaignId) params.append("campaign", campaignId);
  if (orgId) params.append("org", orgId);

  return `${baseUrl}?${params.toString()}`;
}

/**
 * Replace URLs in text with tracked versions
 * @param text - The original text
 * @param messageId - The message ID for tracking
 * @param userId - The user ID for tracking
 * @param campaignId - Optional campaign ID
 * @param orgId - Optional organization ID
 * @returns Text with URLs replaced with tracked versions
 */
export function replaceUrlsWithTracking(
  text: string,
  messageId: string,
  userId: string,
  campaignId?: string,
  orgId?: string
): string {
  return text.replace(URL_REGEX, (url) => {
    return wrapUrlWithTracking(url, messageId, userId, campaignId, orgId);
  });
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
