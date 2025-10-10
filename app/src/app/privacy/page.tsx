import type { Metadata } from "next";
import DashboardLayout from "@/components/DashboardLayout";

export const metadata: Metadata = {
  title: "Privacy Policy | Outreachly",
  description: "Privacy Policy for Outreachly",
};

export default function PrivacyPolicyPage() {
  return (
    <DashboardLayout>
      <div className="min-h-screen bg-white">
        <div className="mx-auto max-w-3xl px-6 py-12 sm:py-16">
          <header className="mb-10">
            <h1 className="text-3xl font-bold tracking-tight text-gray-900">
              Outreachly Privacy Policy
            </h1>
            <p className="mt-2 text-sm text-gray-500">
              Effective date: October 9, 2025
            </p>
          </header>

          <div className="prose prose-slate max-w-none">
            <p>
              This Privacy Policy explains how Outreachly ("we", "us", or "our")
              collects, uses, and protects information when you use our services
              (the "Service").
            </p>

            <h2>1. Information We Collect</h2>
            <ul>
              <li>
                <strong>Account Information</strong>: Name, email address,
                organization details you provide when creating or managing your
                account.
              </li>
              <li>
                <strong>Usage Data</strong>: Log data, device and browser
                information, pages visited, and interactions with the Service.
              </li>
              <li>
                <strong>Content You Provide</strong>: Email content, templates,
                recipients, and other materials you upload or compose in the
                Service.
              </li>
              <li>
                <strong>Integrations</strong>: If you connect third‑party
                services (e.g., Gmail), we receive tokens and limited profile
                details necessary to provide the integration. For Gmail, this
                may include the <code>gmail.send</code> scope for sending email
                you author.
              </li>
            </ul>

            <h2>2. How We Use Information</h2>
            <ul>
              <li>
                Provide, operate, and improve the Service and its features.
              </li>
              <li>Authenticate users, secure accounts, and prevent abuse.</li>
              <li>
                Offer integrations you opt into (e.g., sending emails via
                Gmail).
              </li>
              <li>
                Provide support, communicate updates, and improve user
                experience.
              </li>
              <li>Comply with legal obligations and enforce our Terms.</li>
            </ul>

            <h2>3. Legal Bases (EEA/UK)</h2>
            <p>
              Where applicable, we process personal data based on: (a)
              performance of a contract, (b) legitimate interests (e.g., service
              improvement, security), (c) consent (e.g., marketing
              communications or optional integrations), or (d) legal
              obligations.
            </p>

            <h2>4. Sharing and Disclosure</h2>
            <ul>
              <li>
                <strong>Service Providers</strong>: We share data with vendors
                who help operate the Service (e.g., cloud hosting, analytics)
                under confidentiality agreements.
              </li>
              <li>
                <strong>Integrations You Enable</strong>: If you connect Gmail
                or other services, data may be shared as needed to provide those
                features per the provider’s terms.
              </li>
              <li>
                <strong>Legal</strong>: We may disclose information to comply
                with laws, regulations, lawful requests, or to protect rights,
                property, and safety.
              </li>
            </ul>

            <h2>5. Data Retention</h2>
            <p>
              We retain personal data only as long as necessary to provide the
              Service, fulfill the purposes described here, comply with legal
              obligations, resolve disputes, and enforce agreements. Retention
              periods may vary by data type and context.
            </p>

            <h2>6. Security</h2>
            <p>
              We implement appropriate technical and organizational measures
              designed to protect your information. However, no method of
              transmission or storage is 100% secure, and we cannot guarantee
              absolute security.
            </p>

            <h2>7. International Transfers</h2>
            <p>
              Your information may be transferred to and processed in countries
              outside your own. Where required, we use appropriate safeguards
              (such as standard contractual clauses).
            </p>

            <h2>8. Your Rights</h2>
            <p>
              Depending on your location, you may have rights to access,
              correct, delete, or port your data, object to or restrict certain
              processing, and withdraw consent where applicable. You can
              exercise rights by contacting us using the information below.
            </p>

            <h2>9. Children’s Privacy</h2>
            <p>
              The Service is not directed to children under 13 (or under the age
              required by your jurisdiction). We do not knowingly collect
              personal information from children.
            </p>

            <h2>10. Changes to This Policy</h2>
            <p>
              We may update this Privacy Policy from time to time. Material
              changes will be posted in the Service or on our website. Your
              continued use after changes become effective constitutes
              acceptance of the updated Policy.
            </p>

            <h2>11. Contact Us</h2>
            <p>
              Questions about this Privacy Policy? Contact us at{" "}
              <a href="mailto:vietmynguyen@umass.edu">vietmynguyen@umass.edu</a>
              .
            </p>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
