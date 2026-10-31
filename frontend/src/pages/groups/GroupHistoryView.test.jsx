import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  getActivity: vi.fn(),
}))

import { getActivity } from '../../api/groupsClient'
import GroupHistoryView from './GroupHistoryView'

beforeEach(() => {
  getActivity.mockReset()
})

describe('GroupHistoryView (T054/T051)', () => {
  it('lista las entradas con actor, fecha humanizada y descripción del evento', async () => {
    getActivity.mockResolvedValue({
      ok: true,
      content: [
        {
          id: 2,
          eventType: 'PERMISSION_CHANGED',
          actor: { id: 7, firstName: 'Ana', lastName: 'López' },
          createdAt: '2026-10-27T10:00:00Z',
          details: null,
        },
        {
          id: 1,
          eventType: 'MEMBER_ADDED',
          actor: { id: 7, firstName: 'Ana', lastName: 'López' },
          createdAt: '2026-10-20T08:00:00Z',
          details: null,
        },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 1,
      size: 20,
    })

    render(<GroupHistoryView groupId={42} />)

    await waitFor(() => expect(screen.getAllByTestId('activity-entry')).toHaveLength(2))
    const entries = screen.getAllByTestId('activity-entry')
    expect(entries[0]).toHaveTextContent(/Ana López/)
    expect(entries[0]).toHaveTextContent(/Cambió los permisos del grupo/i)
    expect(entries[1]).toHaveTextContent(/Agregó un miembro/i)
    expect(entries[0].textContent).toMatch(/27|oct/i)

    expect(screen.getByText(/Los gastos y pagos se registrarán cuando exista la integración/i))
      .toBeInTheDocument()
  })

  it('avisa cuando todavía no hay entradas', async () => {
    getActivity.mockResolvedValue({
      ok: true, entries: [], totalElements: 0, totalPages: 1, page: 1, size: 20,
    })

    render(<GroupHistoryView groupId={42} />)

    expect(await screen.findByRole('status')).toHaveTextContent(
      /Todavía no hay actividad en este grupo/i,
    )
  })
})
