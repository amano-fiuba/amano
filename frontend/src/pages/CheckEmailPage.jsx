import { useState } from 'react'
import ResendCountdown from '../components/ResendCountdown'

// "Revisa tu correo" screen (task T023 + T052, feature 003: FR-002, FR-010, FR-012).
// The registration no longer starts a session, so after signing up the user lands
// here instead of the dashboard. The normalized address is restored from
// sessionStorage, so reloading the page keeps showing where the link was sent
// (FR-002). US4 wires the resend control: the button is always enabled and the
// neutral message plus the countdown come from the server's remainingSeconds.
export const CHECK_EMAIL_STORAGE_KEY = 'amano.check-email'

export default function CheckEmailPage() {
  const [email] = useState(
    () => sessionStorage.getItem(CHECK_EMAIL_STORAGE_KEY) ?? '',
  )

  return (
    <main>
      <h1>Revisa tu correo</h1>
      <p>
        {email
          ? `Enviamos un enlace de verificación a ${email}. Haz clic en el enlace para verificar tu cuenta y poder iniciar sesión.`
          : 'Enviamos un enlace de verificación a tu correo electrónico. Haz clic en el enlace para verificar tu cuenta y poder iniciar sesión.'}
      </p>
      <ResendCountdown email={email} />
    </main>
  )
}
