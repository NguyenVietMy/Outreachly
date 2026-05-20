import { useState, useCallback } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { API_BASE_URL } from "@/lib/config";

interface SourceCitation {
  sourceType: string;
  sourceKey: string;
  score: number;
}

interface RoutingDecision {
  sourceType: string;
  decision: string;
  reason: string;
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  sources?: SourceCitation[];
  routingDecisions?: RoutingDecision[];
}

interface IndexStatus {
  [sourceType: string]: number;
}

export function useChat() {
  const { user } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [indexStatus, setIndexStatus] = useState<IndexStatus | null>(null);
  const [reindexing, setReindexing] = useState(false);

  const send = useCallback(
    async (message: string) => {
      if (!user || !message.trim()) return;

      const userMessage: ChatMessage = { role: "user", content: message };
      setMessages((prev) => [...prev, userMessage]);
      setLoading(true);

      try {
        const history = messages.map((m) => ({
          role: m.role,
          content: m.content,
        }));

        const res = await fetch(`${API_BASE_URL}/api/personal/chat`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ message, history }),
        });

        if (!res.ok) throw new Error("Chat request failed");

        const data = await res.json();
        const assistantMessage: ChatMessage = {
          role: "assistant",
          content: data.message,
          sources: data.sources,
          routingDecisions: data.routingDecisions,
        };
        setMessages((prev) => [...prev, assistantMessage]);
      } catch {
        setMessages((prev) => [
          ...prev,
          {
            role: "assistant",
            content: "Sorry, something went wrong. Please try again.",
          },
        ]);
      } finally {
        setLoading(false);
      }
    },
    [user, messages]
  );

  const fetchIndexStatus = useCallback(async () => {
    if (!user) return;
    try {
      const res = await fetch(
        `${API_BASE_URL}/api/personal/chat/index-status`,
        { credentials: "include" }
      );
      if (res.ok) {
        setIndexStatus(await res.json());
      }
    } catch {
      // ignore
    }
  }, [user]);

  const reindex = useCallback(async () => {
    if (!user) return;
    setReindexing(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/personal/chat/reindex`, {
        method: "POST",
        credentials: "include",
      });
      if (res.ok) {
        await fetchIndexStatus();
      }
    } catch {
      // ignore
    } finally {
      setReindexing(false);
    }
  }, [user, fetchIndexStatus]);

  const clearMessages = useCallback(() => {
    setMessages([]);
  }, []);

  return {
    messages,
    loading,
    indexStatus,
    reindexing,
    send,
    fetchIndexStatus,
    reindex,
    clearMessages,
  };
}
