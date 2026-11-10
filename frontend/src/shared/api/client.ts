export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message: string;
  errorCode: string | null;
  timestamp: string;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly status: number,
  ) {
    super(message);
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...init?.headers,
      },
    });
  } catch {
    throw new ApiError(
      "Cannot reach the backend. Check that it is running, then try again.",
      "NETWORK_ERROR",
      0,
    );
  }
  let body: ApiEnvelope<T>;
  try {
    body = (await response.json()) as ApiEnvelope<T>;
  } catch {
    throw new ApiError(
      "The backend returned an unexpected response.",
      "INVALID_RESPONSE",
      response.status,
    );
  }
  if (!response.ok || !body.success) {
    throw new ApiError(
      body.message || "The request failed.",
      body.errorCode || "REQUEST_FAILED",
      response.status,
    );
  }
  return body.data;
}
