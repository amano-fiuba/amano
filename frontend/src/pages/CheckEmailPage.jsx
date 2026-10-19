import { useState } from 'react'

// "Revisa tu correo" screen (task T023, feature 003: FR-002, FR-010, SC-002).
// The registration no longer starts a session, so after signing up the user
// lands here instead of the dashboard. The normalized address is restored from
// sessionStorage, so reloading the page keeps showing where the link was sent
// (FR-002). The resend button is always enabled (FR-010); wiring it to the
// resend endpoint — with its 5-minute limit — is done in US4.
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
      <button type="button">Reenviar correo de verificación</button>
    </main>
  )
}
