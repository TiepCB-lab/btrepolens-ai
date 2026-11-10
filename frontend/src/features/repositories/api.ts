import { request } from "../../shared/api/client";

export interface TrackedRepository {
  id: number;
  owner: string;
  name: string;
  fullName: string;
  githubUrl: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface RepositoryMetadata {
  name: string;
  owner: string;
  description: string | null;
  visibility: string;
  defaultBranch: string;
  url: string;
  updatedAt: string;
}
export interface WorkItem {
  number: number;
  title: string;
  state: string;
  url: string;
  updatedAt: string;
  body?: string | null;
  createdAt?: string;
  labels?: string[];
}
export interface Overview {
  repository: RepositoryMetadata;
  openIssues: { total: number; incomplete: boolean };
  openPullRequests: { total: number; incomplete: boolean };
  recentIssues: WorkItem[];
  recentPullRequests: WorkItem[];
}
export type WorkKind = "issues" | "pull-requests";
export type State = "open" | "closed" | "all";

export const repositoriesApi = {
  list: () => request<TrackedRepository[]>("/repositories"),
  register: (owner: string, name: string) =>
    request<TrackedRepository>("/repositories", {
      method: "POST",
      body: JSON.stringify({ owner, name }),
    }),
  activate: (id: number) =>
    request<TrackedRepository>(`/repositories/${id}/activate`, {
      method: "PUT",
    }),
  overview: (id: string) => request<Overview>(`/repositories/${id}/overview`),
  work: (id: string, kind: WorkKind, state: State) =>
    request<WorkItem[]>(`/repositories/${id}/${kind}?state=${state}&limit=20`),
  detail: (id: string, kind: WorkKind, number: string) =>
    request<WorkItem>(`/repositories/${id}/${kind}/${number}`),
};
