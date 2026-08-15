import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import type { Creator } from './api/creators.ts'
import type {
  SourceVideo,
  SourceVideoPage,
} from './api/sourceVideos.ts'
import { routes } from './router.tsx'

const creator: Creator = {
  id: '7b44fd57-cf3f-4a69-9663-b3db27e3f662',
  name: 'Lex Fridman',
  createdAt: '2026-08-13T12:00:00Z',
  updatedAt: '2026-08-13T12:00:00Z',
}

const sourceVideo: SourceVideo = {
  id: '248b7019-99cc-442b-b93a-13eb3b87b1a1',
  creatorId: creator.id,
  title: 'Donald Knuth Interview',
  originUrl: 'https://example.com/knuth',
  createdAt: '2026-08-13T13:00:00Z',
  updatedAt: '2026-08-13T13:05:00Z',
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function renderAt(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  const router = createMemoryRouter(routes, { initialEntries: [path] })

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )

  return router
}

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

function sourceVideoPage(
  items: SourceVideo[],
  overrides: Partial<SourceVideoPage> = {},
): SourceVideoPage {
  return {
    items,
    page: 0,
    size: 30,
    totalElements: items.length,
    totalPages: items.length === 0 ? 0 : 1,
    ...overrides,
  }
}

describe('Creator Source Videos workspace', () => {
  it('shows the Source Videos empty state for an existing Creator', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(jsonResponse(sourceVideoPage([])))
    vi.stubGlobal('fetch', fetchMock)

    renderAt(`/creators/${creator.id}`)

    expect(
      await screen.findByText('No Source Videos yet. Add the first one above.'),
    ).toBeTruthy()
    expect(screen.getByRole('textbox', { name: 'Title' })).toBeTruthy()
    expect(
      screen.getByRole('textbox', { name: 'Origin URL (optional)' }),
    ).toBeTruthy()
    expect(fetchMock.mock.calls[1][0]).toBe(
      `/api/creators/${creator.id}/source-videos?page=0&size=30`,
    )
  })

  it('creates a Source Video, clears the form, and refreshes the list', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(jsonResponse(sourceVideoPage([])))
      .mockResolvedValueOnce(jsonResponse(sourceVideo, 201))
      .mockResolvedValueOnce(jsonResponse(sourceVideoPage([sourceVideo])))
    vi.stubGlobal('fetch', fetchMock)
    renderAt(`/creators/${creator.id}`)
    await screen.findByText('No Source Videos yet. Add the first one above.')

    const titleInput = screen.getByRole('textbox', { name: 'Title' })
    const originUrlInput = screen.getByRole('textbox', {
      name: 'Origin URL (optional)',
    })
    fireEvent.change(titleInput, {
      target: { value: '  Donald Knuth Interview  ' },
    })
    fireEvent.change(originUrlInput, {
      target: { value: '  https://example.com/knuth  ' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add Source Video' }))

    expect(
      await screen.findByRole('link', { name: 'Donald Knuth Interview' }),
    ).toBeTruthy()
    expect(
      screen.getByRole('link', { name: 'https://example.com/knuth' }),
    ).toBeTruthy()
    expect((titleInput as HTMLInputElement).value).toBe('')
    expect((originUrlInput as HTMLInputElement).value).toBe('')
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4))

    expect(fetchMock.mock.calls[2][0]).toBe(
      `/api/creators/${creator.id}/source-videos`,
    )
    expect(JSON.parse(fetchMock.mock.calls[2][1].body as string)).toEqual({
      title: 'Donald Knuth Interview',
      originUrl: 'https://example.com/knuth',
    })
  })

  it('sends a missing optional origin URL as null', async () => {
    const withoutOriginUrl = {
      ...sourceVideo,
      title: 'No URL Episode',
      originUrl: null,
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(jsonResponse(sourceVideoPage([])))
      .mockResolvedValueOnce(jsonResponse(withoutOriginUrl, 201))
      .mockResolvedValueOnce(jsonResponse(sourceVideoPage([withoutOriginUrl])))
    vi.stubGlobal('fetch', fetchMock)
    renderAt(`/creators/${creator.id}`)
    await screen.findByText('No Source Videos yet. Add the first one above.')

    fireEvent.change(screen.getByRole('textbox', { name: 'Title' }), {
      target: { value: 'No URL Episode' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add Source Video' }))

    expect(await screen.findByText('No origin URL')).toBeTruthy()
    expect(JSON.parse(fetchMock.mock.calls[2][1].body as string)).toEqual({
      title: 'No URL Episode',
      originUrl: null,
    })
  })

  it('validates the title and origin URL before creating', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(jsonResponse(sourceVideoPage([])))
    vi.stubGlobal('fetch', fetchMock)
    renderAt(`/creators/${creator.id}`)
    await screen.findByText('No Source Videos yet. Add the first one above.')

    fireEvent.click(screen.getByRole('button', { name: 'Add Source Video' }))
    expect(screen.getByRole('alert').textContent).toContain(
      'Enter a Source Video title.',
    )

    fireEvent.change(screen.getByRole('textbox', { name: 'Title' }), {
      target: { value: 'Interview' },
    })
    fireEvent.change(
      screen.getByRole('textbox', { name: 'Origin URL (optional)' }),
      { target: { value: '/relative/video' } },
    )
    fireEvent.click(screen.getByRole('button', { name: 'Add Source Video' }))

    expect(screen.getByRole('alert').textContent).toContain(
      'Enter a valid HTTP or HTTPS origin URL.',
    )
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('shows a backend validation detail without losing the form values', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(jsonResponse(sourceVideoPage([])))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            detail:
              'Origin URL must be an absolute HTTP or HTTPS URL with a valid host',
          },
          400,
        ),
      )
    vi.stubGlobal('fetch', fetchMock)
    renderAt(`/creators/${creator.id}`)
    await screen.findByText('No Source Videos yet. Add the first one above.')

    const titleInput = screen.getByRole('textbox', { name: 'Title' })
    fireEvent.change(titleInput, { target: { value: 'Interview' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add Source Video' }))

    expect(
      await screen.findByText(
        'Origin URL must be an absolute HTTP or HTTPS URL with a valid host',
      ),
    ).toBeTruthy()
    expect((titleInput as HTMLInputElement).value).toBe('Interview')
  })

  it('navigates through paginated Source Videos', async () => {
    const secondPageVideo = {
      ...sourceVideo,
      id: '2c2be5a0-3bd7-4d5e-9e34-a97516350b4c',
      title: 'Oldest Episode',
      originUrl: null,
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(
        jsonResponse(
          sourceVideoPage([sourceVideo], {
            totalElements: 31,
            totalPages: 2,
          }),
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          sourceVideoPage([secondPageVideo], {
            page: 1,
            totalElements: 31,
            totalPages: 2,
          }),
        ),
      )
    vi.stubGlobal('fetch', fetchMock)
    const router = renderAt(`/creators/${creator.id}`)

    expect(await screen.findByText('Page 1 of 2')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))

    expect(
      await screen.findByRole('link', { name: 'Oldest Episode' }),
    ).toBeTruthy()
    expect(screen.getByText('Page 2 of 2')).toBeTruthy()
    expect(router.state.location.search).toBe('?page=2')
    expect(screen.getByText('No origin URL')).toBeTruthy()
    expect(fetchMock.mock.calls[2][0]).toBe(
      `/api/creators/${creator.id}/source-videos?page=1&size=30`,
    )

    await router.navigate(-1)
    expect(await screen.findByText('Page 1 of 2')).toBeTruthy()

    await router.navigate(1)
    expect(await screen.findByText('Page 2 of 2')).toBeTruthy()
  })

  it('restores page state from the URL and normalizes invalid page values', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(
        jsonResponse(
          sourceVideoPage([sourceVideo], {
            page: 1,
            totalElements: 31,
            totalPages: 2,
          }),
        ),
      )
    vi.stubGlobal('fetch', fetchMock)

    const pageRouter = renderAt(`/creators/${creator.id}?page=2`)

    expect(await screen.findByText('Page 2 of 2')).toBeTruthy()
    expect(pageRouter.state.location.search).toBe('?page=2')
    expect(fetchMock.mock.calls[1][0]).toBe(
      `/api/creators/${creator.id}/source-videos?page=1&size=30`,
    )

    cleanup()
    fetchMock.mockReset()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(
        jsonResponse(
          sourceVideoPage([sourceVideo], {
            totalElements: 31,
            totalPages: 2,
          }),
        ),
      )

    const invalidRouter = renderAt(`/creators/${creator.id}?page=abc`)

    expect(await screen.findByText('Page 1 of 2')).toBeTruthy()
    await waitFor(() => expect(invalidRouter.state.location.search).toBe(''))
    expect(fetchMock.mock.calls[1][0]).toBe(
      `/api/creators/${creator.id}/source-videos?page=0&size=30`,
    )
  })

  it('normalizes an out-of-range URL page to the final page', async () => {
    const secondPageVideo = {
      ...sourceVideo,
      id: '2c2be5a0-3bd7-4d5e-9e34-a97516350b4c',
      title: 'Oldest Episode',
      originUrl: null,
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(creator))
      .mockResolvedValueOnce(
        jsonResponse(
          sourceVideoPage([], {
            page: 98,
            totalElements: 31,
            totalPages: 2,
          }),
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          sourceVideoPage([secondPageVideo], {
            page: 1,
            totalElements: 31,
            totalPages: 2,
          }),
        ),
      )
    vi.stubGlobal('fetch', fetchMock)

    const router = renderAt(`/creators/${creator.id}?page=99`)

    expect(await screen.findByText('Oldest Episode')).toBeTruthy()
    expect(screen.getByText('Page 2 of 2')).toBeTruthy()
    expect(router.state.location.search).toBe('?page=2')
    expect(fetchMock.mock.calls[1][0]).toBe(
      `/api/creators/${creator.id}/source-videos?page=98&size=30`,
    )
    expect(fetchMock.mock.calls[2][0]).toBe(
      `/api/creators/${creator.id}/source-videos?page=1&size=30`,
    )
  })
})

describe('Source Video detail page', () => {
  it('shows Source Video metadata and a link back to its Creator', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(sourceVideo))
    vi.stubGlobal('fetch', fetchMock)

    renderAt(`/source-videos/${sourceVideo.id}`)

    expect(
      await screen.findByRole('heading', { name: 'Donald Knuth Interview' }),
    ).toBeTruthy()
    expect(
      screen
        .getByRole('link', { name: '← Back to Creator' })
        .getAttribute('href'),
    ).toBe(`/creators/${creator.id}`)
    expect(
      screen.getByRole('link', { name: 'https://example.com/knuth' }),
    ).toBeTruthy()
    expect(document.querySelectorAll('time')).toHaveLength(2)
    expect(fetchMock.mock.calls[0][0]).toBe(
      `/api/source-videos/${sourceVideo.id}`,
    )
  })

  it('shows the Source Video not-found state', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ detail: 'SourceVideo was not found' }, 404),
      ),
    )

    renderAt(`/source-videos/${sourceVideo.id}`)

    expect(
      await screen.findByRole('heading', { name: 'Source Video not found' }),
    ).toBeTruthy()
    expect(
      screen.getByText('The requested Source Video does not exist.'),
    ).toBeTruthy()
  })
})
