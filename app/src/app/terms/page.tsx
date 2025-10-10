import type { Metadata } from "next";
import DashboardLayout from "@/components/DashboardLayout";

export const metadata: Metadata = {
  title: "Terms of Service | Outreachly",
  description: "Terms of Service for using Outreachly",
};

export default function TermsOfServicePage() {
  return (
    <DashboardLayout>
      <div className="min-h-screen bg-white">
        <div className="mx-auto max-w-3xl px-6 py-12 sm:py-16">
          <header className="mb-10">
            <h1 className="text-3xl font-bold tracking-tight text-gray-900">
              Outreachly Terms of Service
            </h1>
            <p className="mt-2 text-sm text-gray-500">
              Effective date: October 9, 2025
            </p>
          </header>

          <div className="prose prose-slate max-w-none">
            <p>
              These Terms of Service ("Terms") govern your access to and use of
              Outreachly (the "Service"). By accessing or using the Service, you
              agree to be bound by these Terms. If you do not agree, do not use
              the Service.
            </p>

            <h2>1. Eligibility</h2>
            <p>
              You must be at least 18 years old and able to form a binding
              contract to use the Service. If you use the Service on behalf of
              an organization, you represent and warrant that you have authority
              to bind that organization to these Terms.
            </p>

            <h2>2. Accounts and Security</h2>
            <p>
              You are responsible for maintaining the confidentiality of your
              account credentials and for all activities that occur under your
              account. Notify us immediately of any unauthorized use or security
              incident.
            </p>

            <h2>3. Gmail Integration and Third-Party Services</h2>
            <p>
              Outreachly may offer optional integrations, including the Gmail
              API. When you choose to connect your Google account, Google will
              present a consent screen requesting permissions (e.g.,{" "}
              <code>gmail.send</code>) so that Outreachly can send email you
              author. You can revoke access at any time in your Google Account
              settings. Your use of third-party services is subject to their own
              terms and privacy policies.
            </p>

            <h2>4. Acceptable Use</h2>
            <p>
              You agree not to use the Service for spam, harassment, illegal, or
              harmful activities.
            </p>
            <p>You must:</p>
            <ul>
              <li>
                Comply with all applicable laws, including CAN-SPAM, CASL, and
                GDPR as relevant.
              </li>
              <li>
                Only email recipients who have a legitimate interest in or
                reasonable expectation of your contact.
              </li>
            </ul>

            <h2>5. Content and License</h2>
            <p>
              You retain ownership of any content you submit to the Service. You
              grant Outreachly a non-exclusive, worldwide, royalty-free license
              to host, process, and display your content solely to provide and
              improve the Service.
            </p>

            <h2>6. Intellectual Property</h2>
            <p>
              Outreachly and its licensors own all right, title, and interest in
              and to the Service, including its software, features, and
              branding. Except for the limited rights expressly granted in these
              Terms, no rights are transferred to you.
            </p>

            <h2>7. Beta Features</h2>
            <p>
              We may provide beta or experimental features. These are provided
              “as is” and may change, break, or be discontinued at any time
              without notice.
            </p>

            <h2>8. Disclaimers</h2>
            <p>
              The Service is provided on an “as is” and “as available” basis. To
              the fullest extent permitted by law, Outreachly disclaims all
              warranties, express or implied, including merchantability, fitness
              for a particular purpose, and non-infringement.
            </p>

            <h2>9. Limitation of Liability</h2>
            <p>
              To the maximum extent permitted by law, Outreachly will not be
              liable for any indirect, incidental, special, consequential, or
              punitive damages, or for any loss of profits, revenues, data, or
              goodwill, arising out of or related to your use of the Service.
            </p>

            <h2>10. Indemnification</h2>
            <p>
              You agree to indemnify and hold harmless Outreachly and its
              affiliates, officers, agents, and employees from any claim,
              demand, losses, or damages arising out of or related to your use
              of the Service or your violation of these Terms.
            </p>

            <h2>11. Suspension and Termination</h2>
            <p>
              We may suspend or terminate your access to the Service at any time
              if we believe you have violated these Terms or pose a risk to the
              Service or other users. You may stop using the Service at any
              time.
            </p>

            <h2>12. Changes to the Service and Terms</h2>
            <p>
              We may modify the Service and these Terms from time to time.
              Material changes will be posted within the Service or on our
              website. Your continued use of the Service after changes become
              effective constitutes acceptance of the updated Terms.
            </p>

            <h2>13. Privacy and Data Protection</h2>
            <p>
              Your use of the Service is subject to our{" "}
              <a href="/privacy">Privacy Policy</a>, which explains how we
              collect, use, and protect your personal information. Outreachly
              processes personal data in accordance with applicable privacy
              laws, including the GDPR. We implement appropriate technical and
              organizational measures to protect your data.
            </p>

            <h2>14. Governing Law</h2>
            <p>
              These Terms will be governed by and construed in accordance with
              the laws applicable in your primary place of business if you are
              an organization, or your country/state of residence if you are an
              individual, without regard to conflict of law principles.
            </p>

            <h2>15. Contact</h2>
            <p>
              Questions about these Terms? Contact us at{" "}
              <a href="mailto:vietmynguyen@umass.edu">vietmynguyen@umass.edu</a>
              .
            </p>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
