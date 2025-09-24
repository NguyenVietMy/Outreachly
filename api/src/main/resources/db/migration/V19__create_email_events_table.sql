-- Create email_events table for tracking SES events
CREATE TABLE email_events (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    email_address VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('BOUNCE', 'COMPLAINT', 'DELIVERY', 'OPEN', 'CLICK', 'REJECT')),
    bounce_type VARCHAR(100),
    bounce_subtype VARCHAR(100),
    complaint_feedback_type VARCHAR(100),
    timestamp TIMESTAMP NOT NULL,
    raw_message TEXT,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_email_events_email_address ON email_events(email_address);
CREATE INDEX idx_email_events_event_type ON email_events(event_type);
CREATE INDEX idx_email_events_timestamp ON email_events(timestamp);
CREATE INDEX idx_email_events_processed ON email_events(processed);
CREATE INDEX idx_email_events_message_id ON email_events(message_id);

-- Create composite index for common queries
CREATE INDEX idx_email_events_email_type_timestamp ON email_events(email_address, event_type, timestamp);
