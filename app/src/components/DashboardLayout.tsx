"use client";

import { useAuth } from "@/contexts/AuthContext";
import { Button } from "@/components/ui/button";
import {
  BarChart3,
  Settings,
  LogOut,
  Menu,
  X,
  Layers,
  User,
  Zap,
  ChevronDown,
} from "lucide-react";
import { useState, useRef, useEffect } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";

interface DashboardLayoutProps {
  children: React.ReactNode;
}

const TOPBAR_H = "h-12";
const TOPBAR_OFFSET = "top-12";
const TOPBAR_PL = "pt-12";

export default function DashboardLayout({ children }: DashboardLayoutProps) {
  const { user, logout, loading } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const pathname = usePathname();

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const navigation = [
    { name: "Dashboard", href: "/dashboard", icon: BarChart3 },
    { name: "Personal", href: "/personal", icon: User },
    { name: "Workspace", href: "/workspace", icon: Layers },
    { name: "Settings", href: "/settings", icon: Settings },
  ];
  const integrationNavigation = [
    { name: "Integrations", href: "/integrations", icon: Zap },
  ];

  const currentPage =
    navigation.find(
      (n) => pathname === n.href || pathname?.startsWith(`${n.href}/`)
    ) ||
    integrationNavigation.find((n) => pathname === n.href) || {
      name: "Dashboard",
    };

  return (
    <div className="h-screen overflow-hidden bg-[#f5f5f5] dark:bg-[#1a1a1a]">
      {/* ── Top navigation bar ── */}
      <header
        className={`fixed inset-x-0 top-0 z-50 ${TOPBAR_H} flex items-center justify-between bg-[#f5f5f5] px-4 lg:px-6`}
      >
        {/* Left: hamburger (mobile) + logo + page context */}
        <div className="flex items-center gap-3">
          <button
            onClick={() => setSidebarOpen(true)}
            className="p-1.5 rounded-md text-[#666666] hover:text-[#0d0d0d] lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </button>
          <Link
            href="/dashboard"
            className="text-[15px] font-semibold text-[#0d0d0d] tracking-tight"
          >
            Outreachly
          </Link>
          <span className="hidden text-[#e5e5e5] sm:inline">/</span>
          <span className="hidden text-[13px] text-[#666666] sm:inline">
            {currentPage.name}
          </span>
        </div>

        {/* Right: user menu */}
        <div className="relative" ref={userMenuRef}>
          <button
            onClick={() => setUserMenuOpen((prev) => !prev)}
            className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-[#666666] hover:bg-[#f0f0f0] hover:text-[#0d0d0d] transition-colors"
          >
            <div className="h-6 w-6 rounded-full bg-[#d4fae8] flex items-center justify-center">
              <span className="text-[11px] font-medium text-[#0fa76e]">
                {user?.firstName?.charAt(0)}
                {user?.lastName?.charAt(0)}
              </span>
            </div>
            <span className="hidden sm:inline text-[13px] font-medium text-[#0d0d0d]">
              {user?.firstName} {user?.lastName}
            </span>
            <ChevronDown className="h-3.5 w-3.5" />
          </button>

          {userMenuOpen && (
            <div className="absolute right-0 top-full mt-1 w-56 rounded-lg border border-[rgba(0,0,0,0.05)] bg-white p-1 shadow-lg">
              <div className="px-3 py-2 border-b border-[rgba(0,0,0,0.05)] mb-1">
                <p className="text-sm font-medium text-[#0d0d0d] truncate">
                  {user?.firstName} {user?.lastName}
                </p>
                <p className="text-xs text-[#666666] truncate">
                  {user?.email}
                </p>
              </div>
              <button
                onClick={() => {
                  setUserMenuOpen(false);
                  logout();
                }}
                disabled={loading}
                className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-[#666666] hover:bg-[#f5f5f5] hover:text-[#0d0d0d] transition-colors"
              >
                <LogOut className="h-4 w-4" />
                {loading ? "Signing out..." : "Sign out"}
              </button>
            </div>
          )}
        </div>
      </header>

      {/* ── Mobile sidebar backdrop ── */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* ── Sidebar ── */}
      <aside
        className={`fixed ${TOPBAR_OFFSET} bottom-0 left-0 z-50 w-56 bg-[#f5f5f5] dark:bg-[#141414] transform transition-transform duration-300 ease-in-out lg:translate-x-0 ${
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        {/* Mobile close button */}
        <div className="flex items-center justify-end px-3 py-2 lg:hidden">
          <button
            onClick={() => setSidebarOpen(false)}
            className="p-1.5 rounded-md text-[#666666] hover:text-[#0d0d0d]"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {navigation.map((item) => {
            const Icon = item.icon;
            const isActive =
              pathname === item.href ||
              pathname?.startsWith(`${item.href}/`) ||
              (item.href === "/workspace" && pathname === "/leads/modify");
            return (
              <Link
                key={item.name}
                href={item.href}
                onClick={() => setSidebarOpen(false)}
                className={
                  `flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] transition-colors ` +
                  (isActive
                    ? "bg-[#d4fae8] dark:bg-[#18E299]/15 text-[#0d0d0d] dark:text-[#ededed] font-medium"
                    : "text-[#666666] dark:text-[#a0a0a0] hover:bg-[#f0f0f0] dark:hover:bg-[rgba(255,255,255,0.06)] hover:text-[#0d0d0d] dark:hover:text-[#ededed]")
                }
              >
                <Icon className={`h-4 w-4 ${isActive ? "text-[#0fa76e]" : ""}`} />
                <span>{item.name}</span>
              </Link>
            );
          })}

          <div className="pt-4">
            <p className="px-3 text-[11px] font-medium uppercase tracking-wider text-[#888888] dark:text-[#666666]">
              Integrations
            </p>
            <div className="mt-2 space-y-1">
              {integrationNavigation.map((item) => {
                const Icon = item.icon;
                const isActive = pathname === "/integrations";
                return (
                  <Link
                    key={item.name}
                    href={item.href}
                    onClick={() => setSidebarOpen(false)}
                    className={
                      `flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] transition-colors ` +
                      (isActive
                        ? "bg-[#d4fae8] dark:bg-[#18E299]/15 text-[#0d0d0d] dark:text-[#ededed] font-medium"
                        : "text-[#666666] dark:text-[#a0a0a0] hover:bg-[#f0f0f0] dark:hover:bg-[rgba(255,255,255,0.06)] hover:text-[#0d0d0d] dark:hover:text-[#ededed]")
                    }
                  >
                    <Icon className={`h-4 w-4 ${isActive ? "text-[#0fa76e]" : ""}`} />
                    <span>{item.name}</span>
                  </Link>
                );
              })}
            </div>
          </div>
        </nav>
      </aside>

      {/* ── Main content area ── */}
      <div className={`${TOPBAR_PL} lg:pl-56 h-screen`}>
        <main className="h-full pl-0 pr-2 pt-2 pb-2">
          <div className="h-full overflow-y-auto rounded-2xl bg-background shadow-sm">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
