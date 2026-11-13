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
  fireEvent.change(screen.getByLabelText("GitHub repository URL"), {
    target: { value: "https://github.com/octocat/Hello-World.git" },
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

it("rejects non-repository links without sending a request", async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: [] })),
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
  const input = screen.getByLabelText("GitHub repository URL");
  for (const value of [
    "https://github.com.evil.com/owner/repo",
    "https://github.com/owner/repo/issues/1",
    "https://github.com/owner",
  ]) {
    fireEvent.change(input, { target: { value } });
    fireEvent.click(screen.getByRole("button", { name: "Connect repository" }));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Enter a GitHub repository URL",
    );
  }
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("explains private repository access when GitHub returns 404", async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true, data: [] })),
    )
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: false,
          errorCode: "GITHUB_NOT_FOUND",
          message: "Not found",
        }),
        { status: 404 },
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
  fireEvent.change(screen.getByLabelText("GitHub repository URL"), {
    target: { value: "owner/private-repo" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Connect repository" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "private repositories require a GitHub token",
  );
  expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
    owner: "owner",
    name: "private-repo",
  });
});
