resource "aws_ses_domain_identity" "main" {
  domain = var.domain_name
}

resource "aws_route53_record" "ses_verification" {
  zone_id = var.route53_zone_id
  name    = "_amazonses.${var.domain_name}"
  type    = "TXT"
  ttl     = "600"
  records = [aws_ses_domain_identity.main.verification_token]
}

resource "aws_ses_domain_identity_verification" "main" {
  domain = aws_ses_domain_identity.main.id

  depends_on = [aws_route53_record.ses_verification]
}

resource "aws_ses_domain_dkim" "main" {
  domain = aws_ses_domain_identity.main.domain
}

resource "aws_route53_record" "dkim" {
  count   = 3
  zone_id = var.route53_zone_id
  name    = "${aws_ses_domain_dkim.main.dkim_tokens[count.index]}._domainkey.${var.domain_name}"
  type    = "CNAME"
  ttl     = "600"
  records = ["${aws_ses_domain_dkim.main.dkim_tokens[count.index]}.dkim.amazonses.com"]
}

resource "aws_ses_domain_mail_from" "main" {
  domain           = aws_ses_domain_identity.main.domain
  mail_from_domain = "mail.${var.domain_name}"
}

resource "aws_route53_record" "mail_from_mx" {
  zone_id = var.route53_zone_id
  name    = aws_ses_domain_mail_from.main.mail_from_domain
  type    = "MX"
  ttl     = "600"
  records = ["10 feedback-smtp.${var.region}.amazonses.com"]
}

resource "aws_route53_record" "mail_from_spf" {
  zone_id = var.route53_zone_id
  name    = aws_ses_domain_mail_from.main.mail_from_domain
  type    = "TXT"
  ttl     = "600"
  records = ["v=spf1 include:amazonses.com -all"]
}

# Resend DNS Records for Domain Verification
# MX Record for Resend
resource "aws_route53_record" "resend_mx" {
  zone_id = var.route53_zone_id
  name    = "send.${var.domain_name}"
  type    = "MX"
  ttl     = 300

  records = [
    "10 feedback-smtp.us-east-1.amazonses.com"
  ]
}

# SPF Record for Resend
resource "aws_route53_record" "resend_spf" {
  zone_id = var.route53_zone_id
  name    = "send.${var.domain_name}"
  type    = "TXT"
  ttl     = 300

  records = [
    "v=spf1 include:amazonses.com ~all"
  ]
}

# DKIM Record for Resend
resource "aws_route53_record" "resend_dkim" {
  zone_id = var.route53_zone_id
  name    = "resend._domainkey.${var.domain_name}"
  type    = "TXT"
  ttl     = 300

  records = [
    "p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCYsZ5qUNGR9ilVJEV2tbgmpbrhssQnFMjYappSY5Cp+K3pk/KCPog+nAgWDfvW5/OePn36y9JIziHn6QzK8m5Xj+jOvLYmNvu+TTZZbgGVgZm6HaCY+CFc9NNNX6iJmFHErwWyB0svnr4okikGy3Ti+zaK9+6t+IVdRARrz0WpOQIDAQAB"
  ]
}