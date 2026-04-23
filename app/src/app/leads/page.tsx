"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function LeadsRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/workspace?tab=leads");
  }, [router]);
  return null;
}
