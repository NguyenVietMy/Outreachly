import { useState, useEffect } from 'react';
import { API_BASE_URL } from '@/lib/config';

interface ActivityFeedItem {
  id: string;
  orgId: string;
  userId: number;
  activityType: 'csv_import' | 'campaign' | 'domain' | 'checkpoint';
  title: string;
  description: string;
  status: 'success' | 'error' | 'warning' | 'paused' | 'processing';
  createdAt: string;
  updatedAt: string;
}

interface UseActivityFeedReturn {
  activities: ActivityFeedItem[];
  loading: boolean;
  error: string | null;
  refetch: () => void;
}

export function useActivityFeed(): UseActivityFeedReturn {
  const [activities, setActivities] = useState<ActivityFeedItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchActivities = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await fetch(`${API_BASE_URL}/api/activity-feed/recent`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
      });

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error('Authentication required. Please log in again.');
        }
        throw new Error(`Failed to fetch activities: ${response.statusText}`);
      }

      const data = await response.json();
      setActivities(data);
    } catch (err) {
      console.error('Error fetching activity feed:', err);
      setError(err instanceof Error ? err.message : 'Failed to fetch activities');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchActivities();
  }, []);

  return {
    activities,
    loading,
    error,
    refetch: fetchActivities,
  };
}
