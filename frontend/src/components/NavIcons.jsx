// Íconos de línea de la barra superior: heredan el color con currentColor.
function Icon({ children }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  )
}

export function CameraIcon() {
  return (
    <Icon>
      <path d="M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Z" />
      <circle cx="12" cy="13.5" r="3.5" />
    </Icon>
  )
}

export function PencilIcon() {
  return (
    <Icon>
      <path d="M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17v3Z" />
      <path d="m14.5 7.5 3 3" />
    </Icon>
  )
}

export function FriendsIcon() {
  return (
    <Icon>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20c.6-3.6 3-5.5 6.5-5.5s5.9 1.9 6.5 5.5" />
      <path d="M15.5 4.8a3.5 3.5 0 0 1 0 6.4M18 14.8c2 .7 3.2 2.5 3.5 5.2" />
    </Icon>
  )
}

export function GroupsIcon() {
  return (
    <Icon>
      <circle cx="12" cy="7.5" r="3.5" />
      <circle cx="5.5" cy="10" r="2.25" />
      <circle cx="18.5" cy="10" r="2.25" />
      <path d="M6 20c.4-3.6 2.6-5.5 6-5.5s5.6 1.9 6 5.5" />
      <path d="M1.5 18c.2-2.2 1.3-3.6 3-4M22.5 18c-.2-2.2-1.3-3.6-3-4" />
    </Icon>
  )
}

export function ActivityIcon() {
  return (
    <Icon>
      <path d="M5 20v-8M12 20V4M19 20v-5" />
    </Icon>
  )
}

export function BellIcon() {
  return (
    <Icon>
      <path d="M6 9a6 6 0 1 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15 6 9" />
      <path d="M10 20.5a2.2 2.2 0 0 0 4 0" />
    </Icon>
  )
}

export function HelpIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9.3a2.6 2.6 0 0 1 5 .9c0 1.7-2.5 2.2-2.5 3.8" />
      <path d="M12 17.2h.01" />
    </Icon>
  )
}

export function SettingsIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.9 2.9l-.1-.1a1.7 1.7 0 0 0-2.8 1.2v.2a2 2 0 1 1-4 0V21a1.7 1.7 0 0 0-2.9-1.2l-.1.1a2 2 0 1 1-2.9-2.9l.1-.1A1.7 1.7 0 0 0 3 14H2.8a2 2 0 1 1 0-4H3a1.7 1.7 0 0 0 1.2-2.9l-.1-.1A2 2 0 1 1 7 4.1l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5v-.2a2 2 0 1 1 4 0V3a1.7 1.7 0 0 0 2.9 1.2l.1-.1A2 2 0 1 1 19.9 7l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1h.2a2 2 0 1 1 0 4H21a1.7 1.7 0 0 0-1.6 1Z" />
    </Icon>
  )
}

export function PlusIcon() {
  return (
    <Icon>
      <path d="M12 5v14M5 12h14" />
    </Icon>
  )
}

export function SearchIcon() {
  return (
    <Icon>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </Icon>
  )
}

// Billete: marca a quien pagó un gasto.
export function PaidIcon() {
  return (
    <Icon>
      <rect x="2.5" y="6" width="19" height="12" rx="2" />
      <circle cx="12" cy="12" r="2.5" />
      <path d="M6 9.5v.01M18 14.5v.01" />
    </Icon>
  )
}

// Una persona: gasto personal (027, submenú "Registrar gasto").
export function PersonIcon() {
  return (
    <Icon>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c.6-3.8 3.1-5.8 7-5.8s6.4 2 7 5.8" />
    </Icon>
  )
}

// Dos personas: gasto con un amigo (027). El gasto grupal usa GroupsIcon.
export function FriendIcon() {
  return (
    <Icon>
      <circle cx="8" cy="8.5" r="3" />
      <circle cx="16" cy="8.5" r="3" />
      <path d="M2.5 19.5c.5-3.2 2.6-5 5.5-5 1.6 0 2.9.5 3.9 1.5" />
      <path d="M21.5 19.5c-.5-3.2-2.6-5-5.5-5-1.6 0-2.9.5-3.9 1.5" />
    </Icon>
  )
}

// Flecha hacia la derecha: ítem que abre un submenú.
export function ChevronRightIcon() {
  return (
    <Icon>
      <path d="m9 6 6 6-6 6" />
    </Icon>
  )
}

// Flecha hacia la izquierda: volver del submenú en móvil.
export function ChevronLeftIcon() {
  return (
    <Icon>
      <path d="m15 6-6 6 6 6" />
    </Icon>
  )
}
