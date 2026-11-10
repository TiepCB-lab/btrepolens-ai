import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  BrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useParams,
} from "react-router";
import {
  BookOpen,
  CircleDot,
  GitPullRequest,
  LayoutGrid,
  PanelsTopLeft,
} from "lucide-react";
import { RepositoryList } from "./features/repositories/RepositoryList";
import { AssistantPage } from "./features/assistant/AssistantPage";
import {
  OverviewPage,
  RepositoryLayout,
  WorkDetailPage,
  WorkListPage,
} from "./features/repositories/RepositoryPages";
import "./styles.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000, refetchOnWindowFocus: false },
  },
});

function Shell() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link to="/repositories" className="brand">
          <span className="brand-mark">
            <PanelsTopLeft size={19} strokeWidth={2.2} />
          </span>
          <span>
            BTRepoLens <b>AI</b>
            <small>GitHub workspace</small>
          </span>
        </Link>
        <div className="nav-caption">Workspace</div>
        <Link className="side-link selected" to="/repositories">
          <LayoutGrid size={17} /> Repositories
        </Link>
        <div className="side-note">
          <span className="side-note-title">Built for focus</span>
          <p>Your issues and pull requests, together in one workspace.</p>
          <span className="side-readonly">
            <span /> Writes require approval
          </span>
        </div>
      </aside>
      <div className="main-frame">
        <header className="topbar">
          <div className="mobile-brand">
            <PanelsTopLeft size={18} /> BTRepoLens <b>AI</b>
          </div>
          <div className="topbar-title">Repository workspace</div>
          <span className="topbar-status">
            <span /> Local workspace
          </span>
        </header>
        <main className="sheet">
          <Routes>
            <Route path="/" element={<Navigate to="/repositories" replace />} />
            <Route path="/repositories" element={<RepositoryList />} />
            <Route path="/repositories/:id/*" element={<RepositoryRoutes />} />
            <Route
              path="*"
              element={
                <div className="empty">
                  <h1>Page not found</h1>
                  <Link className="button" to="/repositories">
                    Go to repositories
                  </Link>
                </div>
              }
            />
          </Routes>
        </main>
        <footer className="footer">
          <span>BTRepoLens AI</span>
          <span>GitHub repository workspace</span>
        </footer>
      </div>
    </div>
  );
}

function RepositoryRoutes() {
  const { id } = useParams();
  return (
    <RepositoryLayout>
      <Routes>
        <Route index element={<OverviewPage />} />
        <Route path="issues" element={<WorkListPage kind="issues" />} />
        <Route path="assistant" element={<AssistantPage key={id} />} />
        <Route
          path="issues/:number"
          element={<WorkDetailPage kind="issues" />}
        />
        <Route
          path="pull-requests"
          element={<WorkListPage kind="pull-requests" />}
        />
        <Route
          path="pull-requests/:number"
          element={<WorkDetailPage kind="pull-requests" />}
        />
        <Route
          path="*"
          element={
            <div className="empty">
              <h2>Page not found</h2>
              <Link className="button" to={`/repositories/${id}`}>
                Back to overview
              </Link>
            </div>
          }
        />
      </Routes>
      <div className="mobile-shortcuts">
        <Link to={`/repositories/${id}`}>
          <LayoutGrid size={16} /> Overview
        </Link>
        <Link to={`/repositories/${id}/issues`}>
          <CircleDot size={16} /> Issues
        </Link>
        <Link to={`/repositories/${id}/pull-requests`}>
          <GitPullRequest size={16} /> Pull requests
        </Link>
        <Link to="/repositories">
          <BookOpen size={16} /> Repositories
        </Link>
      </div>
    </RepositoryLayout>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Shell />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
