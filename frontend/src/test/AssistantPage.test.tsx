import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, expect, it, vi } from "vitest";
import { AssistantPage } from "../features/assistant/AssistantPage";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it("requires a confirmation before sending an approval", async () => {
  let approved = false;
  const api = vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/approve")) approved = true;
    const data = url.endsWith("/repositories")
      ? [{ id: 1, active: true }]
      : url.endsWith("/chat/sessions")
        ? [{ id: 10, repositoryId: 1, title: "Test", busy: false }]
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
  const confirm = vi
    .spyOn(window, "confirm")
    .mockReturnValueOnce(false)
    .mockReturnValueOnce(true);
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
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(api.mock.calls.some(([url]) => url.endsWith("/approve"))).toBe(false);
  fireEvent.click(
    within(section).getByRole("button", { name: "Approve and create" }),
  );
  await waitFor(() =>
    expect(api.mock.calls.some(([url]) => url.endsWith("/approve"))).toBe(true),
  );
  expect(await screen.findByText("SUCCEEDED")).toBeInTheDocument();
});
