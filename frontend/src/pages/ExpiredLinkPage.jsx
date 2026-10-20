import { useState } from 'react'
import ResendCountdown from '../components/ResendCountdown'

// Expired-link screen (task T035 + T053, feature 003, FR-006/FR-012): the 24 h
// window of the token passed, so the page explains it and offers the resend
// flow. The user types the address and submitting it already is the request:
// ResendCountdown (autoResend) shows the neutral message and the 5-minute
// countdown that the server dictates (FR-013/SC-006).
export default function ExpiredLinkPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)

  return (
    <main>
      <h1>El enlace expiró</h1>
      <p>
        Tu enlace de verificación ha expirado. Ingresa tu correo electrónico y te
        enviaremos uno nuevo.
      </p>
      {submitted ? (
        <ResendCountdown email={email.trim().toLowerCase()} autoResend />
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            setSubmitted(true)
          }}
        >
          <label htmlFor="expired-email">Correo electrónico</label>
          <input
            id="expired-email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <button type="submit">Reenviar correo de verificación</button>
        </form>
      )}
    </main>
  )
}
