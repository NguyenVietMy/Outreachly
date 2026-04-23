import { TempoInit } from "@/components/tempo-init";
import { AuthProvider } from "@/contexts/AuthContext";
import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { GeistMono } from "geist/font/mono";
import Script from "next/script";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });

export const metadata: Metadata = {
  title: "Outreachly - Lead Enrichment & Cold Outreach",
  description:
    "Lead enrichment + cold outreach SaaS built with Next.js and Spring Boot",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      {/* <Script src="https://api.tempo.build/proxy-asset?url=https://storage.googleapis.com/tempo-public-assets/error-handling.js" /> [deprecated] */}
      <body className={`${inter.variable} ${GeistMono.variable} font-sans`}>
        <AuthProvider>
          {children}
          <TempoInit />
        </AuthProvider>
      </body>
    </html>
  );
}
