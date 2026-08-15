import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { CreatorApiError, getCreator } from './api/creators.ts'
import {
  createSourceVideo,
  getSourceVideos,
  type SourceVideoPage,
} from './api/sourceVideos.ts'
import './App.css'

const SOURCE_VIDEO_PAGE_SIZE = 30

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
            <SourceVideoWorkspace creatorId={creatorQuery.data.id} />
          </div>
        ) : null}
      </section>
    </main>
  )
}

function SourceVideoWorkspace({ creatorId }: { creatorId: string }) {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [title, setTitle] = useState('')
  const [originUrl, setOriginUrl] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const pageParam = parsePageParam(searchParams.get('page'))
  const page = pageParam.page

  const updatePage = useCallback(
    (nextPage: number, replace = false) => {
      const nextSearchParams = new URLSearchParams(searchParams)
      if (nextPage === 0) {
        nextSearchParams.delete('page')
      } else {
        nextSearchParams.set('page', (nextPage + 1).toString())
      }
      setSearchParams(nextSearchParams, { replace })
    },
    [searchParams, setSearchParams],
  )

  useEffect(() => {
    if (!pageParam.isValid) {
      updatePage(0, true)
    }
  }, [pageParam.isValid, updatePage])

  const sourceVideosQuery = useQuery({
    queryKey: ['source-videos', creatorId, page, SOURCE_VIDEO_PAGE_SIZE],
    queryFn: () => getSourceVideos(creatorId, page, SOURCE_VIDEO_PAGE_SIZE),
    placeholderData: keepPreviousData,
    retry: false,
  })

  useEffect(() => {
    if (!sourceVideosQuery.isSuccess) {
      return
    }

    const lastPage = Math.max(sourceVideosQuery.data.totalPages - 1, 0)
    if (page > lastPage) {
      updatePage(lastPage, true)
    }
  }, [page, sourceVideosQuery.data, sourceVideosQuery.isSuccess, updatePage])

  const createMutation = useMutation({
    mutationFn: (input: { title: string; originUrl: string | null }) =>
      createSourceVideo(creatorId, input),
    onSuccess: (createdSourceVideo) => {
      setTitle('')
      setOriginUrl('')
      setValidationError(null)
      updatePage(0, true)
      queryClient.setQueryData<SourceVideoPage>(
        ['source-videos', creatorId, 0, SOURCE_VIDEO_PAGE_SIZE],
        (currentPage) => {
          if (currentPage === undefined) {
            return currentPage
          }

          const items = [
            createdSourceVideo,
            ...currentPage.items.filter(
              (sourceVideo) => sourceVideo.id !== createdSourceVideo.id,
            ),
          ].slice(0, currentPage.size)
          const totalElements = currentPage.totalElements + 1

          return {
            ...currentPage,
            items,
            totalElements,
            totalPages: Math.ceil(totalElements / currentPage.size),
          }
        },
      )
      void queryClient.invalidateQueries({
        queryKey: ['source-videos', creatorId],
      })
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createMutation.reset()

    const normalizedTitle = title.trim()
    const normalizedOriginUrl = originUrl.trim()
    if (normalizedTitle.length === 0) {
      setValidationError('Enter a Source Video title.')
      return
    }
    if (normalizedTitle.length > 300) {
      setValidationError('Source Video title must be at most 300 characters.')
      return
    }
    if (normalizedOriginUrl.length > 2048) {
      setValidationError('Origin URL must be at most 2048 characters.')
      return
    }
    if (
      normalizedOriginUrl.length > 0 &&
      !isAbsoluteHttpUrl(normalizedOriginUrl)
    ) {
      setValidationError('Enter a valid HTTP or HTTPS origin URL.')
      return
    }

    setValidationError(null)
    createMutation.mutate({
      title: normalizedTitle,
      originUrl: normalizedOriginUrl.length > 0 ? normalizedOriginUrl : null,
    })
  }

  const sourceVideoPage = sourceVideosQuery.data
  const hasMultiplePages =
    sourceVideoPage !== undefined && sourceVideoPage.totalPages > 1

  return (
    <section className="source-video-workspace" aria-labelledby="source-videos-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Content workspace</p>
          <h2 id="source-videos-title">Source Videos</h2>
        </div>
        {sourceVideoPage ? (
          <p className="result-count">
            {sourceVideoPage.totalElements}{' '}
            {sourceVideoPage.totalElements === 1 ? 'video' : 'videos'}
          </p>
        ) : null}
      </div>

      <form className="source-video-form" onSubmit={handleSubmit} noValidate>
        <label className="creator-form-field">
          <span>Title</span>
          <input
            name="title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={300}
            aria-describedby={
              validationError ? 'source-video-form-error' : undefined
            }
          />
        </label>
        <label className="creator-form-field">
          <span>Origin URL (optional)</span>
          <input
            name="originUrl"
            type="url"
            value={originUrl}
            onChange={(event) => setOriginUrl(event.target.value)}
            maxLength={2048}
            placeholder="https://example.com/video"
            aria-describedby={
              validationError ? 'source-video-form-error' : undefined
            }
          />
        </label>
        <button type="submit" disabled={createMutation.isPending}>
          {createMutation.isPending ? 'Adding…' : 'Add Source Video'}
        </button>
      </form>

      {validationError ? (
        <p
          className="form-error"
          id="source-video-form-error"
          role="alert"
        >
          {validationError}
        </p>
      ) : null}
      {createMutation.isError ? (
        <p className="form-error" role="alert">
          {createMutation.error.message}
        </p>
      ) : null}

      <div className="source-video-results" aria-live="polite">
        {sourceVideosQuery.isPending ? <p>Loading Source Videos…</p> : null}
        {sourceVideosQuery.isError ? (
          <p className="page-error" role="alert">
            Unable to load Source Videos. Check the backend and try again.
          </p>
        ) : null}
        {sourceVideosQuery.isSuccess &&
        sourceVideoPage !== undefined &&
        sourceVideoPage.items.length === 0 ? (
          <p className="empty-state">
            No Source Videos yet. Add the first one above.
          </p>
        ) : null}
        {sourceVideosQuery.isSuccess &&
        sourceVideoPage !== undefined &&
        sourceVideoPage.items.length > 0 ? (
          <ul className="source-video-list">
            {sourceVideoPage.items.map((sourceVideo) => (
              <li key={sourceVideo.id}>
                <Link to={`/source-videos/${sourceVideo.id}`}>
                  {sourceVideo.title}
                </Link>
                {sourceVideo.originUrl ? (
                  <a
                    className="origin-url"
                    href={sourceVideo.originUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {sourceVideo.originUrl}
                  </a>
                ) : (
                  <span className="origin-url missing-origin-url">
                    No origin URL
                  </span>
                )}
              </li>
            ))}
          </ul>
        ) : null}

        {sourceVideosQuery.isSuccess && hasMultiplePages ? (
          <nav className="pagination" aria-label="Source Videos pagination">
            <button
              type="button"
              onClick={() => updatePage(page - 1)}
              disabled={page === 0 || sourceVideosQuery.isFetching}
            >
              Previous
            </button>
            <p>
              Page {page + 1} of {sourceVideoPage.totalPages}
            </p>
            <button
              type="button"
              onClick={() => updatePage(page + 1)}
              disabled={
                page + 1 >= sourceVideoPage.totalPages ||
                sourceVideosQuery.isFetching
              }
            >
              Next
            </button>
          </nav>
        ) : null}
      </div>
    </section>
  )
}

function formatTimestamp(timestamp: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(timestamp))
}

function isAbsoluteHttpUrl(value: string) {
  try {
    const url = new URL(value)
    return (
      (url.protocol === 'http:' || url.protocol === 'https:') &&
      url.hostname.length > 0
    )
  } catch {
    return false
  }
}

function parsePageParam(value: string | null) {
  if (value === null) {
    return { page: 0, isValid: true }
  }

  if (!/^[1-9]\d*$/.test(value)) {
    return { page: 0, isValid: false }
  }

  const pageNumber = Number(value)
  if (!Number.isSafeInteger(pageNumber) || pageNumber > 2_147_483_648) {
    return { page: 0, isValid: false }
  }

  return { page: pageNumber - 1, isValid: true }
}
