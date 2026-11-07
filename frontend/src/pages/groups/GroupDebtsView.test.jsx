import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import GroupDebtsView from './GroupDebtsView'

// 022 T003: shared tab skeleton, with the validation queue only for admins.
describe('GroupDebtsView (022)', () => {
  it('muestra las tres pestañas comunes y cambia de panel', async () => {
    const user = userEvent.setup()
    render(<GroupDebtsView />)

    expect(screen.getByRole('button', { name: 'Saldos' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByRole('button', { name: 'Pagos por validar' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Liquidación' }))
    expect(screen.getByTestId('group-debts-panel')).toHaveTextContent('Liquidación')
  })

  it('agrega la pestaña de validación para administradores', () => {
    render(<GroupDebtsView isAdmin />)

    expect(screen.getByRole('button', { name: 'Pagos por validar' })).toBeInTheDocument()
  })
})
