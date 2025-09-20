"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { API_BASE_URL } from "@/lib/config";
import { EmailResultsModal } from "./EmailResultsModal";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { Building2, Globe, Mail } from "lucide-react";

interface Company {
  id: string;
  name: string;
  domain: string;
  industry: string;
  size: string;
  updatedAt: string;
}

interface PaginationInfo {
  currentPage: number;
  totalPages: number;
  totalElements: number;
  size: number;
}

interface CompanyListProps {
  companies: Company[];
  loading: boolean;
  pagination: PaginationInfo;
  onPageChange: (page: number) => void;
}

export function CompanyList({
  companies,
  loading,
  pagination,
  onPageChange,
}: CompanyListProps) {
  const [findingEmails, setFindingEmails] = useState<string | null>(null);
  const [emailResults, setEmailResults] = useState<{
    emails: any[];
    companyName: string;
    domain: string;
  } | null>(null);

  const getDomainUrl = (domain: string) => {
    return domain.startsWith("http") ? domain : `https://${domain}`;
  };

  const handleFindEmails = async (companyId: string) => {
    // Double-check that we have a valid company ID and domain before making the API call
    const company = companies.find((c) => c.id === companyId);
    if (!company || !company.domain) {
      alert("Company domain is required for email search");
      return;
    }

    setFindingEmails(companyId);
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/companies/${companyId}/find-emails`
      );
      const data = await response.json();

      if (response.ok) {
        // Handle successful response - show modal with results
        console.log("Found emails:", data);
        const emailCount = data.data?.emails?.length || 0;
        const domain = data.data?.domain || company.domain;
        const organization = data.data?.organization || company.name;

        if (emailCount > 0) {
          // Show modal with email results
          setEmailResults({
            emails: data.data.emails,
            companyName: organization,
            domain: domain,
          });
        } else {
          alert(`No emails found for ${organization} (${domain})`);
        }
      } else {
        console.error("Failed to find emails:", data.error);
        alert(`Error: ${data.error}`);
      }
    } catch (error) {
      console.error("Error finding emails:", error);
      alert("Failed to find emails. Please try again.");
    } finally {
      setFindingEmails(null);
    }
  };

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Companies</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center space-x-4">
                <Skeleton className="h-12 w-12 rounded-full" />
                <div className="space-y-2">
                  <Skeleton className="h-4 w-[250px]" />
                  <Skeleton className="h-4 w-[200px]" />
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Companies</CardTitle>
            <Badge variant="secondary">{pagination.totalElements} total</Badge>
          </div>
        </CardHeader>
        <CardContent>
          {companies.length === 0 ? (
            <div className="text-center py-8">
              <Building2 className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold mb-2">No companies found</h3>
              <p className="text-muted-foreground">
                Try adjusting your search criteria or add some companies to get
                started.
              </p>
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Company</TableHead>
                    <TableHead>Domain</TableHead>
                    <TableHead>Industry</TableHead>
                    <TableHead>Size</TableHead>
                    <TableHead>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {companies.map((company) => (
                    <TableRow key={company.id}>
                      <TableCell>
                        <div className="flex items-center space-x-3">
                          <div className="w-10 h-10 bg-primary/10 rounded-full flex items-center justify-center">
                            <Building2 className="w-5 h-5 text-primary" />
                          </div>
                          <div>
                            <div className="font-medium">{company.name}</div>
                            <div className="text-sm text-muted-foreground">
                              ID: {company.id.slice(0, 8)}...
                            </div>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        {company.domain ? (
                          <div className="flex items-center space-x-2">
                            <Globe className="w-4 h-4 text-muted-foreground" />
                            <a
                              href={getDomainUrl(company.domain)}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-blue-600 hover:text-blue-800 underline"
                            >
                              {company.domain}
                            </a>
                          </div>
                        ) : (
                          <span className="text-muted-foreground">
                            No domain
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="text-sm">
                          {company.industry || (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="text-sm">
                          {company.size ? (
                            `${company.size} employees`
                          ) : (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center space-x-2">
                          <Button
                            variant="default"
                            size="sm"
                            onClick={() => handleFindEmails(company.id)}
                            disabled={
                              findingEmails === company.id || !company.domain
                            }
                            className="bg-black hover:bg-gray-800 text-white"
                          >
                            <Mail className="w-4 h-4 mr-2" />
                            {findingEmails === company.id
                              ? "Finding..."
                              : "Find emails"}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Pagination */}
              {pagination.totalPages > 1 && (
                <div className="mt-6">
                  <Pagination>
                    <PaginationContent>
                      <PaginationItem>
                        <PaginationPrevious
                          onClick={() =>
                            onPageChange(pagination.currentPage - 1)
                          }
                          className={
                            pagination.currentPage === 0
                              ? "pointer-events-none opacity-50"
                              : "cursor-pointer"
                          }
                        />
                      </PaginationItem>

                      {Array.from(
                        { length: Math.min(5, pagination.totalPages) },
                        (_, i) => {
                          const page = i;
                          return (
                            <PaginationItem key={page}>
                              <PaginationLink
                                onClick={() => onPageChange(page)}
                                isActive={page === pagination.currentPage}
                                className="cursor-pointer"
                              >
                                {page + 1}
                              </PaginationLink>
                            </PaginationItem>
                          );
                        }
                      )}

                      <PaginationItem>
                        <PaginationNext
                          onClick={() =>
                            onPageChange(pagination.currentPage + 1)
                          }
                          className={
                            pagination.currentPage >= pagination.totalPages - 1
                              ? "pointer-events-none opacity-50"
                              : "cursor-pointer"
                          }
                        />
                      </PaginationItem>
                    </PaginationContent>
                  </Pagination>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>

      {/* Email Results Modal */}
      {emailResults && (
        <EmailResultsModal
          emails={emailResults.emails}
          companyName={emailResults.companyName}
          domain={emailResults.domain}
          onClose={() => setEmailResults(null)}
        />
      )}
    </>
  );
}
