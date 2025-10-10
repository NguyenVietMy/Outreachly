/**
 * Converts plain text to HTML with proper formatting and tracking
 */

export interface EmailTrackingData {
  messageId: string;
  recipientEmail: string;
  campaignId?: string;
  userId?: string;
}

export interface ConvertedEmail {
  htmlContent: string;
  trackingPixel: string;
  plainText: string;
}

/**
 * Converts plain text to HTML with smart formatting
 */
export function convertTextToHtml(text: string): string {
  if (!text.trim()) return "";

  let html = text
    // Convert markdown-style formatting first
    .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>") // Bold
    .replace(/\*(.*?)\*/g, "<em>$1</em>") // Italic
    .replace(/__(.*?)__/g, "<u>$1</u>") // Underline

    // Convert links
    .replace(
      /\[([^\]]+)\]\(([^)]+)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
    )

    // Convert blockquotes
    .replace(/^>\s+(.+)$/gm, "<blockquote>$1</blockquote>")

    // Convert lists
    .replace(/^•\s+(.+)$/gm, "<li>$1</li>") // Bullet points
    .replace(/^-\s+(.+)$/gm, "<li>$1</li>") // Dash points
    .replace(/^\d+\.\s+(.+)$/gm, "<li>$1</li>") // Numbered lists

    // Convert line breaks to <br> tags
    .replace(/\n/g, "<br>")

    // Convert double line breaks to paragraphs
    .replace(/(<br>\s*){2,}/g, "</p><p>")

    // Wrap in paragraph tags
    .replace(/^(.+)$/gm, "<p>$1</p>")

    // Clean up empty paragraphs
    .replace(/<p><\/p>/g, "")
    .replace(/<p>\s*<\/p>/g, "")

    // Wrap consecutive <li> elements in <ul>
    .replace(/(<li>.*<\/li>)(\s*<li>.*<\/li>)+/g, (match) => {
      return `<ul>${match}</ul>`;
    });

  // Clean up any malformed HTML
  html = html
    .replace(/<p><ul>/g, "<ul>")
    .replace(/<\/ul><\/p>/g, "</ul>")
    .replace(/<p><blockquote>/g, "<blockquote>")
    .replace(/<\/blockquote><\/p>/g, "</blockquote>");

  return html;
}

/**
 * Generates tracking pixel HTML
 */
export function generateTrackingPixel(trackingData: EmailTrackingData): string {
  const trackingUrl = `${process.env.NEXT_PUBLIC_API_URL || "https://api.outreach-ly.com"}/api/tracking/open`;
  const params = new URLSearchParams({
    msg: trackingData.messageId,
    to: trackingData.recipientEmail,
    ...(trackingData.campaignId && { campaign: trackingData.campaignId }),
    ...(trackingData.userId && { user: trackingData.userId }),
  });

  return `<img src="${trackingUrl}?${params.toString()}" width="1" height="1" style="display:none;" alt="" />`;
}

/**
 * Converts plain text to full HTML email with tracking
 */
export function convertToHtmlEmail(
  text: string,
  trackingData: EmailTrackingData
): ConvertedEmail {
  const htmlContent = convertTextToHtml(text);
  const trackingPixel = generateTrackingPixel(trackingData);

  // Wrap in proper email HTML structure
  const fullHtml = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Email</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
      line-height: 1.6;
      color: #333;
      max-width: 600px;
      margin: 0 auto;
      padding: 20px;
    }
    p { margin: 0 0 16px 0; }
    ul, ol { margin: 0 0 16px 0; padding-left: 20px; }
    li { margin: 0 0 8px 0; }
    blockquote {
      border-left: 4px solid #e5e7eb;
      padding-left: 16px;
      margin: 16px 0;
      font-style: italic;
      color: #6b7280;
    }
    a {
      color: #2563eb;
      text-decoration: none;
    }
    a:hover {
      text-decoration: underline;
    }
    strong { font-weight: 600; }
    em { font-style: italic; }
    u { text-decoration: underline; }
  </style>
</head>
<body>
  ${htmlContent}
  ${trackingPixel}
</body>
</html>`;

  return {
    htmlContent: fullHtml,
    trackingPixel,
    plainText: text,
  };
}

/**
 * Processes variables in email content
 */
export function processVariables(
  content: string,
  variables: Record<string, string>
): string {
  let processed = content;

  Object.entries(variables).forEach(([key, value]) => {
    const regex = new RegExp(`\\{\\{${key}\\}\\}`, "gi");
    processed = processed.replace(regex, value || "");
  });

  return processed;
}
