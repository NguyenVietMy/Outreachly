"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Search, Filter } from "lucide-react";

interface CompanyFilters {
  search: string;
}

interface CompanyFiltersProps {
  filters: CompanyFilters;
  onFilterChange: (filters: Partial<CompanyFilters>) => void;
  onSearch: () => void;
}

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
    onFilterChange({ search: "" });
    onSearch();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Filter className="w-5 h-5" />
          Filters
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Search */}
        <div className="space-y-2">
          <Label htmlFor="search">Company Name</Label>
          <div className="flex gap-2">
            <Input
              id="search"
              placeholder="Search companies..."
              value={localSearch}
              onChange={(e) => handleSearchChange(e.target.value)}
              onKeyPress={handleKeyPress}
            />
            <Button size="sm" onClick={handleSearch}>
              <Search className="w-4 h-4" />
            </Button>
          </div>
        </div>

        {/* Future filters can be added here */}
        <div className="text-sm text-muted-foreground">
          <p>More filters coming soon:</p>
          <ul className="list-disc list-inside mt-2 space-y-1">
            <li>Industry</li>
            <li>Company Size</li>
            <li>Location</li>
            <li>Technologies</li>
          </ul>
        </div>

        {/* Clear Filters */}
        <Button
          variant="outline"
          size="sm"
          onClick={clearFilters}
          className="w-full"
        >
          Clear Filters
        </Button>
      </CardContent>
    </Card>
  );
}
