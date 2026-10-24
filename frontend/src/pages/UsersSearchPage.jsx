import { useRef, useState } from 'react'
import TextField from '../components/TextField'
import { SEARCH_NETWORK_ERROR_MESSAGE, searchUsers } from '../api/usersClient'
import { useAuth } from '../auth/AuthContext'

// Exact messages of the spec (FR-009, FR-017), exported for the tests.
export const TOO_SHORT_MESSAGE = 'Ingresá al menos 2 caracteres para buscar'
export const NO_RESULTS_MESSAGE = 'No se encontraron usuarios'

const MIN_LENGTH = 2

function fullName(user) {
  return user.lastName ? `${user.firstName} ${user.lastName}` : user.firstName
}

// User search screen (feature 008). Searches only on submit (button or Enter,
// FR-001b). Outcomes:
//  - < 2 chars after trimming → notice, no request (FR-004)
//  - 200 → list (or "No se encontraron usuarios")
//  - 400 → the server's field message
//  - 401 → session cleared; ProtectedRoute sends the user to /login
//  - network/5xx → fixed retry message (FR-010)
// Previous results are always cleared on errors, and only the latest request's
// answer is shown (out-of-order answers are ignored).
export default function UsersSearchPage() {
  const { setUser } = useAuth()
  const [term, setTerm] = useState('')
  const [results, setResults] = useState([])
  const [notice, setNotice] = useState(null)
  const latestRequest = useRef(0)

  const handleSubmit = async (event) => {
    event.preventDefault()
    const trimmed = term.trim()

    if (trimmed.length < MIN_LENGTH) {
      setResults([])
      setNotice({ text: TOO_SHORT_MESSAGE, role: 'alert' })
      return
    }

    latestRequest.current += 1
    const requestId = latestRequest.current
    setNotice(null)

    const result = await searchUsers(trimmed)
    if (requestId !== latestRequest.current) {
      return // a newer search was started meanwhile
    }

    if (result.ok) {
      setResults(result.users)
      setNotice(result.users.length === 0 ? { text: NO_RESULTS_MESSAGE, role: 'status' } : null)
      return
    }

    setResults([])
    if (result.status === 401) {
      setUser(null)
      return
    }
    if (result.status === 400 && result.fields?.q) {
      setNotice({ text: result.fields.q, role: 'alert' })
      return
    }
    setNotice({ text: SEARCH_NETWORK_ERROR_MESSAGE, role: 'alert' })
  }

  return (
    <main>
      <h1>Buscar usuarios</h1>
      <form onSubmit={handleSubmit} noValidate role="search">
        <TextField
          label="Nombre, apellido o email"
          type="search"
          autoComplete="off"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
        />
        <button type="submit">Buscar</button>
      </form>

      {notice && (
        <p role={notice.role} data-testid="search-notice">
          {notice.text}
        </p>
      )}

      {results.length > 0 && (
        <ul aria-label="Resultados de la búsqueda">
          {results.map((user) => (
            <li key={user.id}>
              <img src="/avatar-default.png" alt="" width={40} height={40} />
              <span>{fullName(user)}</span> <span>{user.email}</span>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
