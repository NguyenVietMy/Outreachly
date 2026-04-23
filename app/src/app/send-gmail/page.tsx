"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function SendGmailRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/workspace?tab=send-gmail");
  }, [router]);
  return null;
}
