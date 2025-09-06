"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";

interface Provider {
  name: string;
  url: string;
  enabled: boolean;
}

export default function AuthPage() {
  const router = useRouter();
  const [providers, setProviders] = useState<Record<string, Provider>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Fetch available OAuth providers from your Spring Boot API
    const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
    fetch(`${apiUrl}/auth/providers`)
      .then((response) => response.json())
      .then((data) => {
        setProviders(data);
        setLoading(false);
      })
      .catch((error) => {
        console.error("Error fetching providers:", error);
        setLoading(false);
      });
  }, []);

  const handleOAuthLogin = (providerKey: string) => {
    const provider = providers[providerKey];
    if (provider && provider.enabled) {
      // Redirect to Spring Boot OAuth2 endpoint
      const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
      window.location.href = `${apiUrl}${provider.url}`;
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div>
          <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
            Sign in to Outreachly
          </h2>
          <p className="mt-2 text-center text-sm text-gray-600">
            Choose your preferred sign-in method
          </p>
        </div>

        <div className="mt-8 space-y-4">
          {Object.entries(providers).map(
            ([key, provider]) =>
              provider.enabled && (
                <button
                  key={key}
                  onClick={() => handleOAuthLogin(key)}
                  className="group relative w-full flex justify-center py-3 px-4 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
                >
                  <span className="absolute left-0 inset-y-0 flex items-center pl-3">
                    {key === "google" && (
                      <svg className="h-5 w-5 text-red-500" viewBox="0 0 24 24">
                        <path
                          fill="currentColor"
                          d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                        />
                        <path
                          fill="currentColor"
                          d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                        />
                        <path
                          fill="currentColor"
                          d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                        />
                        <path
                          fill="currentColor"
                          d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                        />
                      </svg>
                    )}
                  </span>
                  Continue with {provider.name}
                </button>
              )
          )}
        </div>

        <div className="mt-6">
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-gray-300" />
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="px-2 bg-gray-50 text-gray-500">
                Secure OAuth2 Authentication
              </span>
            </div>
          </div>
        </div>

        <div className="text-center">
          <p className="text-xs text-gray-500">
            By signing in, you agree to our Terms of Service and Privacy Policy
          </p>
        </div>
      </div>
    </div>
  );
}
