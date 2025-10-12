/**
 * Link shortening service using real TinyURL API
 */

/**
 * Create a short link using TinyURL API
 * Flow: Original URL -> Tracking URL -> TinyURL
 * @param originalUrl - The original URL to shorten
 * @param messageId - Message ID for tracking
 * @param userId - User ID for tracking
 * @param campaignId - Optional campaign ID
 * @param orgId - Optional organization ID
 * @returns Promise that resolves to TinyURL
 */
export async function shortenUrl(
  originalUrl: string,
  messageId: string,
  userId: string,
  campaignId?: string,
  orgId?: string,
  recipientEmail?: string
): Promise<string> {
  try {
    // First create the tracking URL
    const trackingUrl = createTrackingUrl(
      originalUrl,
      messageId,
      userId,
      campaignId,
      orgId,
      recipientEmail
    );

    // Then shorten the tracking URL using TinyURL API
    const response = await fetch(
      `https://tinyurl.com/api-create.php?url=${encodeURIComponent(trackingUrl)}`
    );

    if (!response.ok) {
      throw new Error("Failed to create TinyURL");
    }

    const shortUrl = await response.text();

    // TinyURL API returns the short URL as plain text
    if (shortUrl.startsWith("http")) {
      return shortUrl;
    } else {
      throw new Error("Invalid response from TinyURL API");
    }
  } catch (error) {
    console.error("Error creating TinyURL:", error);
    // Fallback to direct tracking without shortening
    return createTrackingUrl(
      originalUrl,
      messageId,
      userId,
      campaignId,
      orgId,
      recipientEmail
    );
  }
}

/**
 * Create tracking URL for the original URL
 */
function createTrackingUrl(
  originalUrl: string,
  messageId: string,
  userId: string,
  campaignId?: string,
  orgId?: string,
  recipientEmail?: string
): string {
  // Use backend URL (port 8080) instead of frontend URL (port 3000)
  const isLocalhost = window.location.hostname === "localhost";
  const backendUrl = isLocalhost
    ? "http://localhost:8080"
    : "https://outreach-ly.com";

  const baseUrl = `${backendUrl}/track/click`;
  const params = new URLSearchParams({
    url: originalUrl,
    msg: messageId,
    user: userId,
  });

  if (campaignId) params.append("campaign", campaignId);
  if (orgId) params.append("org", orgId);
  if (recipientEmail) params.append("email", recipientEmail);

  return `${baseUrl}?${params.toString()}`;
}
