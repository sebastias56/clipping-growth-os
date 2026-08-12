import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import type { SystemStatusResponse } from './api/systemStatus.ts'
import { routes } from './router.tsx'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function renderApplication() {
  const queryClient = new QueryClient()
  const router = createMemoryRouter(routes, { initialEntries: ['/'] })

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

function mockStatusResponse(status: SystemStatusResponse) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue(status),
    }),
  )
}

describe('application shell', () => {
  it('shows the loading state while status is pending', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValue(new Promise(() => undefined)),
    )
    renderApplication()

    expect(
      screen.getByRole('heading', { name: 'Clipping Growth OS' }),
    ).toBeTruthy()
    expect(screen.getByText('Checking service connectivity…')).toBeTruthy()
  })

  it('renders both services as up from the backend response', async () => {
    mockStatusResponse({
      status: 'UP',
      backend: { status: 'UP' },
      mediaWorker: { status: 'UP' },
    })
    renderApplication()

    expect(await screen.findByText('All connected services are available.')).toBeTruthy()
    expect(
      within(screen.getByRole('group', { name: 'Backend status' })).getByText('UP'),
    ).toBeTruthy()
    expect(
      within(screen.getByRole('group', { name: 'Media Worker status' })).getByText(
        'UP',
      ),
    ).toBeTruthy()
  })

  it('renders a degraded response with the media worker down', async () => {
    mockStatusResponse({
      status: 'DEGRADED',
      backend: { status: 'UP' },
      mediaWorker: { status: 'DOWN' },
    })
    renderApplication()

    expect(
      await screen.findByText(
        'The application is reachable, but a connected service is unavailable.',
      ),
    ).toBeTruthy()
    expect(
      within(screen.getByRole('group', { name: 'Backend status' })).getByText('UP'),
    ).toBeTruthy()
    expect(
      within(screen.getByRole('group', { name: 'Media Worker status' })).getByText(
        'DOWN',
      ),
    ).toBeTruthy()
  })

  it('represents a backend request failure without crashing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('internal detail')))
    renderApplication()

    expect(await screen.findByText('Unable to reach the backend.')).toBeTruthy()
    expect(
      within(screen.getByRole('group', { name: 'Backend status' })).getByText(
        'DOWN',
      ),
    ).toBeTruthy()
    expect(
      within(screen.getByRole('group', { name: 'Media Worker status' })).getByText(
        'UNKNOWN',
      ),
    ).toBeTruthy()
  })
})
