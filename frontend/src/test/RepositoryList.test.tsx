import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import { afterEach, expect, it, vi } from "vitest";
import { RepositoryList } from "../features/repositories/RepositoryList";

afterEach(() => vi.unstubAllGlobals());
it("registers a repository and refreshes the list", async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true, data: [] })),
    )
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true, data: { id: 1 } }), {
        status: 201,
      }),
    )
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: true,
          data: [
            {
              id: 1,
              owner: "octocat",
              name: "Hello-World",
              fullName: "octocat/Hello-World",
              active: false,
              updatedAt: "2026-01-01T00:00:00Z",
            },
          ],
        }),
      ),
    );
  vi.stubGlobal("fetch", fetchMock);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <RepositoryList />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Add your first repository" }),
  );
  fireEvent.change(screen.getByLabelText("Owner"), {
    target: { value: "octocat" },
  });
  fireEvent.change(screen.getByLabelText("Repository name"), {
    target: { value: "Hello-World" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Connect repository" }));
  expect(
    await screen.findByRole("link", { name: /Hello-World/ }),
  ).toHaveAttribute("href", "/repositories/1");
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
    owner: "octocat",
    name: "Hello-World",
  });
});
