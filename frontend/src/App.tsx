import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getSystemStatus } from './api/systemStatus.ts'
import './App.css'

function App() {
  const systemStatus = useQuery({
    queryKey: ['system-status'],
    queryFn: getSystemStatus,
    retry: false,
    staleTime: Number.POSITIVE_INFINITY,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })

  const statusView = systemStatus.isPending
    ? {
        aggregate: 'CHECKING',
        backend: 'CHECKING',
        mediaWorker: 'CHECKING',
        message: 'Checking service connectivity…',
      }
    : systemStatus.isError
      ? {
          aggregate: 'UNAVAILABLE',
          backend: 'DOWN',
          mediaWorker: 'UNKNOWN',
          message: 'Unable to reach the backend.',
        }
      : {
          aggregate: systemStatus.data.status,
          backend: systemStatus.data.backend.status,
          mediaWorker: systemStatus.data.mediaWorker.status,
          message:
            systemStatus.data.status === 'UP'
              ? 'All connected services are available.'
              : 'The application is reachable, but a connected service is unavailable.',
        }

  return (
    <main className="app-shell">
      <section className="foundation-card" aria-labelledby="app-title">
        <p className="eyebrow">System status</p>
        <h1 id="app-title">Clipping Growth OS</h1>
        <p className="system-summary" aria-live="polite">
          System <strong>{statusView.aggregate}</strong>
        </p>
        <dl className="status-grid">
          <div role="group" aria-label="Backend status">
            <dt>Backend</dt>
            <dd data-status={statusView.backend}>{statusView.backend}</dd>
          </div>
          <div role="group" aria-label="Media Worker status">
            <dt>Media Worker</dt>
            <dd data-status={statusView.mediaWorker}>{statusView.mediaWorker}</dd>
          </div>
        </dl>
        <p className="status-message">{statusView.message}</p>
      </section>
    </main>
  )
}

export function NotFound() {
  return (
    <main className="app-shell">
      <section className="foundation-card" aria-labelledby="not-found-title">
        <p className="eyebrow">404</p>
        <h1 id="not-found-title">Page not found</h1>
        <Link className="home-link" to="/">
          Return home
        </Link>
      </section>
    </main>
  )
}

export default App
