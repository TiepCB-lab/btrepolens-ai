import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "react-router";
import { ArrowUpRight, MessageSquarePlus, Send } from "lucide-react";
import { ApiError, request } from "../../shared/api/client";
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
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [actionError, setActionError] = useState<Error | null>(null);
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const wasBusy = useRef(false);
  const repositories = useQuery({
    queryKey: ["repositories"],
    queryFn: repositoriesApi.list,
  });
  const repository = repositories.data?.find((repo) => String(repo.id) === id);
  const active = repository?.active ?? false;
  const sessions = useQuery({
    queryKey: ["chat-sessions"],
    queryFn: () => request<Session[]>("/chat/sessions"),
  });
  const selectedSession = useQuery({
    queryKey: ["chat-session", sessionId],
    queryFn: () => request<Session>(`/chat/sessions/${sessionId}`),
    enabled: sessionId !== null,
    refetchInterval: sessionId !== null ? 5000 : false,
  });
  const messages = useQuery({
    queryKey: ["chat-messages", sessionId],
    queryFn: () => request<Message[]>(`/chat/sessions/${sessionId}/messages`),
    enabled: sessionId !== null,
    refetchInterval: sessionId !== null ? 5000 : false,
  });
  const proposals = useQuery({
    queryKey: ["chat-proposals", sessionId],
    queryFn: () => request<Proposal[]>(`/chat/sessions/${sessionId}/proposals`),
    enabled: sessionId !== null,
    refetchInterval: sessionId !== null ? 5000 : false,
  });
  const logs = useQuery({
    queryKey: ["chat-logs", sessionId],
    queryFn: () => request<Log[]>(`/chat/sessions/${sessionId}/tool-logs`),
    enabled: sessionId !== null,
    refetchInterval: sessionId !== null ? 5000 : false,
  });
  const refreshSession = async (targetId: number) => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ["chat-messages", targetId] }),
      client.invalidateQueries({ queryKey: ["chat-proposals", targetId] }),
      client.invalidateQueries({ queryKey: ["chat-logs", targetId] }),
      client.invalidateQueries({ queryKey: ["chat-session", targetId] }),
      client.invalidateQueries({ queryKey: ["chat-sessions"] }),
    ]);
  };
  useEffect(() => {
    if (
      sessionId !== null &&
      wasBusy.current &&
      selectedSession.data?.busy === false
    ) {
      void Promise.all([
        client.invalidateQueries({ queryKey: ["chat-messages", sessionId] }),
        client.invalidateQueries({ queryKey: ["chat-proposals", sessionId] }),
        client.invalidateQueries({ queryKey: ["chat-logs", sessionId] }),
      ]);
    }
    wasBusy.current = selectedSession.data?.busy ?? false;
  }, [client, selectedSession.data?.busy, sessionId]);
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
    mutationFn: ({
      targetId,
      content,
    }: {
      targetId: number;
      content: string;
    }) =>
      request<Message>(`/chat/sessions/${targetId}/messages`, {
        method: "POST",
        body: JSON.stringify({ content: content.trim() }),
      }),
    onSuccess: (_message, { targetId, content }) =>
      setDrafts((previous) => {
        if (previous[targetId] !== content) return previous;
        const next = { ...previous };
        delete next[targetId];
        return next;
      }),
    onSettled: (_data, _error, variables) => refreshSession(variables.targetId),
  });
  const decide = useMutation({
    mutationFn: ({
      proposalId,
      action,
    }: {
      proposalId: number;
      action: "approve" | "reject";
      targetId: number;
    }) =>
      request<Proposal>(`/proposals/${proposalId}/${action}`, {
        method: "POST",
        body: JSON.stringify(
          action === "approve" ? { confirmation: "APPROVE" } : {},
        ),
      }),
    onMutate: () => setActionError(null),
    onError: (error) => setActionError(error),
    onSettled: (_data, _error, variables) => refreshSession(variables.targetId),
  });
  const actionResolved = proposals.data?.some(
    (proposal) =>
      proposal.id === decide.variables?.proposalId &&
      ["SUCCEEDED", "REJECTED"].includes(proposal.state),
  );
  const visibleSessions =
    sessions.data?.filter((session) => String(session.repositoryId) === id) ??
    [];
  const text = sessionId === null ? "" : (drafts[sessionId] ?? "");
  const operationPending =
    create.isPending || send.isPending || decide.isPending;
  const sessionBusy = selectedSession.data?.busy ?? false;
  const canSend =
    active &&
    sessionId !== null &&
    selectedSession.isSuccess &&
    !sessionBusy &&
    !operationPending;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (canSend && sessionId !== null && text.trim())
      send.mutate({ targetId: sessionId, content: text });
  }
  function decideProposal(proposal: Proposal, action: "approve" | "reject") {
    if (sessionId === null) return;
    setConfirmId(null);
    decide.mutate({ proposalId: proposal.id, action, targetId: sessionId });
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
          disabled={!active || create.isPending || operationPending}
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
              disabled={operationPending}
              onChange={(event) => {
                send.reset();
                setActionError(null);
                setConfirmId(null);
                wasBusy.current = false;
                setSessionId(
                  event.target.value ? Number(event.target.value) : null,
                );
              }}
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
              {selectedSession.isError && (
                <ErrorState
                  error={selectedSession.error}
                  retry={() => void selectedSession.refetch()}
                />
              )}
              {sessionBusy && !send.isPending && (
                <div className="assistant-notice session-busy" role="status">
                  A response is in progress in another window. This conversation
                  will update when it finishes.
                </div>
              )}
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
              {send.error && (
                <>
                  <ErrorState error={send.error} />
                  {send.error instanceof ApiError &&
                    (send.error.status === 0 ||
                      send.error.status >= 500 ||
                      send.error.code === "AI_LIMIT_REACHED" ||
                      send.error.code === "INVALID_RESPONSE") && (
                      <p className="assistant-help">
                        Your message may already be saved. Check the
                        conversation before sending it again.
                      </p>
                    )}
                </>
              )}
              <form className="message-form" onSubmit={submit}>
                <label htmlFor="chat-message">Your message</label>
                <textarea
                  id="chat-message"
                  value={text}
                  onChange={(event) =>
                    setDrafts((previous) => ({
                      ...previous,
                      [sessionId]: event.target.value,
                    }))
                  }
                  placeholder="What should I work on next?"
                  maxLength={10000}
                  required
                  disabled={!canSend}
                />
                <button
                  className="button primary"
                  disabled={!canSend || !text.trim()}
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
            {actionError && !actionResolved && (
              <ErrorState error={actionError} />
            )}
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
                  <p
                    className="proposal-body"
                    tabIndex={0}
                    aria-label="Issue proposal details"
                  >
                    {proposal.body}
                  </p>
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
                  {proposal.state === "EXECUTING" && (
                    <p className="uncertain">
                      Creation is in progress. The status will update
                      automatically; do not submit it again.
                    </p>
                  )}
                  {proposal.state === "UNKNOWN" && (
                    <p className="uncertain">
                      Creation could not be confirmed. Check GitHub before
                      taking further action; this proposal cannot be retried.
                    </p>
                  )}
                  {proposal.state === "PENDING" && (
                    <div className="proposal-actions">
                      {confirmId === proposal.id ? (
                        <div
                          className="approval-confirm"
                          role="group"
                          aria-label={`Confirm creation of ${proposal.title}`}
                        >
                          <p>
                            Create <strong>{proposal.title}</strong> in{" "}
                            <strong>
                              {repository?.fullName ?? "this repository"}
                            </strong>{" "}
                            on GitHub?
                          </p>
                          <div className="proposal-actions">
                            <button
                              className="button primary"
                              disabled={!active || operationPending}
                              autoFocus
                              onClick={() =>
                                decideProposal(proposal, "approve")
                              }
                            >
                              Confirm creation
                            </button>
                            <button
                              className="button"
                              disabled={operationPending}
                              onClick={() => setConfirmId(null)}
                            >
                              Cancel
                            </button>
                          </div>
                        </div>
                      ) : (
                        <>
                          <button
                            className="button primary"
                            disabled={!active || operationPending}
                            onClick={() => setConfirmId(proposal.id)}
                          >
                            Approve and create
                          </button>
                          <button
                            className="button"
                            disabled={operationPending}
                            onClick={() => decideProposal(proposal, "reject")}
                          >
                            Reject
                          </button>
                        </>
                      )}
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
