-- Add clicked_url field to email_events table for link click tracking
ALTER TABLE email_events ADD COLUMN clicked_url TEXT;

-- Create index for better query performance on clicked URLs
CREATE INDEX idx_email_events_clicked_url ON email_events(clicked_url);

