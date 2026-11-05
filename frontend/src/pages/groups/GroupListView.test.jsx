import { render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  listGroups: vi.fn(),
}))

import { listGroups } from '../../api/groupsClient'
import GroupListView from './GroupListView'

beforeEach(() => {
  listGroups.mockReset()
})

function renderView(entry = '/groups') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <GroupListView />
    </MemoryRouter>,
  )
}

function group(id, overrides = {}) {
  return {
    id,
    name: `Grupo ${id}`,
    description: null,
    photo: null,
    memberCount: 2,
    lastActivityAt: '2026-11-01T12:00:00Z',
    currentUserRole: 'MEMBER',
    ...overrides,
  }
}

describe('GroupListView (T029/T031 read view)', () => {
  it('muestra la bienvenida de cuenta verificada cuando llega con state verifiedWelcome', async () => {
    listGroups.mockResolvedValue({ ok: true, groups: [] })

    render(
      <MemoryRouter
        initialEntries={[
          { pathname: '/groups', state: { verifiedWelcome: true } },
        ]}
      >
        <GroupListView />
      </MemoryRouter>,
    )

    expect(
      await screen.findByTestId('groups-verified-welcome'),
    ).toBeInTheDocument()
  })

  it('no muestra la bienvenida sin state de verificación', async () => {
    listGroups.mockResolvedValue({ ok: true, groups: [] })

    renderView()

    await screen.findByTestId('groups-empty')
    expect(
      screen.queryByTestId('groups-verified-welcome'),
    ).not.toBeInTheDocument()
  })

  it('renderiza los grupos con foto, nombre y rol del usuario', async () => {
    listGroups.mockResolvedValue({
      ok: true,
      groups: [
        group(3, { name: 'Departamento 2026', photo: 'p3.jpg', currentUserRole: 'ADMIN' }),
        group(5, { name: 'Viaje a Mendoza' }),
      ],
    })

    renderView()

    expect(await screen.findByText('Departamento 2026')).toBeInTheDocument()
    expect(screen.getByText('Viaje a Mendoza')).toBeInTheDocument()
    expect(document.querySelector('img')).toHaveAttribute('src', 'p3.jpg')
    expect(screen.getByTestId('group-avatar-default')).toHaveTextContent('V')
    expect(screen.getByTestId('group-role-3')).toHaveTextContent(/Administrador/)
    expect(screen.getByTestId('group-role-5')).toHaveTextContent(/Miembro/)
  })

  it('ordena por actividad más reciente y la fila entera lleva al grupo', async () => {
    listGroups.mockResolvedValue({
      ok: true,
      groups: [
        group(1, { lastActivityAt: '2026-10-01T12:00:00Z' }),
        group(2, { lastActivityAt: '2026-11-01T12:00:00Z' }),
      ],
    })

    renderView()

    const links = within(await screen.findByTestId('groups-list')).getAllByRole('link')
    expect(links.map((link) => link.getAttribute('href'))).toEqual(['/groups/2', '/groups/1'])
    expect(within(links[0]).getByTestId('group-role-2')).toBeInTheDocument()
  })

  it('muestra contador, miembros, última actividad y descripción', async () => {
    listGroups.mockResolvedValue({
      ok: true,
      groups: [
        group(1, { memberCount: 1, description: 'Regalo para Ana' }),
        group(2, { memberCount: 4 }),
      ],
    })

    renderView()

    expect(await screen.findByTestId('groups-count')).toHaveTextContent('2 grupos')
    expect(screen.getByText('Regalo para Ana')).toBeInTheDocument()
    expect(screen.getByText(/^1 miembro · Actividad: 01\/11\/2026$/)).toBeInTheDocument()
    expect(screen.getByText(/^4 miembros · Actividad: 01\/11\/2026$/)).toBeInTheDocument()
  })

  it('muestra el estado vacío con un único botón para crear el primer grupo', async () => {
    listGroups.mockResolvedValue({ ok: true, groups: [] })

    renderView()

    expect(await screen.findByTestId('groups-empty')).toHaveTextContent(
      /Todavía no sos miembro de ningún grupo/,
    )
    expect(screen.getByRole('link', { name: 'Crear mi primer grupo' })).toHaveAttribute(
      'href',
      '/groups/new',
    )
    expect(screen.queryByRole('link', { name: 'Crear nuevo grupo' })).not.toBeInTheDocument()
    expect(screen.queryByTestId('groups-count')).not.toBeInTheDocument()
  })

  it('muestra el estado de carga y luego el error de red', async () => {
    listGroups.mockResolvedValue({ ok: false, network: true })

    renderView()
    expect(screen.getByText(/Cargando…/)).toBeInTheDocument()

    expect(await screen.findByTestId('groups-error')).toHaveTextContent(
      /No se pudo conectar con el servidor/,
    )
    expect(screen.queryByTestId('groups-empty')).not.toBeInTheDocument()
  })

  it('el error 401 también se muestra como error de conexión con el texto del client', async () => {
    listGroups.mockResolvedValue({ ok: false, status: 401, fields: { member: 'sin sesión' } })

    renderView()

    await waitFor(() =>
      expect(screen.getByTestId('groups-error')).toHaveTextContent(
        /No se pudo conectar con el servidor/,
      ),
    )
  })

  it('mantiene el enlace para crear un nuevo grupo', async () => {
    listGroups.mockResolvedValue({ ok: true, groups: [group(1)] })

    renderView()

    expect(await screen.findByRole('link', { name: /Crear nuevo grupo/i })).toHaveAttribute(
      'href',
      '/groups/new',
    )
  })
})
