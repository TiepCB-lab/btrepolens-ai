import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import {
  QueryClient,
  QueryClientProvider,
  focusManager,
} from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, expect, it, vi } from "vitest";
import { AssistantPage } from "../features/assistant/AssistantPage";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  focusManager.setFocused(undefined);
});

it("refreshes a short turn and an executing proposal even when busy is never observed", async () => {
  let completed = false;
  const api = vi.fn(async (url: string) => {
    const data = url.endsWith("/repositories")
      ? [{ id: 1, active: true }]
      : url.endsWith("/chat/sessions")
        ? [{ id: 10, repositoryId: 1, title: "Test", busy: false }]
        : url.endsWith("/chat/sessions/10")
          ? { id: 10, repositoryId: 1, title: "Test", busy: false }
          : url.endsWith("/messages")
            ? completed
              ? [
                  {
                    id: 1,
                    role: "ASSISTANT",
                    content: "New answer",
                    createdAt: "2026-01-01T00:00:00Z",
                  },
                ]
              : []
            : url.endsWith("/proposals")
              ? [
                  {
                    id: 7,
                    title: "Draft",
                    body: "Criteria",
                    labels: [],
                    state: completed ? "SUCCEEDED" : "EXECUTING",
                    githubUrl: null,
                  },
                ]
              : [];
    return new Response(JSON.stringify({ success: true, data }));
  });
  focusManager.setFocused(true);
  vi.stubGlobal("fetch", api);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/repositories/1/assistant"]}>
        <Routes>
          <Route
            path="/repositories/:id/assistant"
            element={<AssistantPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByRole("option", { name: /Test/ });
  fireEvent.change(screen.getByLabelText("Conversation"), {
    target: { value: "10" },
  });
  expect(await screen.findByText("EXECUTING")).toBeInTheDocument();
  expect(screen.getByText(/Creation is in progress/)).toBeInTheDocument();
  completed = true;
  expect(
    await screen.findByText("New answer", {}, { timeout: 8000 }),
  ).toBeInTheDocument();
  expect(
    await screen.findByText("SUCCEEDED", {}, { timeout: 8000 }),
  ).toBeInTheDocument();
  expect(api.mock.calls.some(([url]) => url.endsWith("/approve"))).toBe(false);
}, 12000);

it("warns that a message may have been saved when the response is lost", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options?: RequestInit) => {
      if (options?.method === "POST") throw new TypeError("connection lost");
      const data = url.endsWith("/repositories")
        ? [{ id: 1, active: true }]
        : url.endsWith("/chat/sessions")
          ? [{ id: 10, repositoryId: 1, title: "Test", busy: false }]
          : url.endsWith("/chat/sessions/10")
            ? { id: 10, repositoryId: 1, title: "Test", busy: false }
            : [];
      return new Response(JSON.stringify({ success: true, data }));
    }),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/repositories/1/assistant"]}>
        <Routes>
          <Route
            path="/repositories/:id/assistant"
            element={<AssistantPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByRole("option", { name: /Test/ });
  fireEvent.change(screen.getByLabelText("Conversation"), {
    target: { value: "10" },
  });
  await waitFor(() =>
    expect(screen.getByLabelText("Your message")).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Your message"), {
    target: { value: "Summarize" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Send message" }));
  expect(
    await screen.findByText(/Your message may already be saved/),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("Your message")).toHaveValue("Summarize");
});

it("keeps the draft and shows recovery guidance when a turn is interrupted", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options?: RequestInit) => {
      if (options?.method === "POST")
        return new Response(
          JSON.stringify({
            success: false,
            errorCode: "SESSION_SUPERSEDED",
            message:
              "This response was interrupted. Your message may already be saved; check the conversation before sending it again.",
          }),
          { status: 409 },
        );
      const data = url.endsWith("/repositories")
        ? [{ id: 1, active: true }]
        : url.endsWith("/chat/sessions")
          ? [{ id: 10, repositoryId: 1, title: "Test", busy: false }]
          : url.endsWith("/chat/sessions/10")
            ? { id: 10, repositoryId: 1, title: "Test", busy: false }
            : [];
      return new Response(JSON.stringify({ success: true, data }));
    }),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/repositories/1/assistant"]}>
        <Routes>
          <Route
            path="/repositories/:id/assistant"
            element={<AssistantPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByRole("option", { name: /Test/ });
  fireEvent.change(screen.getByLabelText("Conversation"), {
    target: { value: "10" },
  });
  await waitFor(() =>
    expect(screen.getByLabelText("Your message")).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Your message"), {
    target: { value: "Summarize" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Send message" }));
  expect(
    await screen.findByText(/check the conversation before sending it again/i),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("Your message")).toHaveValue("Summarize");
});

it("disables sending while a turn is active in another window and refreshes afterward", async () => {
  const api = vi.fn(async (url: string) => {
    const data = url.endsWith("/repositories")
      ? [{ id: 1, active: true, fullName: "octocat/Hello-World" }]
      : url.endsWith("/chat/sessions")
        ? [{ id: 10, repositoryId: 1, title: "Test", busy: true }]
        : url.endsWith("/chat/sessions/10")
          ? { id: 10, repositoryId: 1, title: "Test", busy: true }
          : [];
    return new Response(JSON.stringify({ success: true, data }));
  });
  vi.stubGlobal("fetch", api);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/repositories/1/assistant"]}>
        <Routes>
          <Route
            path="/repositories/:id/assistant"
            element={<AssistantPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByRole("option", { name: /Test/ });
  fireEvent.change(screen.getByLabelText("Conversation"), {
    target: { value: "10" },
  });
  expect(
    await screen.findByText(/response is in progress in another window/i),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("Your message")).toBeDisabled();
  const before = api.mock.calls.filter(([url]) =>
    url.endsWith("/messages"),
  ).length;
  await act(async () => {
    client.setQueryData(["chat-session", 10], {
      id: 10,
      repositoryId: 1,
      title: "Test",
      busy: false,
    });
  });
  await waitFor(() =>
    expect(screen.getByLabelText("Your message")).toBeEnabled(),
  );
  await waitFor(() =>
    expect(
      api.mock.calls.filter(([url]) => url.endsWith("/messages")).length,
    ).toBeGreaterThan(before),
  );
});

it("keeps drafts separate when switching conversations", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const data = url.endsWith("/repositories")
        ? [{ id: 1, active: true }]
        : url.endsWith("/chat/sessions")
          ? [
              { id: 10, repositoryId: 1, title: "First", busy: false },
              { id: 11, repositoryId: 1, title: "Second", busy: false },
            ]
          : /\/chat\/sessions\/\d+$/.test(url)
            ? { id: 10, repositoryId: 1, title: "Test", busy: false }
            : [];
      return new Response(JSON.stringify({ success: true, data }));
    }),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/repositories/1/assistant"]}>
        <Routes>
          <Route
            path="/repositories/:id/assistant"
            element={<AssistantPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByRole("option", { name: /First/ });
  const selector = screen.getByLabelText("Conversation");
  fireEvent.change(selector, { target: { value: "10" } });
  await waitFor(() =>
    expect(screen.getByLabelText("Your message")).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Your message"), {
    target: { value: "First draft" },
  });
  fireEvent.change(selector, { target: { value: "11" } });
  await waitFor(() =>
    expect(screen.getByLabelText("Your message")).toBeEnabled(),
  );
  expect(screen.getByLabelText("Your message")).toHaveValue("");
  fireEvent.change(selector, { target: { value: "10" } });
  expect(screen.getByLabelText("Your message")).toHaveValue("First draft");
});

