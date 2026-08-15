import type { RouteObject } from 'react-router-dom'
import App, { NotFound } from './App.tsx'
import { CreatorDetailPage } from './CreatorDetailPage.tsx'
import { CreatorsPage } from './CreatorsPage.tsx'
import { SourceVideoDetailPage } from './SourceVideoDetailPage.tsx'

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <App />,
  },
  {
    path: '/creators',
    element: <CreatorsPage />,
  },
  {
    path: '/creators/:creatorId',
    element: <CreatorDetailPage />,
  },
  {
    path: '/source-videos/:sourceVideoId',
    element: <SourceVideoDetailPage />,
  },
  {
    path: '*',
    element: <NotFound />,
  },
]
