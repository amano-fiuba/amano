import PendingRequestsPanel from '../components/main/PendingRequestsPanel'
import PersonalActivity from '../components/main/PersonalActivity'
import ProfileSummary from '../components/main/ProfileSummary'
import RecentGroups from '../components/main/RecentGroups'
import './MainPage.scss'

// Pantalla principal con sesión (specs/015-main-screen, US1). Cada sección carga
// sus datos de forma independiente: un error no afecta a las demás.
export default function MainPage() {
  return (
    <main className="main-page">
      <h1 className="main-page__title">Inicio</h1>
      <ProfileSummary />
      <RecentGroups />
      <div className="main-page__side">
        <PersonalActivity />
        <PendingRequestsPanel />
      </div>
    </main>
  )
}
