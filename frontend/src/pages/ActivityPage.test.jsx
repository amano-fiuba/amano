import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'

vi.mock('../api/expenseClient', async (importOriginal) => ({
  ...await importOriginal(),
  searchExpenses: vi.fn(),
  listExpenseYears: vi.fn(),
}))
const setUser = vi.fn()
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ setUser }) }))

import {
  listExpenseYears,
  SEARCH_EXPENSES_ERROR_MESSAGE,
  searchExpenses,
} from '../api/expenseClient'
import ActivityPage, {
  NO_RESULTS_MESSAGE,
  SEARCH_PLACEHOLDER,
  TOO_SHORT_MESSAGE,
  TRUNCATED_MESSAGE,
} from './ActivityPage'

const YEAR = String(new Date().getFullYear())

const PIZZA = {
  id: 1,
  description: 'Pizza',
  category: 'FOOD',
  paymentMethod: 'CASH',
  date: '2026-11-02',
  currency: 'ARS',
  amount: '1500.00',
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function DetailProbe() {
  const location = useLocation()
  return <p>Detalle desde {location.state?.from}</p>
}

function renderPage(entry = '/activity') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/activity"
          element={
            <>
              <ActivityPage />
              <LocationProbe />
            </>
          }
        />
        <Route path="/expenses/:id" element={<DetailProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

const searchbox = () => screen.getByRole('searchbox', { name: SEARCH_PLACEHOLDER })
const yearSelect = () => screen.getByRole('combobox', { name: 'Año' })

beforeEach(() => {
  searchExpenses.mockReset()
  listExpenseYears.mockReset()
  setUser.mockReset()
  searchExpenses.mockResolvedValue({ ok: true, expenses: [PIZZA], truncated: false })
  listExpenseYears.mockResolvedValue({ ok: true, years: [] })
})

describe('ActivityPage (031)', () => {
  it('al entrar muestra los gastos del año actual como tarjetas con enlace al detalle', async () => {
    renderPage()

    expect(await screen.findByText('Pizza')).toBeInTheDocument()
    expect(searchExpenses).toHaveBeenCalledWith('', YEAR)
    expect(yearSelect()).toHaveValue(YEAR)
    expect(searchbox()).toHaveAttribute('placeholder', SEARCH_PLACEHOLDER)
    expect(screen.getByText('ARS 1.500,00')).toBeInTheDocument()
    expect(screen.getByText(/02\/11\/2026 · Alimentos · Efectivo/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Pizza/ })).toHaveAttribute('href', '/expenses/1')
    expect(screen.queryByRole('button', { name: 'Limpiar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Buscar' })).not.toBeInTheDocument()
    expect(screen.queryByText(/resultado/)).not.toBeInTheDocument()
  })

  it('muestra las conversiones y la categoría cuando no hay descripción', async () => {
    searchExpenses.mockResolvedValue({
      ok: true,
      truncated: false,
      expenses: [
        {
          ...PIZZA,
          description: undefined,
          currency: 'USD',
          amount: '100.00',
          conversions: [{ currency: 'ARS', amount: '150000.00' }],
        },
      ],
    })

    renderPage()

    expect(await screen.findByText('≈ ARS 150.000,00')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^Alimentos/ })).toBeInTheDocument()
  })

  it('busca al dejar de escribir, mostrando "Buscando…" mientras tanto', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Pizza')

    await user.type(searchbox(), 'farma')

    expect(screen.getByText('Buscando…')).toBeInTheDocument()
    await waitFor(() => expect(searchExpenses).toHaveBeenLastCalledWith('farma', YEAR))
    expect(await screen.findByText('1 resultado')).toBeInTheDocument()
    expect(searchExpenses).not.toHaveBeenCalledWith('f', YEAR)
    expect(screen.getByTestId('location')).toHaveTextContent('/activity?q=farma')
  })

  it('Enter busca sin esperar la pausa', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Pizza')

    await user.type(searchbox(), 'pizza{Enter}')

    await waitFor(() => expect(searchExpenses).toHaveBeenLastCalledWith('pizza', YEAR), {
      timeout: 200,
    })
  })

  it('con un solo carácter avisa el mínimo y no busca por texto', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Pizza')

    await user.type(searchbox(), ' f {Enter}')

    expect(screen.getByText(TOO_SHORT_MESSAGE)).toBeInTheDocument()
    expect(searchExpenses).toHaveBeenCalledTimes(1)
  })

  it('informa cuando no hay coincidencias', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Pizza')
    searchExpenses.mockResolvedValue({ ok: true, expenses: [], truncated: false })

    await user.type(searchbox(), 'xyz{Enter}')

    expect(await screen.findByText(NO_RESULTS_MESSAGE)).toBeInTheDocument()
  })

  it('borrar el texto muestra de inmediato el año elegido', async () => {
    const user = userEvent.setup()
    renderPage(`/activity?q=pizza&year=2025`)
    await screen.findByText('1 resultado')
    expect(searchbox()).toHaveValue('pizza')
    expect(yearSelect()).toHaveValue('2025')

    await user.clear(searchbox())

    await waitFor(() => expect(searchExpenses).toHaveBeenLastCalledWith('', '2025'), {
      timeout: 200,
    })
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/activity\?year=2025$/)
    await waitFor(() => expect(screen.queryByText('1 resultado')).not.toBeInTheDocument())
  })

  it('cambiar el año consulta de inmediato, con o sin texto', async () => {
    listExpenseYears.mockResolvedValue({ ok: true, years: [2024] })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Pizza')
    const options = within(yearSelect()).getAllByRole('option').map((option) => option.textContent)
    expect(options).toEqual([YEAR, '2024', 'Todos los años'])

    await user.selectOptions(yearSelect(), '2024')
    expect(searchExpenses).toHaveBeenLastCalledWith('', '2024')

    await user.selectOptions(yearSelect(), 'all')
    expect(searchExpenses).toHaveBeenLastCalledWith('', null)

    await user.type(searchbox(), 'pizza{Enter}')
    await user.selectOptions(yearSelect(), YEAR)
    await waitFor(() => expect(searchExpenses).toHaveBeenLastCalledWith('pizza', YEAR))
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/activity\?q=pizza$/)
  })

  it('un año inválido en la dirección se toma como el actual y uno sin gastos se ofrece igual', async () => {
    renderPage('/activity?q=pizza&year=20x5')
    await screen.findByText('Pizza')
    expect(searchExpenses).toHaveBeenCalledWith('pizza', YEAR)
  })

  it('ofrece el año de la dirección aunque no tenga gastos', async () => {
    renderPage('/activity?year=2019')
    await screen.findByText('Pizza')
    expect(yearSelect()).toHaveValue('2019')
  })

  it('avisa cuando hay más de 200 resultados', async () => {
    searchExpenses.mockResolvedValue({ ok: true, expenses: [PIZZA], truncated: true })

    renderPage('/activity?q=pizza')

    expect(await screen.findByText(TRUNCATED_MESSAGE)).toBeInTheDocument()
  })

  it('sin gastos en el año ofrece registrar uno', async () => {
    searchExpenses.mockResolvedValue({ ok: true, expenses: [], truncated: false })

    renderPage()

    expect(await screen.findByText(`No registraste gastos en ${YEAR}.`)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Registrar un gasto' })).toHaveAttribute(
      'href',
      '/expenses/new',
    )
  })

  it('sin gastos en ningún año lo dice así', async () => {
    searchExpenses.mockResolvedValue({ ok: true, expenses: [], truncated: false })

    renderPage('/activity?year=all')

    expect(await screen.findByText('Todavía no registraste gastos.')).toBeInTheDocument()
  })

  it('ante un error permite reintentar', async () => {
    searchExpenses.mockResolvedValueOnce({ ok: false, network: true })
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText(SEARCH_EXPENSES_ERROR_MESSAGE)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByText('Pizza')).toBeInTheDocument()
    expect(searchExpenses).toHaveBeenCalledTimes(2)
  })

  it('una sesión vencida cierra la sesión', async () => {
    searchExpenses.mockResolvedValue({ ok: false, status: 401 })

    renderPage()

    await waitFor(() => expect(setUser).toHaveBeenCalledWith(null))
  })

  it('si los años no cargan, ofrece el actual y todos', async () => {
    listExpenseYears.mockResolvedValue({ ok: false, network: true })

    renderPage()
    await screen.findByText('Pizza')

    const options = within(yearSelect()).getAllByRole('option').map((option) => option.textContent)
    expect(options).toEqual([YEAR, 'Todos los años'])
  })

  it('el detalle recibe la dirección de la búsqueda para volver', async () => {
    const user = userEvent.setup()
    renderPage('/activity?q=pizza&year=2025')

    await user.click(await screen.findByRole('link', { name: /Pizza/ }))

    expect(screen.getByText('Detalle desde /activity?q=pizza&year=2025')).toBeInTheDocument()
  })
})
