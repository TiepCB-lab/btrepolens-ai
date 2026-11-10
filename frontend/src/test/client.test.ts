import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, request } from "../shared/api/client";
import { githubUrl } from "../shared/format";

describe("API client", () => {
  afterEach(() => vi.unstubAllGlobals());
  it("unwraps a successful response", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ success: true, data: [1], message: "ok" }),
            { status: 200 },
          ),
        ),
    );
    await expect(request("/repositories")).resolves.toEqual([1]);
    expect(fetch).toHaveBeenCalledWith(
      "/api/v1/repositories",
      expect.any(Object),
    );
  });
  it("surfaces application errors without turning them into empty data", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({
              success: false,
              message: "Repository already registered",
              errorCode: "REPOSITORY_DUPLICATE",
            }),
            { status: 409 },
          ),
        ),
    );
    await expect(request("/repositories")).rejects.toMatchObject({
      message: "Repository already registered",
      code: "REPOSITORY_DUPLICATE",
      status: 409,
    });
  });
  it("explains a network failure", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network")));
    await expect(request("/repositories")).rejects.toBeInstanceOf(ApiError);
    await expect(request("/repositories")).rejects.toMatchObject({
      code: "NETWORK_ERROR",
    });
  });
});

it("allows only HTTPS GitHub links", () => {
  expect(githubUrl("https://github.com/owner/repo")).toBe(
    "https://github.com/owner/repo",
  );
  expect(
    githubUrl("https://github.com.evil.example/owner/repo"),
  ).toBeUndefined();
  expect(githubUrl("javascript:alert(1)")).toBeUndefined();
});
