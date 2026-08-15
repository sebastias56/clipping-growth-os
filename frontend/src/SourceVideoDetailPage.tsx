import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import {
  getSourceVideo,
  SourceVideoApiError,
} from './api/sourceVideos.ts'
import './App.css'

export function SourceVideoDetailPage() {
  const { sourceVideoId } = useParams()
  const sourceVideoQuery = useQuery({
    queryKey: ['source-video', sourceVideoId],
    queryFn: () => getSourceVideo(sourceVideoId!),
    enabled: sourceVideoId !== undefined,
    retry: false,
  })

  const isNotFound =
    sourceVideoQuery.isError &&
    sourceVideoQuery.error instanceof SourceVideoApiError &&
    sourceVideoQuery.error.status === 404

  return (
    <main className="creator-shell">
      <section
        className="creator-card creator-detail"
        aria-labelledby="source-video-title"
      >
        {sourceVideoQuery.isPending ? <p>Loading Source Video…</p> : null}
        {isNotFound ? (
          <div>
            <Link className="text-link back-link" to="/creators">
              ← Back to Creators
            </Link>
            <p className="eyebrow">404</p>
            <h1 id="source-video-title">Source Video not found</h1>
            <p>The requested Source Video does not exist.</p>
          </div>
        ) : null}
        {sourceVideoQuery.isError && !isNotFound ? (
          <div>
            <Link className="text-link back-link" to="/creators">
              ← Back to Creators
            </Link>
            <h1 id="source-video-title">Unable to load Source Video</h1>
            <p className="page-error" role="alert">
              Check the backend and try again.
            </p>
          </div>
        ) : null}
        {sourceVideoQuery.isSuccess ? (
          <div>
            <Link
              className="text-link back-link"
              to={`/creators/${sourceVideoQuery.data.creatorId}`}
            >
              ← Back to Creator
            </Link>
            <p className="eyebrow">Source Video</p>
            <h1 id="source-video-title">{sourceVideoQuery.data.title}</h1>
            <dl className="source-video-metadata">
              <div>
                <dt>Origin URL</dt>
                <dd>
                  {sourceVideoQuery.data.originUrl ? (
                    <a
                      className="origin-url"
                      href={sourceVideoQuery.data.originUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {sourceVideoQuery.data.originUrl}
                    </a>
                  ) : (
                    'No origin URL'
                  )}
                </dd>
              </div>
              <div>
                <dt>Created</dt>
                <dd>
                  <time dateTime={sourceVideoQuery.data.createdAt}>
                    {formatTimestamp(sourceVideoQuery.data.createdAt)}
                  </time>
                </dd>
              </div>
              <div>
                <dt>Updated</dt>
                <dd>
                  <time dateTime={sourceVideoQuery.data.updatedAt}>
                    {formatTimestamp(sourceVideoQuery.data.updatedAt)}
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
