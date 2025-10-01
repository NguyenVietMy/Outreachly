"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Info } from "lucide-react";

export default function OrganizationOnboarding() {
  const router = useRouter();
  const { user, loading, checkAuth } = useAuth();
  const [name, setName] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  useEffect(() => {
    if (!loading) {
      if (!user) {
        router.push("/auth");
        return;
      }
      if (user.orgId) {
        router.push("/dashboard");
        return;
      }
    }
  }, [user, loading, router]);

  const handleCreate = async () => {
    setError(null);
    if (name.trim().length < 2 || name.trim().length > 30) {
      setError("Organization name must be between 2 and 30 characters");
      return;
    }
    try {
      setIsSubmitting(true);
      const res = await fetch(`${API_URL}/api/organizations`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ name: name.trim() }),
      });
      if (res.status === 201) {
        await checkAuth();
        router.push("/dashboard");
        return;
      }
      const text = await res.text();
      setError(text || "Failed to create organization");
    } catch (e) {
      setError("Failed to create organization");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
      <div className="w-full max-w-2xl">
        <Card>
          <CardHeader>
            <CardTitle>Create your organization</CardTitle>
            <CardDescription>
              You must belong to an organization to use Outreachly.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs defaultValue="create">
              <TabsList className="grid grid-cols-2">
                <TabsTrigger value="create">Create organization</TabsTrigger>
                <TabsTrigger
                  value="join"
                  disabled
                  title="Temporarily unavailable"
                >
                  Join organization
                </TabsTrigger>
              </TabsList>
              <TabsContent value="create" className="space-y-4 mt-4">
                {error && (
                  <Alert>
                    <AlertDescription>{error}</AlertDescription>
                  </Alert>
                )}
                <div className="space-y-2">
                  <Label htmlFor="orgName">Organization Name</Label>
                  <Input
                    id="orgName"
                    placeholder="e.g. Acme Inc"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                  />
                  <div className="flex items-center text-sm text-gray-500 gap-2">
                    <Info className="w-4 h-4" />
                    <span>2–30 characters. You can change details later.</span>
                  </div>
                </div>
                <Button
                  onClick={handleCreate}
                  disabled={
                    isSubmitting ||
                    name.trim().length < 2 ||
                    name.trim().length > 30
                  }
                >
                  {isSubmitting ? "Creating..." : "Create and continue"}
                </Button>
              </TabsContent>
              <TabsContent value="join" className="mt-4">
                <div className="text-gray-500">
                  Joining via invite will be available soon.
                </div>
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
