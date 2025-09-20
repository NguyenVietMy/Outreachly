"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useCampaigns } from "@/hooks/useCampaigns";
import { Search } from "lucide-react";

interface CampaignSelectionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (campaignId: string) => void;
  title?: string;
  description?: string;
  itemCount?: number;
  itemName?: string;
  isLoading?: boolean;
}

export function CampaignSelectionModal({
  isOpen,
  onClose,
  onConfirm,
  title = "Select Campaign",
  description = "Choose where to add your items",
  itemCount = 0,
  itemName = "item(s)",
  isLoading = false,
}: CampaignSelectionModalProps) {
  const [selectedCampaignId, setSelectedCampaignId] =
    useState<string>("default");
  const [campaignSearchTerm, setCampaignSearchTerm] = useState("");
  const { campaigns, loading: campaignsLoading } = useCampaigns();

  const handleConfirm = () => {
    onConfirm(selectedCampaignId);
  };

  const filteredCampaigns = campaigns.filter((campaign) =>
    campaign.name.toLowerCase().includes(campaignSearchTerm.toLowerCase())
  );

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium mb-2 block">
              {description}
            </label>
            <div className="space-y-2">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search campaigns..."
                  value={campaignSearchTerm}
                  onChange={(e) => setCampaignSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
              <Select
                value={selectedCampaignId}
                onValueChange={setSelectedCampaignId}
                disabled={campaignsLoading}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select campaign..." />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="default">Default (No Campaign)</SelectItem>
                  {filteredCampaigns.map((campaign) => (
                    <SelectItem key={campaign.id} value={campaign.id}>
                      {campaign.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          {itemCount > 0 && (
            <div className="text-sm text-muted-foreground">
              {itemCount} {itemName} will be added to the selected campaign.
            </div>
          )}
          <div className="flex gap-2 justify-end">
            <Button variant="outline" onClick={onClose} disabled={isLoading}>
              Cancel
            </Button>
            <Button
              onClick={handleConfirm}
              disabled={isLoading || campaignsLoading}
            >
              {isLoading ? "Adding..." : "Add to Campaign"}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
