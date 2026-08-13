import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  createCreator,
  getCreators,
  type Creator,
} from './api/creators.ts'
import './App.css'

export function CreatorsPage() {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  const creatorsQuery = useQuery({
    queryKey: ['creators'],
    queryFn: getCreators,
    retry: false,
  })

  const createMutation = useMutation({
    mutationFn: createCreator,
    onSuccess: (createdCreator) => {
      setName('')
      setValidationError(null)
      queryClient.setQueryData<Creator[]>(['creators'], (creators = []) => [
        createdCreator,
        ...creators.filter((creator) => creator.id !== createdCreator.id),
      ])
      void queryClient.invalidateQueries({ queryKey: ['creators'] })
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createMutation.reset()

    const normalizedName = name.trim()
    if (normalizedName.length === 0) {
      setValidationError('Enter a creator name.')
      return
    }
    if (normalizedName.length > 120) {
      setValidationError('Creator name must be at most 120 characters.')
      return
    }

    setValidationError(null)
    createMutation.mutate({ name })
  }

  return (
    <main className="creator-shell">
      <section className="creator-card" aria-labelledby="creators-title">
        <header className="creator-header">
          <div>
            <p className="eyebrow">Editorial owners</p>
            <h1 id="creators-title">Creators</h1>
          </div>
          <Link className="text-link" to="/">
            System status
          </Link>
        </header>

        <form className="creator-form" onSubmit={handleSubmit} noValidate>
          <label className="creator-form-field">
            <span>Name</span>
            <input
              name="name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={120}
              aria-describedby={validationError ? 'creator-name-error' : undefined}
            />
          </label>
          <button type="submit" disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Adding…' : 'Add Creator'}
          </button>
        </form>

        {validationError ? (
          <p className="form-error" id="creator-name-error" role="alert">
            {validationError}
          </p>
        ) : null}
        {createMutation.isError ? (
          <p className="form-error" role="alert">
            {createMutation.error.message}
          </p>
        ) : null}

        <div className="creator-results" aria-live="polite">
          {creatorsQuery.isPending ? <p>Loading creators…</p> : null}
          {creatorsQuery.isError ? (
            <p className="page-error" role="alert">
              Unable to load creators. Check the backend and try again.
            </p>
          ) : null}
          {creatorsQuery.isSuccess && creatorsQuery.data.length === 0 ? (
            <p className="empty-state">No creators yet. Add the first one above.</p>
          ) : null}
          {creatorsQuery.isSuccess && creatorsQuery.data.length > 0 ? (
            <ul className="creator-list">
              {creatorsQuery.data.map((creator) => (
                <li key={creator.id}>
                  <Link to={`/creators/${creator.id}`}>{creator.name}</Link>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      </section>
    </main>
  )
}
