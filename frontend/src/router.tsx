import type { RouteObject } from 'react-router-dom'
import App, { NotFound } from './App.tsx'

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <App />,
  },
  {
    path: '*',
    element: <NotFound />,
  },
]
