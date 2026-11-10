import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { ArrowRight, BookOpen, GitBranch, Plus } from "lucide-react";
import { repositoriesApi } from "./api";
import { ErrorState, Loading } from "../../shared/components/States";
import { formatDate } from "../../shared/format";

export function RepositoryList() {
  const client = useQueryClient();
  const list = useQuery({
    queryKey: ["repositories"],
    queryFn: repositoriesApi.list,
  });
  const [owner, setOwner] = useState("");
  const [name, setName] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const add = useMutation({
    mutationFn: () => repositoriesApi.register(owner.trim(), name.trim()),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["repositories"] });
      setOwner("");
      setName("");
      setFormOpen(false);
    },
  });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    add.mutate();
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="breadcrumb">
            Workspace <span>/</span> Repositories
          </p>
          <h1>Repositories</h1>
          <p className="subtitle">Your GitHub projects, in one place.</p>
        </div>
        <button
          className="button primary"
          onClick={() => {
            add.reset();
            setFormOpen(!formOpen);
          }}
        >
          <Plus size={16} /> Add repository
        </button>
      </div>
      {formOpen && (
        <section className="form-panel" aria-labelledby="add-heading">
          <div>
            <h2 id="add-heading">Connect a repository</h2>
            <p>
              Enter the owner and name as they appear on GitHub. Access is
              verified before saving.
            </p>
          </div>
          <form onSubmit={submit} className="register-form">
            <label>
              Owner
              <input
                value={owner}
                onChange={(e) => setOwner(e.target.value)}
                placeholder="e.g. octocat"
                required
                maxLength={100}
                autoFocus
              />
            </label>
            <label>
              Repository name
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Hello-World"
                required
                maxLength={100}
              />
            </label>
            <button className="button primary" disabled={add.isPending}>
              {add.isPending ? "Connecting…" : "Connect repository"}
            </button>
            <button
              type="button"
              className="button subtle"
              onClick={() => setFormOpen(false)}
            >
              Cancel
            </button>
            {add.error && <ErrorState error={add.error} />}
          </form>
        </section>
      )}
      <div className="section-heading">
        <div>
          <h2>Tracked repositories</h2>
          <p>Choose a repository to inspect its current work.</p>
        </div>
        {list.data && (
          <span className="count-label">
            {list.data.length}{" "}
            {list.data.length === 1 ? "repository" : "repositories"}
          </span>
        )}
      </div>
      {list.isPending ? (
        <Loading label="Loading repositories" />
      ) : list.error ? (
        <ErrorState error={list.error} retry={() => void list.refetch()} />
      ) : list.data.length === 0 ? (
        <div className="empty">
          <div className="empty-icon">
            <BookOpen size={22} />
          </div>
          <h3>No repositories connected</h3>
          <p>
            Add a GitHub repository to see its issues and pull requests here.
          </p>
          <button className="button primary" onClick={() => setFormOpen(true)}>
            <Plus size={16} /> Add your first repository
          </button>
        </div>
      ) : (
        <div className="repository-list">
          {list.data.map((repo) => (
            <Link
              className="repository-row"
              key={repo.id}
              to={`/repositories/${repo.id}`}
            >
              <div className="repo-icon">
                <BookOpen size={18} />
              </div>
              <div className="repo-name">
                <strong>{repo.name}</strong>
                <span>
                  {repo.owner} / {repo.name}
                </span>
              </div>
              <div className="repo-meta">
                {repo.active && (
                  <span className="active-indicator">
                    <span /> Active
                  </span>
                )}
                <span className="updated">
                  <GitBranch size={14} /> Tracked since{" "}
                  {formatDate(repo.createdAt)}
                </span>
              </div>
              <ArrowRight className="row-arrow" size={17} />
            </Link>
          ))}
        </div>
      )}
      <div className="footnote">
        GitHub remains the source of truth. Repository data is retrieved when
        you open it.
      </div>
    </>
  );
}
