"use client";

import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  listTemplates,
  parseContent,
  TemplateModel,
  TemplatePlatform,
} from "@/lib/templates";

interface TemplateBrowserModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  platform?: TemplatePlatform | "ALL";
  templates?: TemplateModel[];
  selectedTemplateId?: string | null;
  onSelect?: (template: TemplateModel) => void;
  onUse?: (template: TemplateModel) => void;
}

export default function TemplateBrowserModal({
  open,
  onOpenChange,
  platform = "EMAIL",
  templates,
  selectedTemplateId,
  onSelect,
  onUse,
}: TemplateBrowserModalProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<TemplateModel[]>(templates || []);
  const [selectedId, setSelectedId] = useState<string | null>(
    selectedTemplateId || null
  );

  useEffect(() => {
    setSelectedId(selectedTemplateId || null);
  }, [selectedTemplateId]);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await listTemplates(
          platform === "ALL" ? undefined : platform
        );
        if (!mounted) return;
        console.log(
          `TemplateBrowserModal: Fetched ${data.length} templates for platform ${platform}`,
          data
        );
        setItems(data);
      } catch (e: any) {
        if (!mounted) return;
        setError(e?.message || "Failed to load templates");
      } finally {
        if (mounted) setLoading(false);
      }
    };
    load();
    return () => {
      mounted = false;
    };
  }, [platform]);

  const handleClick = (t: TemplateModel) => {
    if (selectedId === t.id) {
      onUse?.(t);
    } else {
      setSelectedId(t.id);
      onSelect?.(t);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[420px] max-w-[95vw] h-[90vh] overflow-hidden">
        <DialogHeader>
          <DialogTitle>Email Templates</DialogTitle>
        </DialogHeader>
        <div className="space-y-3 h-[82vh] overflow-y-auto pr-2">
          {loading ? (
            <div className="text-sm text-muted-foreground">
              Loading templates...
            </div>
          ) : error ? (
            <div className="text-sm text-red-600">{error}</div>
          ) : items.length === 0 ? (
            <div className="text-sm text-muted-foreground">
              No templates available
            </div>
          ) : (
            <div className="space-y-2">
              {items.map((t) => {
                const content = parseContent<any>(t.contentJson);
                const isSelected = selectedId === t.id;
                return (
                  <Card
                    key={t.id}
                    className={`hover:shadow-md transition-all cursor-pointer ${
                      isSelected
                        ? "ring-2 ring-blue-500 bg-blue-50"
                        : "hover:bg-gray-50"
                    }`}
                    onClick={() => handleClick(t)}
                  >
                    <CardHeader className="py-2">
                      <CardTitle className="text-sm font-medium leading-tight">
                        {t.name}
                      </CardTitle>
                      <Badge variant="secondary" className="w-fit text-xs">
                        {t.category || "No tag"}
                      </Badge>
                    </CardHeader>
                    <CardContent className="pt-0 pb-3">
                      <div className="text-xs text-gray-600 space-y-1">
                        {t.platform === "EMAIL" && content?.subject && (
                          <div>
                            <strong>Subject:</strong> {content.subject}
                          </div>
                        )}
                        <div className="text-xs text-gray-500 line-clamp-3">
                          {(() => {
                            const body = content?.body || "";
                            return body.length > 100
                              ? body.substring(0, 100) + "..."
                              : body;
                          })()}
                        </div>
                        <div className="text-xs text-gray-500">
                          Platform: {t.platform}
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          )}
          <div className="flex justify-end">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
