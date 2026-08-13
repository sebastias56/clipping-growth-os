export interface Creator {
  id: string
  name: string
  createdAt: string
  updatedAt: string
}

export interface CreateCreatorInput {
  name: string
}

interface ProblemDetail {
  detail?: string
}

export class CreatorApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'CreatorApiError'
    this.status = status
  }
}

export async function getCreators(): Promise<Creator[]> {
  const response = await fetch('/api/creators', {
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    throw await creatorApiError(response, 'Unable to load creators.')
  }

  return (await response.json()) as Creator[]
}

export async function getCreator(creatorId: string): Promise<Creator> {
  const response = await fetch(`/api/creators/${encodeURIComponent(creatorId)}`, {
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    throw await creatorApiError(response, 'Unable to load this creator.')
  }

  return (await response.json()) as Creator
}

export async function createCreator(input: CreateCreatorInput): Promise<Creator> {
  const response = await fetch('/api/creators', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })

  if (!response.ok) {
    throw await creatorApiError(response, 'Unable to create the creator.')
  }

  return (await response.json()) as Creator
}

async function creatorApiError(
  response: Response,
  fallbackMessage: string,
): Promise<CreatorApiError> {
  try {
    const problem = (await response.json()) as ProblemDetail
    if (typeof problem.detail === 'string' && problem.detail.length > 0) {
      return new CreatorApiError(response.status, problem.detail)
    }
  } catch {
    // The fallback keeps malformed or non-JSON server errors user-safe.
  }

  return new CreatorApiError(response.status, fallbackMessage)
}
