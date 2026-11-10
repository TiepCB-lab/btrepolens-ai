import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, NavLink, useParams, useSearchParams } from "react-router";
import {
  ArrowLeft,
  ArrowRight,
  CircleDot,
  ExternalLink,
  GitPullRequest,
  RefreshCw,
} from "lucide-react";
import {
  repositoriesApi,
  type State,
  type WorkItem,
  type WorkKind,
} from "./api";
import { ErrorState, Loading } from "../../shared/components/States";
import { formatDate, githubUrl } from "../../shared/format";

function useRepository(id: string) {
  const repos = useQuery({
    queryKey: ["repositories"],
    queryFn: repositoriesApi.list,
  });
  return { ...repos, repo: repos.data?.find((repo) => String(repo.id) === id) };
}

export function RepositoryLayout({ children }: { children: React.ReactNode }) {
  const { id = "" } = useParams();
  const { repo, isPending, error, refetch } = useRepository(id);
  const client = useQueryClient();
  const activate = useMutation({
    mutationFn: () => repositoriesApi.activate(Number(id)),
    onSuccess: () => client.invalidateQueries({ queryKey: ["repositories"] }),
  });
  if (!/^[1-9]\d*$/.test(id))
    return <ErrorState error={new Error("Invalid repository ID.")} />;
  if (isPending) return <Loading label="Loading repository" />;
  if (error) return <ErrorState error={error} retry={() => void refetch()} />;
  if (!repo)
    return (
      <div className="empty">
        <h2>Repository not found</h2>
        <p>This repository is not registered in your workspace.</p>
        <Link to="/repositories" className="button">
          View repositories
        </Link>
      </div>
    );
  return (
    <>
      <div className="page-heading repo-heading">
        <div>
          <p className="breadcrumb">
            <Link to="/repositories">Repositories</Link>
            <span>/</span>
            {repo.owner}
          </p>
          <h1>
            {repo.name}{" "}
            {repo.active && (
              <span className="active-badge">
                <span /> Active
              </span>
            )}
          </h1>
          <p className="subtitle">{repo.fullName}</p>
        </div>
        <div className="heading-actions">
          {!repo.active && (
            <button
              className="button"
              disabled={activate.isPending}
              onClick={() => activate.mutate()}
            >
              {activate.isPending ? "Selecting…" : "Set as active"}
            </button>
          )}
          {githubUrl(repo.githubUrl) && (
            <a
              className="button"
              href={githubUrl(repo.githubUrl)}
              target="_blank"
              rel="noreferrer"
            >
              GitHub <ExternalLink size={14} />
            </a>
          )}
        </div>
      </div>
      {activate.error && <ErrorState error={activate.error} />}
      <nav className="tabs" aria-label="Repository navigation">
        <NavLink end to={`/repositories/${id}`}>
          Overview
        </NavLink>
        <NavLink to={`/repositories/${id}/issues`}>Issues</NavLink>
        <NavLink to={`/repositories/${id}/pull-requests`}>
          Pull requests
        </NavLink>
        <NavLink to={`/repositories/${id}/assistant`}>Assistant</NavLink>
      </nav>
      {children}
    </>
  );
}

function Rows({
  items,
  id,
  kind,
}: {
  items: WorkItem[];
  id: string;
  kind: WorkKind;
}) {
  if (!items.length)
    return (
      <p className="list-empty">
        No {kind === "issues" ? "issues" : "pull requests"} in this view.
      </p>
    );
  return (
    <div className="work-list">
      {items.map((item) => (
        <Link
          className="work-row"
          to={`/repositories/${id}/${kind}/${item.number}`}
          key={item.number}
        >
          <span
            className={`work-icon ${item.state === "open" ? "is-open" : ""}`}
          >
            {kind === "issues" ? (
              <CircleDot size={17} />
            ) : (
              <GitPullRequest size={17} />
            )}
          </span>
          <span className="work-main">
            <strong>{item.title}</strong>
            <span>
              #{item.number} · Updated {formatDate(item.updatedAt)}
            </span>
          </span>
          <ArrowRight size={16} className="row-arrow" />
        </Link>
      ))}
    </div>
  );
}

