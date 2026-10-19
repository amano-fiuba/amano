// Expired-link screen (task T035, feature 003, FR-006): the 24 h window of the
// token passed, so the page explains it and offers the resend screen. The form
// is visible but its request is wired in US4 (task T053), which is why the
// submit is a no-op for now.
export default function ExpiredLinkPage() {
  return (
    <main>
      <h1>El enlace expiró</h1>
      <p>
        Tu enlace de verificación ha expirado. Ingresa tu correo electrónico y te
        enviaremos uno nuevo.
      </p>
      <form onSubmit={(event) => event.preventDefault()}>
        <label htmlFor="expired-email">Correo electrónico</label>
        <input id="expired-email" name="email" type="email" autoComplete="email" />
        <button type="submit">Reenviar correo de verificación</button>
      </form>
    </main>
  )
}
