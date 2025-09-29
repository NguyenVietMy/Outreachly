"use client";

import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Bold,
  Italic,
  Underline,
  List,
  ListOrdered,
  Quote,
  Link,
  Image,
  AlignLeft,
  AlignCenter,
  AlignRight,
  Eye,
  EyeOff,
  Type,
  Palette,
  Code,
  Undo,
  Redo,
  Save,
} from "lucide-react";

interface RichTextEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  isHtml?: boolean;
  onHtmlChange?: (isHtml: boolean) => void;
  maxLength?: number;
  error?: string;
}

export function RichTextEditor({
  value,
  onChange,
  placeholder = "Enter your email content...",
  isHtml = false,
  onHtmlChange,
  maxLength = 10000,
  error,
}: RichTextEditorProps) {
  const [showPreview, setShowPreview] = useState(false);
  const [history, setHistory] = useState<string[]>([value]);
  const [historyIndex, setHistoryIndex] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const executeCommand = (command: string, value?: string) => {
    if (!isHtml) {
      // For plain text, we'll just insert formatting markers
      const textarea = textareaRef.current;
      if (!textarea) return;

      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const selectedText = value || textarea.value.substring(start, end);

      let formattedText = "";
      switch (command) {
        case "bold":
          formattedText = `**${selectedText}**`;
          break;
        case "italic":
          formattedText = `*${selectedText}*`;
          break;
        case "underline":
          formattedText = `__${selectedText}__`;
          break;
        case "list":
          formattedText = `• ${selectedText}`;
          break;
        case "orderedList":
          formattedText = `1. ${selectedText}`;
          break;
        case "quote":
          formattedText = `> ${selectedText}`;
          break;
        case "link":
          formattedText = `[${selectedText}](url)`;
          break;
        default:
          formattedText = selectedText;
      }

      const newValue =
        textarea.value.substring(0, start) +
        formattedText +
        textarea.value.substring(end);

      onChange(newValue);
      saveToHistory(newValue);
    } else {
      // For HTML, we'll use document.execCommand (deprecated but simple)
      document.execCommand(command, false, value);
    }
  };

  const saveToHistory = (newValue: string) => {
    const newHistory = history.slice(0, historyIndex + 1);
    newHistory.push(newValue);
    setHistory(newHistory);
    setHistoryIndex(newHistory.length - 1);
  };

  const undo = () => {
    if (historyIndex > 0) {
      const newIndex = historyIndex - 1;
      setHistoryIndex(newIndex);
      onChange(history[newIndex]);
    }
  };

  const redo = () => {
    if (historyIndex < history.length - 1) {
      const newIndex = historyIndex + 1;
      setHistoryIndex(newIndex);
      onChange(history[newIndex]);
    }
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
          {onHtmlChange && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onHtmlChange(!isHtml)}
              className="flex items-center gap-1"
            >
              <Type className="h-4 w-4" />
              {isHtml ? "Plain Text" : "HTML"}
            </Button>
          )}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setShowPreview(!showPreview)}
            className="flex items-center gap-1"
          >
            {showPreview ? (
              <EyeOff className="h-4 w-4" />
            ) : (
              <Eye className="h-4 w-4" />
            )}
            {showPreview ? "Hide" : "Show"} Preview
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="space-y-3">
          {/* Toolbar */}
          <Card>
            <CardContent className="p-3">
              <div className="flex flex-wrap gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={undo}
                  disabled={historyIndex <= 0}
                  title="Undo"
                >
                  <Undo className="h-4 w-4" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={redo}
                  disabled={historyIndex >= history.length - 1}
                  title="Redo"
                >
                  <Redo className="h-4 w-4" />
                </Button>

                <div className="w-px h-6 bg-gray-300 mx-1" />

                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => executeCommand("bold")}
                  title="Bold"
                >
                  <Bold className="h-4 w-4" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => executeCommand("italic")}
                  title="Italic"
                >
                  <Italic className="h-4 w-4" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => executeCommand("underline")}
                  title="Underline"
                >
                  <Underline className="h-4 w-4" />
                </Button>

                <div className="w-px h-6 bg-gray-300 mx-1" />

                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => executeCommand("list")}
                  title="Bullet List"
                >
                  <List className="h-4 w-4" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => executeCommand("orderedList")}
                  title="Numbered List"
                >
                  <ListOrdered className="h-4 w-4" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => executeCommand("quote")}
                  title="Quote"
                >
                  <Quote className="h-4 w-4" />
                </Button>

                <div className="w-px h-6 bg-gray-300 mx-1" />

                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => executeCommand("link")}
                  title="Link"
                >
                  <Link className="h-4 w-4" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => executeCommand("image")}
                  title="Image"
                >
                  <Image className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>

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

        {/* Preview */}
        {showPreview && (
          <div className="space-y-3">
            <Label className="text-sm font-medium">Preview</Label>
            <Card>
              <CardContent className="p-4">
                <div
                  className="prose prose-sm max-w-none"
                  dangerouslySetInnerHTML={{
                    __html:
                      renderPreview() ||
                      "<p class='text-gray-400'>No content to preview</p>",
                  }}
                />
              </CardContent>
            </Card>
          </div>
        )}
      </div>
    </div>
  );
}
