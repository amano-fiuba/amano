import { BrowserRouter, Route, Routes } from 'react-router'
import { useState } from 'react'
import heroImg from './assets/hero.png'
import reactLogo from './assets/react.svg'
import viteLogo from './assets/vite.svg'
import { AuthProvider } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import './App.css'

// Landing provisoria (equipo base); remplazada por las pantallas definitivas
// conforme avance el feature de registro.
function Home() {
  const [count, setCount] = useState(0)

  return (
    <>
      <section id="center">
        <div className="hero">
          <img src={heroImg} className="base" width="170" height="179" alt="" />
          <img src={reactLogo} className="framework" alt="React logo" />
          <img src={viteLogo} className="vite" alt="Vite logo" />
        </div>
        <div>
          <h1>Get started</h1>
          <p>
            Edit <code>src/App.jsx</code> and save to test <code>HMR</code>
          </p>
        </div>
        <button
          type="button"
          className="counter"
          onClick={() => setCount((count) => count + 1)}
        >
          Count is {count}
        </button>
      </section>

      <div className="ticks"></div>

      <section id="next-steps">
        <div id="docs">
          <svg className="icon" role="presentation" aria-hidden="true">
            <use href="/icons.svg#documentation-icon"></use>
          </svg>
          <h2>Documentation</h2>
          <p>Your questions, answered</p>
          <ul>
            <li>
              <a href="https://vite.dev/" target="_blank" rel="noreferrer">
                <img className="logo" src={viteLogo} alt="Vite" />
                Explore Vite
              </a>
            </li>
            <li>
              <a href="https://react.dev" target="_blank" rel="noreferrer">
                <img
                  className="button-icon"
                  src={reactLogo}
                  alt="React"
                />
              </a>
            </li>
            <li>
              <a href="https://vitejs.dev" target="_blank" rel="noreferrer">
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#vite-icon"></use>
                </svg>
              </a>
            </li>
          </ul>
        </div>

        <div id="social">
          <svg className="icon" role="presentation" aria-hidden="true">
            <use href="/icons.svg#social-icon"></use>
          </svg>
          <h2>Social</h2>
          <p>Stay in touch</p>
          <ul>
            <li>
              <a href="https://github.com/vitejs/vite" target="_blank" rel="noreferrer">
                <img
                  className="button-icon"
                  src="/icons.svg#github-icon"
                  alt="GitHub"
                />
              </a>
            </li>
            <li>
              <a href="https://discord.gg/vite" target="_blank" rel="noreferrer">
                <img
                  className="button-icon"
                  src="/icons.svg#discord-icon"
                  alt="Discord"
                />
              </a>
            </li>
            <li>
              <a href="https://x.com/vite_js" target="_blank" rel="noreferrer">
                <svg
                  className="button-icon"
                  role="presentation"
                  aria-hidden="true"
                >
                  <use href="/icons.svg#x-icon"></use>
                </svg>
              </a>
            </li>
          </ul>
        </div>
      </section>

      <div className="ticks"></div>
      <section id="spacer"></section>
    </>
  )
}

// Rutas base del shell de la SPA (task T015): '/', '/register' y '/dashboard'
// (pantalla protegida). Las pantallas concretas llegan con cada historia
// (RegisterPage/DashboardPage en US1); se colocan placeholders vacíos.
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      {/* Placeholder de US1 (RegisterPage, T023). */}
      <Route path="/register" element={<div />} />
      {/* Placeholder de US1 (DashboardPage, T024), protegido por sesión. */}
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <div />
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}
