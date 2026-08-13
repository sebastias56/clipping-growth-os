import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { CreatorApiError, getCreator } from './api/creators.ts'
import './App.css'

export function CreatorDetailPage() {
  const { creatorId } = useParams()
  const creatorQuery = useQuery({
    queryKey: ['creator', creatorId],
    queryFn: () => getCreator(creatorId!),
    enabled: creatorId !== undefined,
    retry: false,
  })

  const isNotFound =
    creatorQuery.isError &&
    creatorQuery.error instanceof CreatorApiError &&
    creatorQuery.error.status === 404

  return (
    <main className="creator-shell">
      <section className="creator-card creator-detail" aria-labelledby="creator-title">
        <Link className="text-link back-link" to="/creators">
          ← Back to Creators
        </Link>

        {creatorQuery.isPending ? <p>Loading creator…</p> : null}
        {isNotFound ? (
          <div>
            <p className="eyebrow">404</p>
            <h1 id="creator-title">Creator not found</h1>
            <p>The requested Creator does not exist.</p>
          </div>
        ) : null}
        {creatorQuery.isError && !isNotFound ? (
          <div>
            <h1 id="creator-title">Unable to load Creator</h1>
            <p className="page-error" role="alert">
              Check the backend and try again.
            </p>
          </div>
        ) : null}
        {creatorQuery.isSuccess ? (
          <div>
            <p className="eyebrow">Creator</p>
            <h1 id="creator-title">{creatorQuery.data.name}</h1>
            <dl className="creator-metadata">
              <div>
                <dt>Created</dt>
                <dd>
                  <time dateTime={creatorQuery.data.createdAt}>
                    {formatTimestamp(creatorQuery.data.createdAt)}
                  </time>
                </dd>
              </div>
            </dl>
          </div>
        ) : null}
      </section>
    </main>
  )
}

function formatTimestamp(timestamp: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(timestamp))
}
