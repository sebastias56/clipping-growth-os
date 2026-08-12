export type ServiceStatus = 'UP' | 'DOWN'
export type AggregateStatus = 'UP' | 'DEGRADED'

export interface SystemStatusResponse {
  status: AggregateStatus
  backend: {
    status: ServiceStatus
  }
  mediaWorker: {
    status: ServiceStatus
  }
}

export async function getSystemStatus(): Promise<SystemStatusResponse> {
  const response = await fetch('/api/system/status', {
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    throw new Error('The backend status request failed')
  }

  return (await response.json()) as SystemStatusResponse
}
