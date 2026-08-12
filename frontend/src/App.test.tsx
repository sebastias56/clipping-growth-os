import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { routes } from './router.tsx'

afterEach(cleanup)

describe('application shell', () => {
  it('renders the root route through the application providers', () => {
    const queryClient = new QueryClient()
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })

    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )

    expect(
      screen.getByRole('heading', { name: 'Clipping Growth OS' }),
    ).toBeTruthy()
    expect(screen.getByText('Frontend foundation ready')).toBeTruthy()
  })
})
