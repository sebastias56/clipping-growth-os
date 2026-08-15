export interface SourceVideo {
  id: string
  creatorId: string
  title: string
  originUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateSourceVideoInput {
  title: string
  originUrl?: string | null
}

export interface SourceVideoPage {
  items: SourceVideo[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface ProblemDetail {
  detail?: string
}

export class SourceVideoApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'SourceVideoApiError'
    this.status = status
  }
}

export async function createSourceVideo(
  creatorId: string,
  input: CreateSourceVideoInput,
): Promise<SourceVideo> {
  const response = await fetch(
    `/api/creators/${encodeURIComponent(creatorId)}/source-videos`,
    {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(input),
    },
  )

  if (!response.ok) {
    throw await sourceVideoApiError(
      response,
      'Unable to create the Source Video.',
    )
  }

  return (await response.json()) as SourceVideo
}

export async function getSourceVideos(
  creatorId: string,
  page: number,
  size: number,
): Promise<SourceVideoPage> {
  const query = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  })
  const response = await fetch(
    `/api/creators/${encodeURIComponent(creatorId)}/source-videos?${query}`,
    {
      headers: {
        Accept: 'application/json',
      },
    },
  )

  if (!response.ok) {
    throw await sourceVideoApiError(
      response,
      'Unable to load Source Videos.',
    )
  }

  return (await response.json()) as SourceVideoPage
}

export async function getSourceVideo(
  sourceVideoId: string,
): Promise<SourceVideo> {
  const response = await fetch(
    `/api/source-videos/${encodeURIComponent(sourceVideoId)}`,
    {
      headers: {
        Accept: 'application/json',
      },
    },
  )

  if (!response.ok) {
    throw await sourceVideoApiError(
      response,
      'Unable to load this Source Video.',
    )
  }

  return (await response.json()) as SourceVideo
}

async function sourceVideoApiError(
  response: Response,
  fallbackMessage: string,
): Promise<SourceVideoApiError> {
  try {
    const problem = (await response.json()) as ProblemDetail
    if (typeof problem.detail === 'string' && problem.detail.length > 0) {
      return new SourceVideoApiError(response.status, problem.detail)
    }
  } catch {
    // The fallback keeps malformed or non-JSON server errors user-safe.
  }

  return new SourceVideoApiError(response.status, fallbackMessage)
}
