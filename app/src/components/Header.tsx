"use client";

import { useAuth } from "@/contexts/AuthContext";
import Link from "next/link";

export default function Header() {
  const { user, logout, loading } = useAuth();

  if (loading) {
    return (
      <header className="bg-background/80 backdrop-blur-md border-b border-border sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <div className="flex items-center">
              <Link href="/" className="text-lg font-semibold text-foreground tracking-tight">
                Pulse
              </Link>
            </div>
            <div className="animate-pulse bg-muted h-8 w-24 rounded-full"></div>
          </div>
        </div>
      </header>
    );
  }

  return (
    <header className="bg-background/80 backdrop-blur-md border-b border-border sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          <div className="flex items-center">
            <Link href="/" className="text-lg font-semibold text-foreground tracking-tight">
              Pulse
            </Link>
          </div>

          <div className="flex items-center space-x-4">
            {user ? (
              <>
                <div className="flex items-center space-x-3">
                  {user.profilePictureUrl && (
                    <img
                      src={user.profilePictureUrl}
                      alt={user.firstName}
                      className="h-8 w-8 rounded-full"
                    />
                  )}
                  <span className="text-sm font-medium text-foreground">
                    {user.firstName} {user.lastName}
                  </span>
                </div>
                <button
                  onClick={logout}
                  disabled={loading}
                  className="text-sm text-muted-foreground hover:text-foreground px-4 py-2 rounded-full hover:bg-secondary disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {loading ? "Signing out..." : "Logout"}
                </button>
              </>
            ) : (
              <div className="flex items-center space-x-3">
                <Link
                  href="/auth"
                  className="text-sm font-medium text-muted-foreground hover:text-foreground px-4 py-2 rounded-full hover:bg-secondary"
                >
                  Sign In
                </Link>
                <Link
                  href="/auth"
                  className="text-sm font-medium bg-primary text-primary-foreground rounded-full px-5 py-2 shadow-button hover:bg-primary/90"
                >
                  Get Started
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
