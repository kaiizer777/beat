export interface Channel {
  id: number;
  name: string;
  topicQuery: string;
  articleCount: number;
  cronTime: string; // HH:mm or HH:mm:ss
  timezone: string;
  isActive: boolean;
  lastRunStatus?: 'SUCCESS' | 'FAILED' | 'PENDING' | string | null;
  lastRunAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChannelFormData {
  name: string;
  topicQuery: string;
  articleCount: number;
  cronTime: string;
  timezone: string;
  isActive: boolean;
}

export interface ApiValidationError {
  message?: string;
  fieldErrors?: Record<string, string>;
  status?: number;
}

export interface DigestRun {
  id: number;
  channelId: number;
  channelName: string;
  runAt: string;
  status: 'SUCCESS' | 'FAILED' | 'PENDING' | string;
  errorMessage?: string | null;
  emailSent?: boolean;
  itemCount?: number;
}

export interface NewsItem {
  id: number;
  digestRunId: number;
  title: string;
  url: string;
  sourceName?: string | null;
  publishedAt?: string | null;
  summaryBlurb?: string | null;
  rankPosition: number;
}
