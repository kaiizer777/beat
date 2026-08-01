import { Channel, ChannelFormData, ApiValidationError, DigestRun, NewsItem } from '../types/channel';
import { getSession } from 'next-auth/react';

const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error implements ApiValidationError {
  status: number;
  fieldErrors?: Record<string, string>;

  constructor(message: string, status: number, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function getAuthHeaders(): Promise<Record<string, string>> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  try {
    if (typeof window !== 'undefined') {
      const session = await getSession();
      if (session?.accessToken) {
        headers['Authorization'] = `Bearer ${session.accessToken}`;
      }
    } else {
      const { auth } = await import('@/auth');
      const session = await auth();
      if (session?.accessToken) {
        headers['Authorization'] = `Bearer ${session.accessToken}`;
      }
    }
  } catch (err) {
    // Session retrieval fallback
  }
  return headers;
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      window.location.href = '/login';
    }
    throw new ApiError('Unauthorized. Please sign in.', 401);
  }

  if (response.status === 204) {
    return {} as T;
  }

  let data: any;
  try {
    data = await response.json();
  } catch (err) {
    if (!response.ok) {
      throw new ApiError(`HTTP Error ${response.status}: ${response.statusText}`, response.status);
    }
    return {} as T;
  }

  if (!response.ok) {
    const status = response.status;
    let mainMessage = data.message || data.error || `Request failed with status ${status}`;
    let fieldErrors: Record<string, string> | undefined;

    if (data.errors && typeof data.errors === 'object') {
      fieldErrors = data.errors;
      const firstFieldErr = Object.values(data.errors)[0];
      if (firstFieldErr && typeof firstFieldErr === 'string') {
        mainMessage = firstFieldErr;
      }
    }

    throw new ApiError(mainMessage, status, fieldErrors);
  }

  return data as T;
}

export async function fetchChannels(): Promise<Channel[]> {
  try {
    const headers = await getAuthHeaders();
    const res = await fetch(`${API_BASE_URL}/api/channels`, {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    return await handleResponse<Channel[]>(res);
  } catch (err: any) {
    if (err instanceof ApiError) throw err;
    throw new ApiError(err.message || 'Failed to connect to backend server', 0);
  }
}

export async function fetchChannelById(id: number): Promise<Channel> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE_URL}/api/channels/${id}`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  return await handleResponse<Channel>(res);
}

export async function createChannel(data: ChannelFormData): Promise<Channel> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE_URL}/api/channels`, {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
  });
  return await handleResponse<Channel>(res);
}

export async function updateChannel(id: number, data: ChannelFormData): Promise<Channel> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE_URL}/api/channels/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(data),
  });
  return await handleResponse<Channel>(res);
}

export async function deleteChannel(id: number): Promise<void> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE_URL}/api/channels/${id}`, {
    method: 'DELETE',
    headers,
  });
  await handleResponse<void>(res);
}

export async function fetchChannelRuns(channelId: number): Promise<DigestRun[]> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE_URL}/api/channels/${channelId}/runs`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  return await handleResponse<DigestRun[]>(res);
}

export async function fetchRunDetails(runId: number): Promise<DigestRun> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE_URL}/api/runs/${runId}`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  return await handleResponse<DigestRun>(res);
}

export async function fetchRunItems(runId: number): Promise<NewsItem[]> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE_URL}/api/runs/${runId}/items`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  return await handleResponse<NewsItem[]>(res);
}

export async function triggerRunNow(channelId: number): Promise<DigestRun> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE_URL}/api/channels/${channelId}/run-now`, {
    method: 'POST',
    headers,
  });
  return await handleResponse<DigestRun>(res);
}

export async function checkBackendHealth(): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE_URL}/actuator/health`, {
      method: 'GET',
      cache: 'no-store',
    });
    if (!res.ok) return false;
    const data = await res.json();
    return data.status === 'UP';
  } catch (e) {
    return false;
  }
}

