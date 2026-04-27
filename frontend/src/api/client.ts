export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(path, { signal, headers: { Accept: 'application/json' } })
  if (!res.ok) {
    throw new ApiError(res.status, `${res.status} ${res.statusText} for ${path}`)
  }
  return res.json() as Promise<T>
}
