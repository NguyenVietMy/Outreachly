"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Search, Filter, X } from "lucide-react";

interface CompanyFilters {
  search: string;
  companyType: string;
  size: string;
  headquartersCountry: string;
}

interface CompanyFiltersProps {
  filters: CompanyFilters;
  onFilterChange: (filters: Partial<CompanyFilters>) => void;
  onSearch: () => void;
}

// Removed INDUSTRIES - now showing in table instead of filter

const COMPANY_TYPES = [
  "Privately Held",
  "Public Company",
  "Educational Institution",
  "Government Agency",
  "Non Profit Partnership",
  "Self Employed",
  "Self Owned",
  "Self Proprietorship",
];

const COMPANY_SIZES = [
  "1-10",
  "11-50",
  "51-200",
  "201-500",
  "501-1000",
  "1001-5000",
  "5001-10000",
  "10001+",
];

const COUNTRIES = ["United States"];

export function CompanyFilters({
  filters,
  onFilterChange,
  onSearch,
}: CompanyFiltersProps) {
  const [localSearch, setLocalSearch] = useState(filters.search);

  const handleSearchChange = (value: string) => {
    setLocalSearch(value);
    onFilterChange({ search: value });
  };

  const handleFilterChange = (key: keyof CompanyFilters, value: string) => {
    // Treat "all" as empty string for filtering
    const filterValue = value === "all" ? "" : value;
    onFilterChange({ [key]: filterValue });
  };

  const handleSearch = () => {
    onSearch();
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      handleSearch();
    }
  };

  const clearFilters = () => {
    setLocalSearch("");
    onFilterChange({
      search: "",
      companyType: "all",
      size: "all",
      headquartersCountry: "all",
    });
    onSearch();
  };

  const hasActiveFilters =
    (filters.companyType && filters.companyType !== "all") ||
    (filters.size && filters.size !== "all") ||
    (filters.headquartersCountry && filters.headquartersCountry !== "all");

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Filter className="w-5 h-5" />
          Filters
          {hasActiveFilters && (
            <Badge variant="secondary" className="ml-auto">
              Active
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Search */}
        <div className="space-y-2">
          <Label htmlFor="search">Company Name</Label>
          <Input
            id="search"
            placeholder="Search companies..."
            value={localSearch}
            onChange={(e) => handleSearchChange(e.target.value)}
            onKeyPress={handleKeyPress}
          />
        </div>

        {/* Industry removed - now shown in table */}

        {/* Company Type Filter */}
        <div className="space-y-2">
          <Label>Company Type</Label>
          <Select
            value={filters.companyType}
            onValueChange={(value) => handleFilterChange("companyType", value)}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Types</SelectItem>
              {COMPANY_TYPES.map((type) => (
                <SelectItem key={type} value={type}>
                  {type}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Company Size Filter */}
        <div className="space-y-2">
          <Label>Company Size</Label>
          <Select
            value={filters.size}
            onValueChange={(value) => handleFilterChange("size", value)}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select size" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Sizes</SelectItem>
              {COMPANY_SIZES.map((size) => (
                <SelectItem key={size} value={size}>
                  {size} employees
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Headquarters Country Filter */}
        <div className="space-y-2">
          <Label>Headquarters</Label>
          <Select
            value={filters.headquartersCountry}
            onValueChange={(value) =>
              handleFilterChange("headquartersCountry", value)
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select country" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Countries</SelectItem>
              {COUNTRIES.map((country) => (
                <SelectItem key={country} value={country}>
                  {country}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Search Button */}
        <Button size="sm" onClick={handleSearch} className="w-full">
          <Search className="w-4 h-4 mr-2" />
          Search
        </Button>

        {/* Clear Filters */}
        <Button
          variant="outline"
          size="sm"
          onClick={clearFilters}
          className="w-full"
        >
          <X className="w-4 h-4 mr-2" />
          Clear All Filters
        </Button>
      </CardContent>
    </Card>
  );
}
