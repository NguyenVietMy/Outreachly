"use client";

import { useEffect, useRef, useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { useChat, ChatMessage } from "@/hooks/useChat";
import { MarkdownText } from "@/lib/renderMarkdown";
import {
  MessageCircle,
  Send,
  Loader2,
  RefreshCw,
  Trash2,
  ChevronDown,
  Database,
} from "lucide-react";

function SourceBadge({
  sourceType,
  sourceKey,
  score,
}: {
  sourceType: string;
  sourceKey: string;
  score: number;
}) {
  const labels: Record<string, string> = {
    github_readme: "GitHub",
    obsidian_diff: "Notes",
    resume_section: "Resume",
    goal: "Goal",
    task: "Task",
    roadmap: "Roadmap",
  };

  return (
    <Badge variant="outline" className="text-[10px] gap-1">
      <span className="text-muted-foreground">
        {labels[sourceType] || sourceType}:
      </span>
      <span className="truncate max-w-[120px]">{sourceKey}</span>
      <span className="text-muted-foreground">
        ({(score * 100).toFixed(0)}%)
      </span>
    </Badge>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === "user";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"} mb-3`}>
      <div
        className={`max-w-[85%] ${
          isUser
            ? "bg-[#2563eb] text-white rounded-2xl rounded-br-md"
            : "bg-[#f5f5f5] text-[#333] rounded-2xl rounded-bl-md"
        } px-4 py-2.5`}
      >
        <p className="text-sm whitespace-pre-wrap leading-relaxed">
          {isUser ? message.content : <MarkdownText>{message.content}</MarkdownText>}
        </p>
        {message.sources && message.sources.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-2 pt-2 border-t border-border/50">
            {message.sources.map((s, i) => (
              <SourceBadge key={i} {...s} />
            ))}
          </div>
        )}
        {message.routingDecisions && message.routingDecisions.length > 0 && (
          <RoutingInfo decisions={message.routingDecisions} />
        )}
      </div>
    </div>
  );
}

function RoutingInfo({
  decisions,
}: {
  decisions: { sourceType: string; decision: string; reason: string }[];
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="mt-1">
      <button
        onClick={() => setExpanded(!expanded)}
        className="text-[10px] text-muted-foreground hover:text-foreground flex items-center gap-1 transition-colors"
      >
        <Database className="h-3 w-3" />
        Routing
        <ChevronDown
          className={`h-3 w-3 transition-transform ${expanded ? "rotate-180" : ""}`}
        />
      </button>
      {expanded && (
        <div className="mt-1 space-y-0.5">
          {decisions.map((d, i) => (
            <div key={i} className="text-[10px] text-muted-foreground">
              <span className="font-medium">{d.sourceType}</span>:{" "}
              <span
                className={
                  d.decision === "inline" ? "text-green-500" : "text-blue-500"
                }
              >
                {d.decision}
              </span>{" "}
              — {d.reason}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function ChatPanel() {
  const {
    messages,
    loading,
    indexStatus,
    reindexing,
    send,
    fetchIndexStatus,
    reindex,
    clearMessages,
  } = useChat();
  const [input, setInput] = useState("");
  const [open, setOpen] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) {
      fetchIndexStatus();
    }
  }, [open, fetchIndexStatus]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = () => {
    if (!input.trim() || loading) return;
    send(input.trim());
    setInput("");
  };

  const totalChunks = indexStatus
    ? Object.values(indexStatus).reduce((a, b) => a + b, 0)
    : 0;

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button
          size="icon"
          className="fixed bottom-6 right-6 h-14 w-14 rounded-full shadow-lg z-50"
        >
          <MessageCircle className="h-6 w-6" />
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="w-[420px] sm:w-[480px] p-0 flex flex-col">
        <SheetHeader className="px-4 py-3 border-b shrink-0">
          <div className="flex items-center justify-between">
            <SheetTitle className="text-base">Career Coach</SheetTitle>
            <div className="flex items-center gap-1">
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={reindex}
                disabled={reindexing}
                title={`Reindex knowledge base (${totalChunks} chunks)`}
              >
                {reindexing ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <RefreshCw className="h-4 w-4" />
                )}
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={clearMessages}
                title="Clear chat"
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </div>
          {indexStatus && (
            <div className="flex flex-wrap gap-1 mt-1">
              {Object.entries(indexStatus).map(([type, count]) => (
                <Badge key={type} variant="secondary" className="text-[10px]">
                  {type}: {count}
                </Badge>
              ))}
            </div>
          )}
        </SheetHeader>

        <div className="flex-1 overflow-y-auto px-4 py-4 min-h-0">
          {messages.length === 0 && (
            <div className="flex flex-col items-center justify-center h-full text-center text-muted-foreground">
              <MessageCircle className="h-12 w-12 mb-3 opacity-20" />
              <p className="text-sm font-medium">
                Ask about your career, resume, or study plan
              </p>
              <p className="text-xs mt-1 max-w-[280px]">
                I have access to your profile, resume, projects, goals, and
                study progress. Uses Hybrid RAG (vector + keyword search) with
                smart routing.
              </p>
            </div>
          )}
          {messages.map((msg, i) => (
            <MessageBubble key={i} message={msg} />
          ))}
          {loading && (
            <div className="flex justify-start mb-3">
              <div className="bg-muted rounded-2xl rounded-bl-md px-4 py-2.5">
                <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <div className="px-4 py-3 border-t shrink-0">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
            className="flex gap-2"
          >
            <Input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask about your career..."
              disabled={loading}
              className="flex-1"
            />
            <Button
              type="submit"
              size="icon"
              disabled={loading || !input.trim()}
            >
              <Send className="h-4 w-4" />
            </Button>
          </form>
        </div>
      </SheetContent>
    </Sheet>
  );
}
