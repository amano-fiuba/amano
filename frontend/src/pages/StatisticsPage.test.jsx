import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'

vi.mock('../api/expenseClient', async (importOriginal) => ({
  ...(await importOriginal()),
  getExpenseStatistics: vi.fn(),
}))
const setUser = vi.fn()
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ setUser }) }))

import { getExpenseStatistics, STATISTICS_ERROR_MESSAGE } from '../api/expenseClient'
import { MONTH_NAMES } from '../validation/months'
import StatisticsPage from './StatisticsPage'

const NOW = new Date()
const YEAR = String(NOW.getFullYear())
const MONTH = String(NOW.getMonth() + 1)
const PERIOD = `${MONTH_NAMES[NOW.getMonth()].toLowerCase()} de ${YEAR}`

const EMPTY = { total: '0.00', categories: [] }

function statistics(overrides = {}) {
  return {
    ok: true,
    statistics: {
      currency: 'ARS',
      total: '160000.00',
      excludedCount: 0,
      personal: {
        total: '100000.00',
        categories: [
          { category: 'HOUSING', amount: '45000.00', percentage: '45.0' },
          { category: 'FOOD', amount: '35000.00', percentage: '35.0' },
          { category: 'TRANSPORT', amount: '20000.00', percentage: '20.0' },
        ],
      },
      group: {
        total: '60000.00',
        categories: [
          { category: 'FOOD', amount: '30000.00', percentage: '50.0' },
          { category: 'LEISURE', amount: '30000.00', percentage: '50.0' },
        ],
      },
      years: [2024],
      ...overrides,
    },
  }
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderPage(entry = '/statistics') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/statistics"
          element={
            <>
              <StatisticsPage />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

const monthSelect = () => screen.getByRole('combobox', { name: 'Mes' })
const yearSelect = () => screen.getByRole('combobox', { name: 'Año' })
const location = () => screen.getByTestId('location')
const legend = (name) => screen.getByRole('list', { name: `Categorías de ${name}` })
const rowsOf = (name) =>
  within(legend(name))
    .getAllByRole('listitem')
    .map((row) => row.textContent)

beforeEach(() => {
  getExpenseStatistics.mockReset()
  setUser.mockReset()
  getExpenseStatistics.mockResolvedValue(statistics())
})

describe('StatisticsPage (032)', () => {
  it('al entrar muestra el mes actual con el total, la moneda y las dos tortas', async () => {
    renderPage()

    expect(screen.getByText('Cargando…')).toBeInTheDocument()
    expect(await screen.findByText('ARS 160.000,00')).toBeInTheDocument()
    expect(getExpenseStatistics).toHaveBeenCalledWith({ year: YEAR, month: MONTH })
    expect(monthSelect()).toHaveValue(MONTH)
    expect(yearSelect()).toHaveValue(YEAR)
    expect(screen.getByText(`Total de ${PERIOD}`)).toBeInTheDocument()
    expect(screen.getByText('Importes en Peso argentino (ARS)')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Personales' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Grupales' })).toBeInTheDocument()
    expect(screen.getByText('ARS 100.000,00')).toBeInTheDocument()
    expect(screen.getByText('ARS 60.000,00')).toBeInTheDocument()
    expect(rowsOf('personales')).toEqual([
      'ViviendaARS 45.000,0045,0 %',
      'AlimentosARS 35.000,0035,0 %',
      'TransporteARS 20.000,0020,0 %',
    ])
    expect(rowsOf('grupales')).toEqual(['AlimentosARS 30.000,0050,0 %', 'OcioARS 30.000,0050,0 %'])
    expect(screen.getByRole('group', { name: /^Personales: Vivienda · ARS 45.000,00 · 45,0 %/ }))
      .toBeInTheDocument()
    expect(screen.queryByText(/no se pud/)).not.toBeInTheDocument()
  })

  it('cada categoría tiene el mismo color en las dos tortas y en la lista', async () => {
    const { container } = renderPage()
    await screen.findByText('ARS 160.000,00')

    const fills = [...container.querySelectorAll('path')].map((path) => path.getAttribute('fill'))
    expect(fills).toEqual([
      'var(--color-categoria-housing)',
      'var(--color-categoria-food)',
      'var(--color-categoria-transport)',
      'var(--color-categoria-food)',
      'var(--color-categoria-leisure)',
    ])
    const swatch = legend('personales').querySelector('.statistics-legend__swatch')
    expect(swatch.getAttribute('style')).toContain('var(--color-categoria-housing)')
  })

  it('ofrece el año actual, los años con gastos y el de la dirección, de más reciente a más antiguo', async () => {
    renderPage('/statistics?year=2019&month=3')
    await screen.findByText('ARS 160.000,00')

    expect(getExpenseStatistics).toHaveBeenCalledWith({ year: '2019', month: '3' })
    const options = within(yearSelect()).getAllByRole('option').map((option) => option.textContent)
    expect(options).toEqual([YEAR, '2024', '2019'])
    expect(within(monthSelect()).getAllByRole('option')).toHaveLength(12)
    expect(monthSelect()).toHaveValue('3')
  })

  it('cambiar el mes o el año consulta de inmediato y guarda el período en la dirección', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('ARS 160.000,00')
    const otherMonth = MONTH === '10' ? '9' : '10'

    await user.selectOptions(monthSelect(), otherMonth)
    await waitFor(() =>
      expect(getExpenseStatistics).toHaveBeenLastCalledWith({ year: YEAR, month: otherMonth }))
    expect(location()).toHaveTextContent(new RegExp(`^/statistics\\?month=${otherMonth}$`))

    await screen.findByText('ARS 160.000,00')
    await user.selectOptions(yearSelect(), '2024')
    await waitFor(() =>
      expect(getExpenseStatistics).toHaveBeenLastCalledWith({ year: '2024', month: otherMonth }))
    expect(location()).toHaveTextContent(new RegExp(`^/statistics\\?year=2024&month=${otherMonth}$`))

    await screen.findByText('ARS 160.000,00')
    await user.selectOptions(yearSelect(), YEAR)
    await user.selectOptions(monthSelect(), MONTH)
    await waitFor(() => expect(location()).toHaveTextContent(/^\/statistics$/))
  })

  it('un mes o un año inválido en la dirección se toma como el actual', async () => {
    renderPage('/statistics?year=20x5&month=13')
    await screen.findByText('ARS 160.000,00')

    expect(getExpenseStatistics).toHaveBeenCalledWith({ year: YEAR, month: MONTH })
  })

  it('solo muestra la respuesta del último período pedido', async () => {
    let resolveFirst
    getExpenseStatistics
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce(statistics({ total: '1.00' }))
    const user = userEvent.setup()
    renderPage()

    await user.selectOptions(monthSelect(), MONTH === '10' ? '9' : '10')
    expect(await screen.findByText('ARS 1,00')).toBeInTheDocument()
    resolveFirst(statistics({ total: '999.00' }))
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(screen.queryByText('ARS 999,00')).not.toBeInTheDocument()
  })

  it('sin gastos en el mes lo dice y ofrece registrar uno', async () => {
    getExpenseStatistics.mockResolvedValue(
      statistics({ total: '0.00', personal: EMPTY, group: EMPTY }),
    )

    renderPage()

    expect(await screen.findByText(`No registraste gastos en ${PERIOD}.`)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Registrar un gasto' })).toHaveAttribute(
      'href',
      '/expenses/new',
    )
    expect(screen.queryByRole('heading', { name: 'Personales' })).not.toBeInTheDocument()
  })

  it('con un solo tipo de gasto muestra el mensaje del otro en su lugar', async () => {
    getExpenseStatistics.mockResolvedValueOnce(statistics({ group: EMPTY }))
    const { unmount } = renderPage()

    expect(await screen.findByText(`No tuviste gastos grupales en ${PERIOD}.`)).toBeInTheDocument()
    expect(legend('personales')).toBeInTheDocument()
    unmount()

    getExpenseStatistics.mockResolvedValueOnce(statistics({ personal: EMPTY }))
    renderPage()
    expect(await screen.findByText(`No registraste gastos personales en ${PERIOD}.`)).toBeInTheDocument()
    expect(legend('grupales')).toBeInTheDocument()
  })

  it('avisa los gastos que no se pudieron convertir', async () => {
    getExpenseStatistics.mockResolvedValueOnce(statistics({ excludedCount: 1 }))
    const { unmount } = renderPage()
    expect(await screen.findByText('1 gasto no se pudo convertir a ARS y no está incluido.'))
      .toBeInTheDocument()
    unmount()

    getExpenseStatistics.mockResolvedValueOnce(
      statistics({ currency: 'USD', excludedCount: 3, personal: EMPTY, group: EMPTY }),
    )
    renderPage()
    expect(
      await screen.findByText('3 gastos no se pudieron convertir a USD y no están incluidos.'),
    ).toBeInTheDocument()
    expect(screen.getByText(`No registraste gastos en ${PERIOD}.`)).toBeInTheDocument()
  })

  it('ante un error permite reintentar', async () => {
    getExpenseStatistics.mockResolvedValueOnce({ ok: false, network: true })
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText(STATISTICS_ERROR_MESSAGE)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByText('ARS 160.000,00')).toBeInTheDocument()
    expect(getExpenseStatistics).toHaveBeenCalledTimes(2)
  })

  it('una sesión vencida cierra la sesión', async () => {
    getExpenseStatistics.mockResolvedValue({ ok: false, status: 401 })

    renderPage()

    await waitFor(() => expect(setUser).toHaveBeenCalledWith(null))
  })
})
