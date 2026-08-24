import { PrismaAdapter } from "@auth/prisma-adapter";
import type { PrismaClient } from "@prisma/client";

/**
 * Wraps Prisma adapter methods with automatic retry for serverless database cold starts (Neon PostgreSQL).
 */
function withRetry<T extends (...args: any[]) => Promise<any>>(fn: T, maxRetries = 2): T {
  return (async (...args: any[]) => {
    let lastError: any;
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return await fn(...args);
      } catch (err: any) {
        lastError = err;
        const msg = String(err?.message || err);
        const isConnectionError =
          msg.includes("Can't reach database server") ||
          msg.includes("connection closed") ||
          msg.includes("Connection lost") ||
          msg.includes("Server has closed the connection") ||
          msg.includes("ETIMEDOUT") ||
          msg.includes("ECONNRESET") ||
          err?.code === "P1001" ||
          err?.code === "P1002" ||
          err?.name === "PrismaClientInitializationError" ||
          err?.name === "PrismaClientKnownRequestError";

        if (isConnectionError && attempt < maxRetries) {
          console.warn(`[Neon DB Auto-Retry] Database cold start detected (${attempt + 1}/${maxRetries}). Retrying query...`);
          await new Promise((r) => setTimeout(r, 800 * (attempt + 1)));
          continue;
        }
        throw err;
      }
    }
    throw lastError;
  }) as T;
}

export function createResilientPrismaAdapter(prisma: PrismaClient) {
  const base = PrismaAdapter(prisma);
  const wrapped: Record<string, any> = {};

  for (const [key, value] of Object.entries(base)) {
    if (typeof value === "function") {
      wrapped[key] = withRetry(value as any);
    } else {
      wrapped[key] = value;
    }
  }

  return wrapped as ReturnType<typeof PrismaAdapter>;
}
