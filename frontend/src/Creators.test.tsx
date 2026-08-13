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
import { routes } from './router.tsx'

const creator: Creator = {
  id: '7b44fd57-cf3f-4a69-9663-b3db27e3f662',
  name: 'MrBeast',
  createdAt: '2026-08-13T12:00:00Z',
  updatedAt: '2026-08-13T12:00:00Z',
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
}

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

describe('Creators page', () => {
  it('shows loading and then the loaded creators', async () => {
    let resolveResponse: (response: Response) => void = () => undefined
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValue(
        new Promise<Response>((resolve) => {
          resolveResponse = resolve
        }),
      ),
    )

    renderAt('/creators')
    expect(screen.getByText('Loading creators…')).toBeTruthy()

    resolveResponse(jsonResponse([creator]))
    expect(await screen.findByRole('link', { name: 'MrBeast' })).toBeTruthy()
  })

  it('shows the empty state', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([])))

    renderAt('/creators')

    expect(
      await screen.findByText('No creators yet. Add the first one above.'),
    ).toBeTruthy()
  })

  it('renders a list of creators', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse([
          creator,
          { ...creator, id: '248b7019-99cc-442b-b93a-13eb3b87b1a1', name: 'Acme Fitness' },
        ]),
      ),
    )

    renderAt('/creators')

    expect(await screen.findByRole('link', { name: 'MrBeast' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Acme Fitness' })).toBeTruthy()
  })

  it('creates a Creator, clears the form, and refreshes the list', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(creator, 201))
      .mockResolvedValueOnce(jsonResponse([creator]))
    vi.stubGlobal('fetch', fetchMock)
    renderAt('/creators')
    await screen.findByText('No creators yet. Add the first one above.')

    const nameInput = screen.getByRole('textbox', { name: 'Name' })
    fireEvent.change(nameInput, { target: { value: '  MrBeast  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add Creator' }))

    expect(await screen.findByRole('link', { name: 'MrBeast' })).toBeTruthy()
    expect((nameInput as HTMLInputElement).value).toBe('')
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3))

    expect(fetchMock.mock.calls[1][0]).toBe('/api/creators')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body as string)).toEqual({
      name: '  MrBeast  ',
    })
  })

  it('rejects blank input without calling the create endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    renderAt('/creators')
    await screen.findByText('No creators yet. Add the first one above.')

    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), {
      target: { value: '   ' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add Creator' }))

    expect(screen.getByRole('alert').textContent).toContain('Enter a creator name.')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shows a backend failure without crashing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('internal detail')))

    renderAt('/creators')

    expect(
      await screen.findByText('Unable to load creators. Check the backend and try again.'),
    ).toBeTruthy()
  })

  it('shows a create failure returned by the backend', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        jsonResponse({ detail: 'Creator could not be saved.' }, 500),
      )
    vi.stubGlobal('fetch', fetchMock)
    renderAt('/creators')
    await screen.findByText('No creators yet. Add the first one above.')

    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), {
      target: { value: 'MrBeast' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add Creator' }))

    expect(await screen.findByText('Creator could not be saved.')).toBeTruthy()
  })
})

describe('Creator detail page', () => {
  it('shows the Creator and created timestamp', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(creator)))

    renderAt(`/creators/${creator.id}`)

    expect(await screen.findByRole('heading', { name: 'MrBeast' })).toBeTruthy()
    const createdTime = document.querySelector('time')
    expect(createdTime?.getAttribute('datetime')).toBe(creator.createdAt)
    expect(screen.getByRole('link', { name: '← Back to Creators' })).toBeTruthy()
  })

  it('shows the not-found state for a missing Creator', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ detail: 'Creator was not found' }, 404),
      ),
    )

    renderAt(`/creators/${creator.id}`)

    expect(
      await screen.findByRole('heading', { name: 'Creator not found' }),
    ).toBeTruthy()
    expect(screen.getByText('The requested Creator does not exist.')).toBeTruthy()
  })
})
