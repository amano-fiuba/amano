import LogoutButton from '../components/LogoutButton'
import ThemeToggle from '../components/ThemeToggle'
import HomeLink from '../components/HomeLink'

// Ajustes mínimos (specs/015-main-screen, FR-009): reúne lo que estaba en la
// barra anterior; acá se sumarán las próximas opciones.
export default function SettingsPage() {
  return (
    <main className="page settings-page">
      <HomeLink />
      <h1>Ajustes</h1>
      <section aria-labelledby="settings-theme-title">
        <h2 id="settings-theme-title">Apariencia</h2>
        <ThemeToggle />
      </section>
      <section aria-labelledby="settings-session-title">
        <h2 id="settings-session-title">Sesión</h2>
        <LogoutButton />
      </section>
    </main>
  )
}
