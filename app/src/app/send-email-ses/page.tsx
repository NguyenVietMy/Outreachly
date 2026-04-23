"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function DomainSendingRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/workspace?tab=domain-sending");
  }, [router]);
  return null;
}
