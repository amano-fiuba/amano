import { useState } from 'react'
import { addMember, searchCandidates, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'

const CANNOT_INVITE_MESSAGE = 'No podés invitar gente a este grupo'

export default function AddMemberView({ group, permissions }) {
  const [mode, setMode] = useState('REGISTERED')
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [searched, setSearched] = useState(false)
  const [profileName, setProfileName] = useState('')
  const [profileEmail, setProfileEmail] = useState('')
  const [formError, setFormError] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')

  const canInvite = !permissions?.membersCannotInvite

  async function handleSearch() {
    setFormError('')
    setError('')
    if (query.trim().length < 2) {
      setFormError('Ingresá al menos 2 caracteres para buscar')
      return
    }
    const result = await searchCandidates(group.id, query.trim())
    if (result.ok) {
      setResults(result.content)
      setSearched(true)
    } else if (result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
    } else {
      setFormError(result.detail || CANNOT_INVITE_MESSAGE)
    }
  }

  async function addRegistered(candidate) {
    setConfirmation('')
    setError('')
    const result = await addMember(group.id, {
      memberType: 'REGISTERED',
      identifier: candidate.email,
    })
    if (!result || result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
      return
    }
    if (result.ok) {
      setConfirmation(`Miembro agregado: ${candidate.email}`)
      setResults([])
      setSearched(false)
    } else {
      setError(result.detail || 'No se pudo agregar al miembro')
    }
  }

  async function addSimulated(event) {
    event.preventDefault()
    setConfirmation('')
    setError('')
    if (!profileName.trim() || !profileEmail.trim()) {
      setFormError('Ingresá nombre y correo del perfil simulado')
      return
    }
    const result = await addMember(group.id, {
      memberType: 'SIMULATED',
      identifier: profileEmail.trim(),
      name: profileName.trim(),
    })
    if (!result || result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
      return
    }
    if (result.ok) {
      setConfirmation(`Miembro agregado: ${profileName.trim()}`)
      setProfileName('')
      setProfileEmail('')
      setFormError('')
    } else {
      setError(result.detail || 'No se pudo agregar al miembro')
    }
  }

  return (
    <main className="page">
      <h1>Agregar miembros</h1>
      {!canInvite && <p role="alert">{CANNOT_INVITE_MESSAGE}</p>}
      {confirmation && <p role="status">{confirmation}</p>}
      {error && (
        <p role="alert" data-testid="add-member-error">
          {error}
        </p>
      )}
      {formError && (
        <p role="alert" data-testid="add-member-form-error">
          {formError}
        </p>
      )}

      <div role="radiogroup" aria-label="Tipo de miembro">
        <label>
          <input
            type="radio"
            name="memberType"
            checked={mode === 'REGISTERED'}
            onChange={() => setMode('REGISTERED')}
          />{' '}
          Amigo registrado
        </label>
        <label>
          <input
            type="radio"
            name="memberType"
            checked={mode === 'SIMULATED'}
            onChange={() => setMode('SIMULATED')}
          />{' '}
          Perfil simulado
        </label>
      </div>

      {mode === 'REGISTERED' && (
        <section data-testid="friend-search">
          <label htmlFor="friend-query">Buscar entre tus amigos</label>
          <input
            id="friend-query"
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button type="button" onClick={handleSearch} disabled={!canInvite}>
            Buscar
          </button>
          {searched && results.length === 0 && <p>No encontraste amigos con esa búsqueda</p>}
          {results.map((candidate) => (
            <div key={candidate.id}>
              <span>
                {candidate.firstName} {candidate.lastName} · {candidate.email}
              </span>
              <button type="button" onClick={() => addRegistered(candidate)} disabled={!canInvite}>
                Agregar a {candidate.email}
              </button>
            </div>
          ))}
        </section>
      )}

      {mode === 'SIMULATED' && (
        <section data-testid="simulated-form">
          <label htmlFor="simulated-name">Nombre del perfil simulado</label>
          <input
            id="simulated-name"
            type="text"
            value={profileName}
            onChange={(event) => setProfileName(event.target.value)}
          />
          <label htmlFor="simulated-email">Correo del perfil simulado</label>
          <input
            id="simulated-email"
            type="text"
            value={profileEmail}
            onChange={(event) => setProfileEmail(event.target.value)}
          />
          <button type="button" onClick={addSimulated} disabled={!canInvite}>
            Agregar perfil simulado
          </button>
        </section>
      )}
    </main>
  )
}
