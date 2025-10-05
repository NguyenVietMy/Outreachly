"use client";

import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Eye, BookOpen } from "lucide-react";

interface RichTextEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  isHtml?: boolean;
  onHtmlChange?: (isHtml: boolean) => void;
  maxLength?: number;
  error?: string;
  onBrowseTemplates?: () => void;
}

export function RichTextEditor({
  value,
  onChange,
  placeholder = "Enter your email content...",
  isHtml = false,
  onHtmlChange,
  maxLength = 10000,
  error,
  onBrowseTemplates,
}: RichTextEditorProps) {
  const [history, setHistory] = useState<string[]>([value]);
  const [historyIndex, setHistoryIndex] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const saveToHistory = (newValue: string) => {
    const newHistory = history.slice(0, historyIndex + 1);
    newHistory.push(newValue);
    setHistory(newHistory);
    setHistoryIndex(newHistory.length - 1);
  };

  const insertVariable = (variable: string) => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const token = `{{${variable}}}`;

    const newValue =
      textarea.value.substring(0, start) +
      token +
      textarea.value.substring(end);

    onChange(newValue);
    saveToHistory(newValue);
  };

  const renderPreview = () => {
    if (!isHtml) {
      // Simple markdown-like rendering
      return value
        .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
        .replace(/\*(.*?)\*/g, "<em>$1</em>")
        .replace(/__(.*?)__/g, "<u>$1</u>")
        .replace(/^• (.*$)/gm, "<li>$1</li>")
        .replace(/^\d+\. (.*$)/gm, "<li>$1</li>")
        .replace(/^> (.*$)/gm, "<blockquote>$1</blockquote>")
        .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
        .replace(/\n/g, "<br>");
    }
    return value;
  };

  const availableVariables = [
    "firstName",
    "lastName",
    "companyName",
    "email",
    "position",
    "website",
    "phone",
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <Label className="text-base font-medium">
          Content{" "}
          {isHtml && (
            <Badge variant="secondary" className="ml-2">
              HTML
            </Badge>
          )}
        </Label>
        <div className="flex items-center gap-2">
          {onBrowseTemplates && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onBrowseTemplates}
              className="flex items-center gap-1"
            >
              <BookOpen className="h-4 w-4" />
              Browse Templates
            </Button>
          )}
          <Dialog>
            <DialogTrigger asChild>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="flex items-center gap-1"
              >
                <Eye className="h-4 w-4" />
                Show Preview
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>Email Preview</DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                <div className="prose prose-sm max-w-none">
                  <div
                    dangerouslySetInnerHTML={{
                      __html:
                        renderPreview() ||
                        "<p class='text-gray-400'>No content to preview</p>",
                    }}
                  />
                </div>
              </div>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      <div className="space-y-4">
        <div className="space-y-3">
          {/* Variables */}
          <Card>
            <CardContent className="p-3">
              <div className="space-y-2">
                <Label className="text-sm font-medium">Variables</Label>
                <div className="flex flex-wrap gap-1">
                  {availableVariables.map((variable) => (
                    <Button
                      key={variable}
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => insertVariable(variable)}
                      className="text-xs"
                    >
                      {`{{${variable}}}`}
                    </Button>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Editor */}
          <Textarea
            ref={textareaRef}
            value={value}
            onChange={(e) => {
              onChange(e.target.value);
              saveToHistory(e.target.value);
            }}
            placeholder={placeholder}
            className={`min-h-[300px] ${error ? "border-red-500" : ""}`}
            maxLength={maxLength}
          />

          <div className="flex justify-between text-xs text-gray-500">
            <span>{error || ""}</span>
            <span>
              {value.length}/{maxLength}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
