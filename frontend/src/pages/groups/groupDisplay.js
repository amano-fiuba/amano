// Presentación de un grupo compartida entre "Mis grupos" y la pantalla principal.

export function byLatestActivity(groups) {
  return [...groups].sort((a, b) => Date.parse(b.lastActivityAt) - Date.parse(a.lastActivityAt))
}

export function formatActivityDate(isoDateTime) {
  return new Date(isoDateTime).toLocaleDateString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

export function memberCountLabel(count) {
  return count === 1 ? '1 miembro' : `${count} miembros`
}