export function OverviewPage() {
  const { id = "" } = useParams();
  const query = useQuery({
    queryKey: ["overview", id],
    queryFn: () => repositoriesApi.overview(id),
    enabled: /^[1-9]\d*$/.test(id),
  });
  if (query.isPending) return <Loading label="Loading GitHub overview" />;
  if (query.error)
    return (
      <ErrorState error={query.error} retry={() => void query.refetch()} />
    );
  const {
    repository,
    openIssues,
    openPullRequests,
    recentIssues,
    recentPullRequests,
  } = query.data;
  return (
    <div className="overview">
      <section className="repo-intro">
        <div>
          <span className="section-label">Repository overview</span>
          <h2>
            {repository.owner} / {repository.name}
          </h2>
          <p>
            {repository.description || "No description provided on GitHub."}
          </p>
        </div>
        <button
          className="button subtle refresh"
          onClick={() => void query.refetch()}
          disabled={query.isFetching}
        >
          <RefreshCw size={15} /> Refresh
        </button>
      </section>
      <div className="repo-facts">
        <div>
          <span>Visibility</span>
          <strong className="capitalize">{repository.visibility}</strong>
        </div>
        <div>
          <span>Default branch</span>
          <strong>{repository.defaultBranch}</strong>
        </div>
        <div>
          <span>Updated on GitHub</span>
          <strong>{formatDate(repository.updatedAt)}</strong>
        </div>
      </div>
      <section className="work-summary">
        <div className="summary-copy">
          <h2>Open work</h2>
          <p>
            Counts reported by GitHub Search. Results may take time to reflect
            recent changes.
          </p>
        </div>
        <Link to={`/repositories/${id}/issues`}>
          <span>Open issues</span>
          <strong>{openIssues.total.toLocaleString()}</strong>
          {openIssues.incomplete && <small>Results incomplete</small>}
          <ArrowRight size={16} />
        </Link>
        <Link to={`/repositories/${id}/pull-requests`}>
          <span>Open pull requests</span>
          <strong>{openPullRequests.total.toLocaleString()}</strong>
          {openPullRequests.incomplete && <small>Results incomplete</small>}
          <ArrowRight size={16} />
        </Link>
      </section>
      <div className="recent-grid">
        <section className="recent-section">
          <div className="section-heading">
            <div>
              <h2>Recent issues</h2>
              <p>Open issues, sorted by update</p>
            </div>
            <Link className="inline-link" to={`/repositories/${id}/issues`}>
              View issues <ArrowRight size={14} />
            </Link>
          </div>
          <Rows items={recentIssues} id={id} kind="issues" />
        </section>
        <section className="recent-section">
          <div className="section-heading">
            <div>
              <h2>Recent pull requests</h2>
              <p>Open pull requests, sorted by update</p>
            </div>
            <Link
              className="inline-link"
              to={`/repositories/${id}/pull-requests`}
            >
              View pull requests <ArrowRight size={14} />
            </Link>
          </div>
          <Rows items={recentPullRequests} id={id} kind="pull-requests" />
        </section>
      </div>
    </div>
  );
}

export function WorkListPage({ kind }: { kind: WorkKind }) {
  const { id = "" } = useParams();
  const [params, setParams] = useSearchParams();
  const raw = params.get("state");
  const state: State = raw === "closed" || raw === "all" ? raw : "open";
  const query = useQuery({
    queryKey: ["work", id, kind, state],
    queryFn: () => repositoriesApi.work(id, kind, state),
    enabled: /^[1-9]\d*$/.test(id),
  });
  const title = kind === "issues" ? "Issues" : "Pull requests";
  return (
    <section className="list-page">
      <div className="section-heading">
        <div>
          <h2>{title}</h2>
          <p>Showing up to 20 items, sorted by most recently updated.</p>
        </div>
        <button
          className="button subtle"
          onClick={() => void query.refetch()}
          disabled={query.isFetching}
        >
          <RefreshCw size={15} /> Refresh
        </button>
      </div>
      <div className="filter-tabs" aria-label="Filter by state">
        {(["open", "closed", "all"] as const).map((value) => (
          <button
            key={value}
            className={state === value ? "selected" : ""}
            aria-pressed={state === value}
            onClick={() => setParams(value === "open" ? {} : { state: value })}
          >
            {value}
          </button>
        ))}
      </div>
      {query.isPending ? (
        <Loading label={`Loading ${title.toLowerCase()}`} />
      ) : query.error ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : (
        <>
          <p className="list-caption">
            {query.data.length} shown{" "}
            {kind === "issues" &&
              "· Issue-only results, scan limited to 10 GitHub pages"}
          </p>
          <Rows items={query.data} id={id} kind={kind} />
        </>
      )}
    </section>
  );
}

export function WorkDetailPage({ kind }: { kind: WorkKind }) {
  const { id = "", number = "" } = useParams();
  const query = useQuery({
    queryKey: ["detail", id, kind, number],
    queryFn: () => repositoriesApi.detail(id, kind, number),
    enabled: /^[1-9]\d*$/.test(id) && /^[1-9]\d*$/.test(number),
  });
  if (!/^[1-9]\d*$/.test(number))
    return <ErrorState error={new Error("Invalid item number.")} />;
  if (query.isPending) return <Loading label="Loading details" />;
  if (query.error)
    return (
      <ErrorState error={query.error} retry={() => void query.refetch()} />
    );
  const item = query.data;
  const url = githubUrl(item.url);
  return (
    <article className="detail">
      <Link className="inline-link back" to={`/repositories/${id}/${kind}`}>
        <ArrowLeft size={15} /> All{" "}
        {kind === "issues" ? "issues" : "pull requests"}
      </Link>
      <div className="detail-title">
        <div>
          <h2>{item.title}</h2>
          <p>
            #{item.number} ·{" "}
            <span className={item.state === "open" ? "open-text" : ""}>
              {item.state}
            </span>{" "}
            · Updated {formatDate(item.updatedAt)}
          </p>
        </div>
        {url && (
          <a href={url} target="_blank" rel="noreferrer" className="button">
            Open on GitHub <ExternalLink size={14} />
          </a>
        )}
      </div>
      {item.labels && item.labels.length > 0 && (
        <div className="labels">
          {item.labels.map((label) => (
            <span key={label}>{label}</span>
          ))}
        </div>
      )}
      <div className="detail-body">
        {item.body || "No description provided."}
      </div>
    </article>
  );
}
