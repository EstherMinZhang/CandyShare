const BASE = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
export type User = {
  id: number;
  name: string;
  email: string;
  contactDetails: string;
};
export type Registration = {
  name: string;
  email: string;
  password: string;
};
export type ListingInput = {
  notes: string;
  region: string;
};
export type Listing = ListingInput & {
  id: number;
  ownerId: number;
  ownerName: string;
  createdAt: string;
  status: "AVAILABLE" | "RESERVED" | "COMPLETED" | "WITHDRAWN";
  handoff: null | {
    ownerContact: string;
    claimantName: string | null;
    claimantContact: string | null;
    instructions: string;
    claimId: number | null;
  };
};
export type Claim = {
  id: number;
  status: "RESERVED" | "CANCELLED" | "COMPLETED";
  createdAt: string;
  resolvedAt: string | null;
  listing: Listing;
};
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
async function read<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `Request failed (${response.status}). Please try again.`;
    try {
      const body = await response.json();
      message =
        [body.message, ...(body.details ?? [])].filter(Boolean).join(" ") ||
        message;
    } catch {
      /* Keep the default message for proxy errors. */
    }
    throw new ApiError(response.status, message);
  }
  return response.json() as Promise<T>;
}
async function request<T>(
  path: string,
  method = "GET",
  body?: unknown,
  form = false,
): Promise<T> {
  const headers: Record<string, string> = {};
  if (method !== "GET") {
    const csrf = await read<{ headerName: string; token: string }>(
      await fetch(`${BASE}/api/auth/csrf`, {
        credentials: "include",
        cache: "no-store",
      }),
    );
    headers[csrf.headerName] = csrf.token;
  }
  if (body !== undefined)
    headers["Content-Type"] = form
      ? "application/x-www-form-urlencoded"
      : "application/json";
  return read<T>(
    await fetch(`${BASE}${path}`, {
      method,
      credentials: "include",
      cache: "no-store",
      headers,
      body:
        body === undefined
          ? undefined
          : form
            ? String(body)
            : JSON.stringify(body),
    }),
  );
}
export const api = {
  me: () => request<User>("/api/auth/me"),
  register: (input: Registration) =>
    request<User>("/api/auth/register", "POST", {
      ...input,
      contactDetails: input.email.trim(),
    }),
  login: (email: string, password: string) =>
    request<User>(
      "/api/auth/login",
      "POST",
      new URLSearchParams({ email, password }),
      true,
    ),
  logout: () => request("/api/auth/logout", "POST"),
  listings: (query: string, region: string) =>
    request<Listing[]>(
      `/api/listings?${new URLSearchParams({ query, region })}`,
    ),
  detail: (id: number) => request<Listing>(`/api/listings/${id}`),
  myListings: () => request<Listing[]>("/api/me/listings"),
  myClaims: () => request<Claim[]>("/api/me/claims"),
  create: (input: ListingInput) =>
    request<Listing>("/api/listings", "POST", input),
  update: (id: number, input: ListingInput) =>
    request<Listing>(`/api/listings/${id}`, "PUT", input),
  withdraw: (id: number) =>
    request<Listing>(`/api/listings/${id}/withdraw`, "POST"),
  reserve: (id: number) => request<Claim>(`/api/listings/${id}/claims`, "POST"),
  cancel: (id: number) => request<Claim>(`/api/claims/${id}/cancel`, "POST"),
  complete: (id: number) =>
    request<Listing>(`/api/listings/${id}/complete`, "POST"),
};