it("prevents switching conversations while a message is being sent", async () => {
  let finishRequest!: (response: Response) => void;
  const pending = new Promise<Response>((resolve) => {
    finishRequest = resolve;
  });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options?: RequestInit) => {
      if (options?.method === "POST") return pending;
      const data = url.endsWith("/repositories")
        ? [{ id: 1, active: true }]
        : url.endsWith("/chat/sessions")
          ? [
              { id: 10, repositoryId: 1, title: "First", busy: false },
              { id: 11, repositoryId: 1, title: "Second", busy: false },
            ]
          : url.endsWith("/chat/sessions/10")
            ? { id: 10, repositoryId: 1, title: "First", busy: false }
            : [];
      return new Response(JSON.stringify({ success: true, data }));
    }),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/repositories/1/assistant"]}>
        <Routes>
          <Route
            path="/repositories/:id/assistant"
            element={<AssistantPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByRole("option", { name: /First/ });
  const selector = screen.getByLabelText("Conversation");
  fireEvent.change(selector, { target: { value: "10" } });
  await waitFor(() =>
    expect(screen.getByLabelText("Your message")).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText("Your message"), {
    target: { value: "Please summarize" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Send message" }));
  await waitFor(() => expect(selector).toBeDisabled());
  expect(screen.getByRole("button", { name: /Thinking/ })).toBeDisabled();
  await act(async () =>
    finishRequest(
      new Response(JSON.stringify({ success: true, data: { id: 1 } })),
    ),
  );
  await waitFor(() => expect(selector).toBeEnabled());
  expect(screen.getByLabelText("Your message")).toHaveValue("");
});

it("requires a confirmation before sending an approval", async () => {
  let approved = false;
  const api = vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/approve")) approved = true;
    const data = url.endsWith("/repositories")
      ? [{ id: 1, active: true, fullName: "octocat/Hello-World" }]
      : url.endsWith("/chat/sessions")
        ? [{ id: 10, repositoryId: 1, title: "Test", busy: false }]
        : url.endsWith("/chat/sessions/10")
          ? { id: 10, repositoryId: 1, title: "Test", busy: false }
          : url.endsWith("/proposals")
            ? [
                {
                  id: 7,
                  title: "Draft",
                  body: "Criteria",
                  labels: [],
                  state: approved ? "SUCCEEDED" : "PENDING",
                  githubUrl: null,
                },
              ]
            : url.endsWith("/approve")
              ? { id: 7, state: "SUCCEEDED" }
              : [];
    if (options?.method === "POST")
      expect(JSON.parse(String(options.body))).toEqual({
        confirmation: "APPROVE",
      });
    return new Response(JSON.stringify({ success: true, data }));
  });
  vi.stubGlobal("fetch", api);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/repositories/1/assistant"]}>
        <Routes>
          <Route
            path="/repositories/:id/assistant"
            element={<AssistantPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByRole("option", { name: /Test/ });
  fireEvent.change(screen.getByLabelText("Conversation"), {
    target: { value: "10" },
  });
  const proposal = await screen.findByText("Draft");
  const section = proposal.closest("article")!;
  fireEvent.click(
    within(section).getByRole("button", { name: "Approve and create" }),
  );
  expect(
    within(section).getByRole("group", { name: "Confirm creation of Draft" }),
  ).toBeInTheDocument();
  expect(within(section).getByText("octocat/Hello-World")).toBeInTheDocument();
  expect(
    within(section).getByRole("button", { name: "Confirm creation" }),
  ).toHaveFocus();
  expect(api.mock.calls.some(([url]) => url.endsWith("/approve"))).toBe(false);
  fireEvent.click(within(section).getByRole("button", { name: "Cancel" }));
  expect(api.mock.calls.some(([url]) => url.endsWith("/approve"))).toBe(false);
  fireEvent.click(
    within(section).getByRole("button", { name: "Approve and create" }),
  );
  fireEvent.click(
    within(section).getByRole("button", { name: "Confirm creation" }),
  );
  await waitFor(() =>
    expect(api.mock.calls.some(([url]) => url.endsWith("/approve"))).toBe(true),
  );
  expect(await screen.findByText("SUCCEEDED")).toBeInTheDocument();
});
