import { useRef, useState } from 'react'
import SubmitButton from '../components/SubmitButton'
import TextField from '../components/TextField'
import { SEARCH_NETWORK_ERROR_MESSAGE, searchUsers } from '../api/usersClient'
import { useAuth } from '../auth/AuthContext'

// Exact messages of the spec (FR-009, FR-017), exported for the tests.
export const TOO_SHORT_MESSAGE = 'Ingresá al menos 2 caracteres para buscar'
export const NO_RESULTS_MESSAGE = 'No se encontraron usuarios'

const MIN_LENGTH = 2
const DEFAULT_AVATAR = '/avatar-default.png'

function fullName(user) {
  return user.lastName ? `${user.firstName} ${user.lastName}` : user.firstName
}

function resultsLabel(count) {
  return count === 1 ? '1 resultado' : `${count} resultados`
}

// Inline icons keep the page self-contained and inherit the palette through
// currentColor (same visual language as the register form controls).
function SearchIcon() {
  return (
    <svg
      className="search-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  )
}

function AlertIcon() {
  return (
    <svg
      className="search-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v5" />
      <path d="M12 16h.01" />
    </svg>
  )
}

function UserPlusIcon() {
  return (
    <svg
      className="search-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M19 8v6" />
      <path d="M22 11h-6" />
    </svg>
  )
}

// User search screen (feature 008). Searches only on submit (button or Enter,
// FR-001b). Outcomes:
//  - < 2 chars after trimming → notice, no request (FR-004)
//  - 200 → list (or "No se encontraron usuarios")
//  - 400 → the server's field message
//  - 401 → session cleared; ProtectedRoute sends the user to /login
//  - network/5xx → fixed retry message (FR-010)
// Previous results are always cleared when a search starts or fails, and only
// the latest request's answer is shown (out-of-order answers are ignored).
export default function UsersSearchPage() {
  const { setUser } = useAuth()
  const [term, setTerm] = useState('')
  const [results, setResults] = useState([])
  const [notice, setNotice] = useState(null)
  const [isSearching, setIsSearching] = useState(false)
  const latestRequest = useRef(0)

  const showError = (text, title) => {
    setNotice({ kind: 'error', text, title })
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const trimmed = term.trim()

    // Every submission invalidates any in-flight request, including an invalid
    // term (otherwise an older answer could overwrite the validation notice).
    latestRequest.current += 1
    const requestId = latestRequest.current

    if (trimmed.length < MIN_LENGTH) {
      setIsSearching(false)
      setResults([])
      showError(TOO_SHORT_MESSAGE, 'Revisá el término de búsqueda')
      return
    }

    setNotice(null)
    setResults([])
    setIsSearching(true)

    let result
    try {
      result = await searchUsers(trimmed)
    } catch {
      // searchUsers already maps failures to {ok:false}; this is a safety net.
      result = { ok: false, network: true }
    }

    if (requestId !== latestRequest.current) {
      return // a newer search was started meanwhile
    }
    setIsSearching(false)

    if (result.ok) {
      if (result.users.length === 0) {
        setNotice({ kind: 'empty', text: NO_RESULTS_MESSAGE })
      } else {
        setResults(result.users)
      }
      return
    }

    if (result.status === 401) {
      setUser(null)
      return
    }
    if (result.status === 400 && result.fields?.q) {
      showError(result.fields.q, 'Revisá el término de búsqueda')
      return
    }
    showError(SEARCH_NETWORK_ERROR_MESSAGE, 'No pudimos completar la búsqueda')
  }

  return (
    <main className="page users-search-page">
      <header className="users-search-page__header">
        <h1>Buscar usuarios</h1>
        <p className="users-search-page__lead">
          Encontrá personas por su nombre, apellido o correo electrónico.
        </p>
      </header>

      <form
        className="users-search-form"
        onSubmit={handleSubmit}
        noValidate
        role="search"
        aria-busy={isSearching || undefined}
      >
        <div className="users-search-form__row">
          <TextField
            label="Nombre, apellido o email"
            type="search"
            autoComplete="off"
            enterKeyHint="search"
            leadingIcon={<SearchIcon />}
            value={term}
            onChange={(event) => setTerm(event.target.value)}
          />
          <SubmitButton
            className="users-search-form__submit"
            loading={isSearching}
            loadingLabel="Buscando…"
          >
            <SearchIcon />
            Buscar
          </SubmitButton>
        </div>
        <p className="users-search-form__hint">
          Buscá por nombre, apellido o correo. Mínimo 2 caracteres.
        </p>
      </form>

      {isSearching && (
        <div className="users-search-loading" role="status" data-testid="search-loading">
          <span className="button-spinner" aria-hidden="true" />
          <p>Buscando usuarios…</p>
        </div>
      )}

      {notice?.kind === 'error' && (
        <div className="users-search-banner" role="alert" data-testid="search-notice">
          <span className="users-search-banner__icon">
            <AlertIcon />
          </span>
          <div>
            <strong>{notice.title}</strong>
            <p>{notice.text}</p>
          </div>
        </div>
      )}

      {notice?.kind === 'empty' && (
        <section className="users-search-empty" role="status" data-testid="search-notice">
          <span className="users-search-empty__icon">
            <SearchIcon />
          </span>
          <h2>{NO_RESULTS_MESSAGE}</h2>
          <p>Probá con otro nombre, apellido o correo.</p>
        </section>
      )}

      {!isSearching && !notice && results.length === 0 && (
        <section
          className="users-search-empty users-search-empty--initial"
          data-testid="search-initial"
        >
          <span className="users-search-empty__icon">
            <SearchIcon />
          </span>
          <h2>Encontrá personas</h2>
          <p>Ingresá un término de búsqueda para ver las coincidencias acá.</p>
        </section>
      )}

      {results.length > 0 && (
        <section className="users-search__results" aria-labelledby="users-search-results-title">
          <div className="users-search__results-header">
            <h2 id="users-search-results-title">Resultados</h2>
            <span className="users-search__count" aria-live="polite">
              {resultsLabel(results.length)}
            </span>
          </div>
          <ul className="user-results" aria-label="Resultados de la búsqueda">
            {results.map((user) => {
              const name = fullName(user)
              return (
                <li key={user.id} className="user-card" data-testid={`user-card-${user.id}`}>
                  <img
                    className="user-card__avatar"
                    src={DEFAULT_AVATAR}
                    alt=""
                    width={44}
                    height={44}
                  />
                  <div className="user-card__identity">
                    <strong>{name}</strong>
                    <span>{user.email}</span>
                  </div>
                  {/* Friend requests are out of scope (FR-013) and have no API. */}
                  <button
                    type="button"
                    className="user-card__action"
                    disabled
                    title="Las solicitudes de amistad estarán disponibles próximamente"
                    aria-label={`Agregar a ${name} (próximamente)`}
                    aria-describedby="users-search-friendship-note"
                  >
                    <UserPlusIcon />
                    Agregar
                  </button>
                </li>
              )
            })}
          </ul>
          <p className="users-search__friendship-note" id="users-search-friendship-note">
            Las solicitudes de amistad estarán disponibles próximamente.
          </p>
        </section>
      )}
    </main>
  )
}
