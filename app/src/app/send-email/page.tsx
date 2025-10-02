"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

export default function SendEmailRedirect() {
  const router = useRouter();

  useEffect(() => {
    // Redirect to the new Gmail-based send email page
    router.replace("/send-gmail");
  }, [router]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="text-center">
        <Loader2 className="h-8 w-8 animate-spin mx-auto mb-4 text-blue-600" />
        <p className="text-gray-600">Redirecting to Gmail Email...</p>
      </div>
    </div>
  );
}
