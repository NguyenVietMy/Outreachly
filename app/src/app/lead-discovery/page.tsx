"use client";

import { useState, useEffect } from "react";
import AuthGuard from "@/components/AuthGuard";
import DashboardLayout from "@/components/DashboardLayout";
import { CompanyList } from "@/components/companies/CompanyList";
import { CompanyFilters } from "@/components/companies/CompanyFilters";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";
import { API_BASE_URL } from "@/lib/config";

interface Company {
  id: string;
  name: string;
  domain: string;
  createdAt: string;
  updatedAt: string;
}

interface CompanyFilters {
  search: string;
}

export default function LeadDiscoveryPage() {
  const [companies, setCompanies] = useState<Company[]>([]);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState<CompanyFilters>({
    search: "",
  });
  const [pagination, setPagination] = useState({
    currentPage: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const fetchCompanies = async (page = 0, search = "") => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: page.toString(),
        size: pagination.size.toString(),
      });

      if (search) {
        params.append("search", search);
      }

      const response = await fetch(`${API_BASE_URL}/api/companies?${params}`);
      const data = await response.json();

      if (response.ok) {
        setCompanies(data.companies);
        setPagination({
          currentPage: data.currentPage,
          totalPages: data.totalPages,
          totalElements: data.totalElements,
          size: data.size,
        });
      } else {
        console.error("Failed to fetch companies:", data.error);
      }
    } catch (error) {
      console.error("Error fetching companies:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCompanies(0, filters.search);
  }, []);

  const handleSearch = () => {
    fetchCompanies(0, filters.search);
  };

  const handlePageChange = (page: number) => {
    fetchCompanies(page, filters.search);
  };

  const handleFilterChange = (newFilters: Partial<CompanyFilters>) => {
    setFilters((prev) => ({ ...prev, ...newFilters }));
  };

  return (
    <AuthGuard>
      <DashboardLayout>
        <div className="p-6 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold tracking-tight">
                Lead Discovery
              </h1>
              <p className="text-muted-foreground">
                Discover and find new companies for your campaigns
              </p>
            </div>
            <Button>
              <Plus className="w-4 h-4 mr-2" />
              Add Company
            </Button>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
            {/* Filters Sidebar */}
            <div className="lg:col-span-1">
              <CompanyFilters
                filters={filters}
                onFilterChange={handleFilterChange}
                onSearch={handleSearch}
              />
            </div>

            {/* Companies List */}
            <div className="lg:col-span-3">
              <CompanyList
                companies={companies}
                loading={loading}
                pagination={pagination}
                onPageChange={handlePageChange}
              />
            </div>
          </div>
        </div>
      </DashboardLayout>
    </AuthGuard>
  );
}
