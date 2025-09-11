"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  ALLOWED_VARS,
  TemplateContentEmail,
  TemplateContentLinkedIn,
  TemplatePlatform,
  countWords,
} from "@/lib/templates";

export interface TemplateEditorValue {
  name: string;
  platform: TemplatePlatform;
  category?: string;
  subject?: string;
  body: string;
}

export function TemplateEditor({
  initial,
  mode,
  onChange,
  onSave,
  onCancel,
  saving,
  showAiButton = false,
}: {
  initial: TemplateEditorValue;
  mode: "create" | "edit";
  onChange?: (val: TemplateEditorValue) => void;
  onSave?: (val: TemplateEditorValue) => void | Promise<void>;
  onCancel?: () => void;
  saving?: boolean;
  showAiButton?: boolean;
}) {
  const [value, setValue] = useState<TemplateEditorValue>(initial);
  const bodyRef = useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => setValue(initial), [initial]);

  const setField = (key: keyof TemplateEditorValue, v: any) => {
    const next = { ...value, [key]: v };
    setValue(next);
    onChange?.(next);
  };

  const insertVar = (variable: (typeof ALLOWED_VARS)[number]) => {
    const el = bodyRef.current;
    const start = el?.selectionStart ?? value.body.length;
    const end = el?.selectionEnd ?? value.body.length;
    const token = `{{${variable}}}`;
    const before = value.body.slice(0, start);
    const after = value.body.slice(end);
    const next = `${before}${token}${after}`;
    setField("body", next);
    setTimeout(() => {
      if (el) {
        const pos = before.length + token.length;
        el.selectionStart = el.selectionEnd = pos;
        el.focus();
      }
    }, 0);
  };

  const wordCount = useMemo(() => countWords(value.body), [value.body]);

  return (
    <div className="space-y-3">
      <Input
        placeholder="Name"
        value={value.name}
        onChange={(e) => setField("name", e.target.value)}
      />
      <Input
        placeholder="Category (optional)"
        value={value.category || ""}
        onChange={(e) => setField("category", e.target.value)}
      />
      {value.platform === "EMAIL" && (
        <Input
          placeholder="Subject"
          value={value.subject || ""}
          onChange={(e) => setField("subject", e.target.value)}
        />
      )}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <div className="text-xs text-muted-foreground">
            Body ({wordCount} words)
          </div>
          <div className="flex gap-1">
            {ALLOWED_VARS.map((v) => (
              <Button
                key={v}
                size="sm"
                variant="outline"
                onClick={() => insertVar(v)}
              >
                {`{{${v}}}`}
              </Button>
            ))}
          </div>
        </div>
        <Textarea
          ref={bodyRef}
          rows={10}
          value={value.body}
          onChange={(e) => setField("body", e.target.value)}
        />
      </div>
      <div className="flex gap-2">
        <Button
          onClick={() => onSave?.(value)}
          disabled={
            !value.name ||
            !value.body ||
            (value.platform === "EMAIL" && !value.subject) ||
            !!saving
          }
        >
          {saving ? "Saving..." : "Save"}
        </Button>
        {showAiButton && (
          <Button variant="outline" disabled title="Coming soon">
            Generate with AI
          </Button>
        )}
        {mode === "edit" && (
          <Button variant="outline" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
      <div className="space-y-2">
        <Tabs defaultValue="rendered">
          <TabsList className="grid grid-cols-2 w-full">
            <TabsTrigger value="rendered">Rendered</TabsTrigger>
            <TabsTrigger value="raw">Raw</TabsTrigger>
          </TabsList>
          <TabsContent value="rendered" className="pt-3">
            <div className="text-sm space-y-2">
              {value.platform === "EMAIL" && (
                <div>
                  <div className="text-xs text-muted-foreground">Subject</div>
                  <div>{value.subject}</div>
                  <Separator className="my-2" />
                </div>
              )}
              <div className="whitespace-pre-wrap">{value.body}</div>
            </div>
          </TabsContent>
          <TabsContent value="raw" className="pt-3">
            <pre className="text-xs bg-muted p-3 rounded">
              {JSON.stringify(
                { subject: value.subject, body: value.body },
                null,
                2
              )}
            </pre>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
