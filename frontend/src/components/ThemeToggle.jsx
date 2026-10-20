import { useEffect, useState } from 'react'

// Toggle de modo claro/oscuro: escribe data-theme en <html>, persiste la
// preferencia en localStorage y arranca desde la preferencia guardada.
// index.html aplica el tema temprano para evitar el destello de tema incorrecto.
const STORAGE_KEY = 'amano-theme'

function SunIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 7.5A4.5 4.5 0 1 0 12 16.5 4.5 4.5 0 0 0 12 7.5ZM12 2h.75v3H12V2Zm0 17h.75v3H12v-3Zm10-10v.75h-3V9h3Zm0 5v.75h-3-.75v-.75.75h3ZM5.64 4.22l.53.53 2.12 2.12-.53.53-2.65-2.65.53-.53ZM15.71 16.7l.53-.53 2.65 2.65-.53.53-2.65-2.65ZM2 12v-.75h3V12H2Zm19.65 5.35-.53-.53-.53.53.53.53.53-.53ZM4.42 18.82l.53.53 2.12-2.12-.53-.53-2.12 2.12Zm15.16-13.64-.53-.53-2.12 2.12.53.53 2.12-2.12ZM20 11.25v1.5h-1.5v-1.5H20ZM4 11.25v1.5H5.5v-1.5H4Z" />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12.3 3a9 9 0 1 0 9.9 9.9A6 6 0 0 1 12.3 3Z" />
    </svg>
  )
}

export default function ThemeToggle() {
  const [theme, setTheme] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light'
    } catch {
      return 'light'
    }
  })

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      // Modo privado/almacenamiento bloqueado: no persistir es aceptable.
    }
  }, [theme])

  const isDark = theme === 'dark'

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={() => setTheme(isDark ? 'light' : 'dark')}
      aria-pressed={isDark}
      aria-label={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
    >
      {isDark ? <SunIcon /> : <MoonIcon />}
      {isDark ? 'Modo claro' : 'Modo oscuro'}
    </button>
  )
}
