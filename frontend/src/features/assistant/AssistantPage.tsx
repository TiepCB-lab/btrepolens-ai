import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "react-router";
import { ArrowUpRight, MessageSquarePlus, Send } from "lucide-react";
import { request } from "../../shared/api/client";
import { ErrorState, Loading } from "../../shared/components/States";
import { githubUrl } from "../../shared/format";
import { repositoriesApi } from "../repositories/api";

type Session = {
  id: number;
  repositoryId: number;
  title: string;
  busy: boolean;
};
type Message = {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  createdAt: string;
};
type Proposal = {
  id: number;
  title: string;
  body: string;
  labels: string[];
  state: string;
  githubUrl: string | null;
};
type Log = {
  id: number;
  toolName: string;
  success: boolean;
  createdAt: string;
};

export function AssistantPage() {
  const { id = "" } = useParams();
  const client = useQueryClient();
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [text, setText] = useState("");
  const [actionError, setActionError] = useState<Error | null>(null);
  const repositories = useQuery({
    queryKey: ["repositories"],
    queryFn: repositoriesApi.list,
  });
  const active =
    repositories.data?.find((repo) => String(repo.id) === id)?.active ?? false;
  const sessions = useQuery({
    queryKey: ["chat-sessions"],
    queryFn: () => request<Session[]>("/chat/sessions"),
  });
  const messages = useQuery({
    queryKey: ["chat-messages", sessionId],
    queryFn: () => request<Message[]>(`/chat/sessions/${sessionId}/messages`),
    enabled: sessionId !== null,
  });
  const proposals = useQuery({
    queryKey: ["chat-proposals", sessionId],
    queryFn: () => request<Proposal[]>(`/chat/sessions/${sessionId}/proposals`),
    enabled: sessionId !== null,
  });
  const logs = useQuery({
    queryKey: ["chat-logs", sessionId],
    queryFn: () => request<Log[]>(`/chat/sessions/${sessionId}/tool-logs`),
    enabled: sessionId !== null,
  });
  const refreshSession = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ["chat-messages", sessionId] }),
      client.invalidateQueries({ queryKey: ["chat-proposals", sessionId] }),
      client.invalidateQueries({ queryKey: ["chat-logs", sessionId] }),
    ]);
  };
  const create = useMutation({
    mutationFn: () =>
      request<Session>("/chat/sessions", {
        method: "POST",
        body: JSON.stringify({
          repositoryId: Number(id),
          title: "Repository conversation",
        }),
      }),
    onSuccess: async (session) => {
      await client.invalidateQueries({ queryKey: ["chat-sessions"] });
      setSessionId(session.id);
    },
  });
  const send = useMutation({
    mutationFn: (content: string) =>
      request<Message>(`/chat/sessions/${sessionId}/messages`, {
        method: "POST",
        body: JSON.stringify({ content }),
      }),
    onSuccess: () => setText(""),
    onSettled: refreshSession,
  });
  const decide = useMutation({
    mutationFn: ({
      proposalId,
      action,
    }: {
      proposalId: number;
      action: "approve" | "reject";
    }) =>
      request<Proposal>(`/proposals/${proposalId}/${action}`, {
        method: "POST",
        body: JSON.stringify(
          action === "approve" ? { confirmation: "APPROVE" } : {},
        ),
      }),
    onMutate: () => setActionError(null),
    onError: (error) => setActionError(error),
    onSettled: refreshSession,
  });
  const visibleSessions =
    sessions.data?.filter((session) => String(session.repositoryId) === id) ??
    [];
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (text.trim()) send.mutate(text.trim());
  }
  function decideProposal(proposal: Proposal, action: "approve" | "reject") {
    if (
      action === "approve" &&
      !window.confirm(
        `Create this GitHub issue in the active repository?\n\n${proposal.title}\n\nReview the full proposal before approving.`,
      )
    )
      return;
    decide.mutate({ proposalId: proposal.id, action });
  }
  return (
    <div className="assistant-page">
      <div className="section-heading">
        <div>
          <h2>Repository assistant</h2>
          <p>
            Ask about this repository. GitHub content is treated as untrusted
            data.
          </p>
        </div>
        <button
          className="button"
          disabled={!active || create.isPending}
          onClick={() => create.mutate()}
        >
          <MessageSquarePlus size={15} /> New conversation
        </button>
      </div>
      {!active && (
        <div className="assistant-notice">
          Set this repository as active before starting or continuing a
          conversation.
        </div>
      )}
      {sessions.error && (
        <ErrorState
          error={sessions.error}
          retry={() => void sessions.refetch()}
        />
      )}
      {create.error && <ErrorState error={create.error} />}
      <div className="assistant-layout">
        <section className="conversation-panel">
          <label className="session-select">
            Conversation
            <select
              value={sessionId ?? ""}
              onChange={(event) =>
                setSessionId(
                  event.target.value ? Number(event.target.value) : null,
                )
              }
            >
              <option value="">Select a conversation</option>
              {visibleSessions.map((session) => (
                <option value={session.id} key={session.id}>
                  {session.title} · #{session.id}
                </option>
              ))}
            </select>
          </label>
          {!sessionId ? (
            <div className="assistant-empty">
              <h3>Start with a question</h3>
              <p>
                Ask for a summary of open work, a development plan, or an issue
                proposal.
              </p>
              <p>
                Issue proposals are saved for your review and are never
                published automatically.
              </p>
            </div>
          ) : (
            <>
              <div className="messages" aria-live="polite">
                {messages.isPending ? (
                  <Loading label="Loading conversation" />
                ) : messages.error ? (
                  <ErrorState
                    error={messages.error}
                    retry={() => void messages.refetch()}
                  />
                ) : messages.data.length ? (
                  messages.data.map((message) => (
                    <article
                      className={`message ${message.role === "USER" ? "user-message" : ""}`}
                      key={message.id}
                    >
                      <strong>
                        {message.role === "USER" ? "You" : "BTRepoLens"}
                      </strong>
                      <p>{message.content}</p>
                    </article>
                  ))
                ) : (
                  <p className="list-empty">
                    No messages yet. Ask a question about this repository.
                  </p>
                )}
              </div>
              {send.error && <ErrorState error={send.error} />}
              <form className="message-form" onSubmit={submit}>
                <label htmlFor="chat-message">Your message</label>
                <textarea
                  id="chat-message"
                  value={text}
                  onChange={(event) => setText(event.target.value)}
                  placeholder="What should I work on next?"
                  maxLength={10000}
                  required
                  disabled={!active || send.isPending}
                />
                <button
                  className="button primary"
                  disabled={!active || send.isPending || !text.trim()}
                >
                  <Send size={15} />{" "}
                  {send.isPending ? "Thinking…" : "Send message"}
                </button>
              </form>
            </>
          )}
        </section>
        <aside className="assistant-side">
          <section>
            <h3>Issue proposals</h3>
            <p className="assistant-help">
              Review the full proposal before approving a GitHub write.
            </p>
            {actionError && <ErrorState error={actionError} />}
            {sessionId && proposals.isPending ? (
              <Loading label="Loading proposals" />
            ) : proposals.error ? (
              <ErrorState
                error={proposals.error}
                retry={() => void proposals.refetch()}
              />
            ) : !proposals.data?.length ? (
              <p className="assistant-help">
                No proposals in this conversation.
              </p>
            ) : (
              proposals.data.map((proposal) => (
                <article className="proposal" key={proposal.id}>
                  <div className="proposal-head">
                    <strong>{proposal.title}</strong>
                    <span>{proposal.state}</span>
                  </div>
                  <p className="proposal-body">{proposal.body}</p>
                  <p className="assistant-help">
                    Labels:{" "}
                    {proposal.labels.length
                      ? proposal.labels.join(", ")
                      : "None"}
                  </p>
                  {proposal.githubUrl && githubUrl(proposal.githubUrl) && (
                    <a
                      className="inline-link"
                      href={githubUrl(proposal.githubUrl)}
                      target="_blank"
                      rel="noreferrer"
                    >
                      View on GitHub <ArrowUpRight size={14} />
                    </a>
                  )}
                  {(proposal.state === "UNKNOWN" ||
                    proposal.state === "EXECUTING") && (
                    <p className="uncertain">
                      Creation could not be confirmed. Check GitHub before
                      taking further action; this proposal cannot be retried.
                    </p>
                  )}
                  {proposal.state === "PENDING" && (
                    <div className="proposal-actions">
                      <button
                        className="button primary"
                        disabled={!active || decide.isPending}
                        onClick={() => decideProposal(proposal, "approve")}
                      >
                        Approve and create
                      </button>
                      <button
                        className="button"
                        disabled={decide.isPending}
                        onClick={() => decideProposal(proposal, "reject")}
                      >
                        Reject
                      </button>
                    </div>
                  )}
                </article>
              ))
            )}
          </section>
          <section className="tool-log">
            <h3>Tool activity</h3>
            <p className="assistant-help">
              Names and outcomes; no GitHub content is logged here.
            </p>
            {sessionId && logs.isPending ? (
              <Loading label="Loading tool activity" />
            ) : logs.error ? (
              <ErrorState
                error={logs.error}
                retry={() => void logs.refetch()}
              />
            ) : !logs.data?.length ? (
              <p className="assistant-help">No tools used yet.</p>
            ) : (
              <ul>
                {logs.data.map((log) => (
                  <li key={log.id}>
                    <span>{log.toolName}</span>
                    <span>{log.success ? "Succeeded" : "Failed"}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>
    </div>
  );
}
