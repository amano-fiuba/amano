import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { getGroup, getMembers } from '../../api/groupsClient'
import {
  GROUP_EXPENSE_NETWORK_ERROR_MESSAGE,
  createGroupExpense,
} from '../../api/groupExpensesClient'
import TextField from '../../components/TextField'
import SelectField from '../../components/SelectField'
import SubmitButton from '../../components/SubmitButton'
import ExpenseSharePreview from '../../components/groups/ExpenseSharePreview'
import { useSnackbar } from '../../components/Snackbar'
import useCurrentDate, { deviceTimeZone } from '../../hooks/useCurrentDate'
import { CURRENCY_OPTIONS } from '../../validation/expenseCatalog'
import {
  DATE_FUTURE,
  normalizeDescription,
  parseArgentineAmount,
  parseArgentineDate,
  validateDescription,
} from '../../validation/expenseValidation'
import { expensePermissions } from './expensePermissions'
import { splitEqually } from './splitEqually'
import { participantKey, toParticipant, userIdOf } from './participantKeys'

// Fixed texts of the screen (specs/017-debt-states, US3), exported for tests.
export const GROUP_EXPENSE_SUCCESS_MESSAGE = 'Gasto registrado'
export const FORM_LOAD_ERROR_MESSAGE = 'No se pudo cargar el grupo. Intentá nuevamente'
export const NOT_A_MEMBER_MESSAGE = 'No existe el grupo solicitado o no sos miembro'
export const CANNOT_CREATE_MESSAGE = 'No podés registrar gastos en este grupo'
export const PARTICIPANTS_REQUIRED_MESSAGE = 'Elegí al menos un participante además de quien pagó'
export const REUSED_KEY_MESSAGE =
  'La operación ya fue registrada con datos distintos. Reintentá el guardado.'

const SERVER_FIELDS = ['amount', 'currency', 'date', 'description']

// "Nuevo gasto" of a group (specs/017-debt-states, US3; plan.md §Diseño del
// frontend): amount, currency, date, description, payer and participants, with
// the live preview of the equal split. One Idempotency-Key per save operation,
// kept across network failures (same rule as ExpenseForm of 007).
export default function GroupExpenseFormView() {
  const { id } = useParams()
  const auth = useAuth()
  const currentUserId = auth?.user?.id ?? null
  const setUser = auth?.setUser
  const navigate = useNavigate()
  const showSnackbar = useSnackbar()
  const { today } = useCurrentDate()

  const [load, setLoad] = useState({ status: 'loading', group: null, members: [], error: '' })
  const [retryToken, setRetryToken] = useState(0)
  const [payerKey, setPayerKey] = useState(null)
  const [selected, setSelected] = useState([])
  const [idempotencyKey, setIdempotencyKey] = useState(null)
  const [formError, setFormError] = useState('')
  const [participantsError, setParticipantsError] = useState('')
  const [payerError, setPayerError] = useState('')

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    control,
    formState: { errors, isSubmitting },
  } = useForm({
    mode: 'onBlur',
    defaultValues: { amount: '', currency: 'ARS', date: today, description: '' },
  })
  const amountValue = useWatch({ control, name: 'amount' })

  useEffect(() => {
    let cancelled = false
    Promise.all([getGroup(id), getMembers(id)]).then(([groupResult, membersResult]) => {
      if (cancelled) return
      const failed = [groupResult, membersResult].find((result) => !result.ok)
      if (failed) {
        if (failed.status === 401) {
          setUser?.(null)
          return
        }
        const retryable = Boolean(failed.network) || failed.status >= 500
        setLoad({
          status: 'error',
          group: null,
          members: [],
          error: retryable ? FORM_LOAD_ERROR_MESSAGE : NOT_A_MEMBER_MESSAGE,
          retryable,
        })
        return
      }
      const members = Array.isArray(membersResult.members) ? membersResult.members : []
      setLoad({ status: 'ready', group: groupResult.group, members, error: '' })
      setSelected(members.map(participantKey))
      setPayerKey(currentUserId != null ? `u:${currentUserId}` : null)
    })
    return () => {
      cancelled = true
    }
  }, [id, retryToken, currentUserId, setUser])

  const { group, members } = load
  const { canCreateExpenses, canExpenseForOthers } = expensePermissions(group)
  const names = useMemo(
    () => Object.fromEntries(members.map((member) => [participantKey(member), member.name])),
    [members],
  )
  const registered = members.filter((member) => member.userId != null)
  const payerOptions = registered.map((member) => ({ code: participantKey(member), label: member.name }))

  // Participants keep the on-screen order: it is the order sent to the API and
  // the one the preview uses for the remaining cents.
  const orderedSelection = members.map(participantKey).filter((key) => selected.includes(key))
  const parsedAmount = parseArgentineAmount(amountValue)
  const shares = parsedAmount.ok ? splitEqually(parsedAmount.canonical, orderedSelection, payerKey) : null
  const hasOtherParticipant = orderedSelection.some((key) => key !== payerKey)

  function toggleParticipant(key) {
    setParticipantsError('')
    setSelected((previous) =>
      previous.includes(key) ? previous.filter((item) => item !== key) : [...previous, key],
    )
  }

  const todayIso = parseArgentineDate(today).iso
  const validateDate = (value) => {
    const date = parseArgentineDate(value)
    if (!date.ok) {
      return date.error
    }
    if (todayIso != null && date.iso > todayIso) {
      return DATE_FUTURE
    }
    return true
  }

  const onSubmit = async (values) => {
    clearErrors()
    setFormError('')
    setParticipantsError('')
    setPayerError('')
    if (!hasOtherParticipant) {
      setParticipantsError(PARTICIPANTS_REQUIRED_MESSAGE)
      return
    }

    const amount = parseArgentineAmount(values.amount)
    const date = parseArgentineDate(values.date)
    const description = normalizeDescription(values.description)
    const payload = {
      amount: amount.ok ? amount.canonical : values.amount,
      currency: values.currency,
      date: date.ok ? date.iso : values.date,
      deviceTimeZone: deviceTimeZone(),
      payerUserId: userIdOf(payerKey),
      participants: orderedSelection.map(toParticipant),
    }
    if (description !== null) {
      payload.description = description
    }

    const key = idempotencyKey ?? crypto.randomUUID()
    if (idempotencyKey === null) {
      setIdempotencyKey(key)
    }
    const result = await createGroupExpense(id, payload, key)

    if (result.ok) {
      setIdempotencyKey(null)
      showSnackbar?.(GROUP_EXPENSE_SUCCESS_MESSAGE)
      navigate(`/groups/${id}/expenses/${result.expense.id}`)
      return
    }
    if (result.network) {
      setFormError(GROUP_EXPENSE_NETWORK_ERROR_MESSAGE)
      return
    }
    if (result.status === 401) {
      setUser?.(null)
      return
    }
    // A rejected payload is a finished operation: the next confirm gets a new key.
    setIdempotencyKey(null)
    if (result.status === 422) {
      setFormError(result.detail || REUSED_KEY_MESSAGE)
      return
    }
    const fields = result.fields ?? {}
    for (const [field, message] of Object.entries(fields)) {
      if (SERVER_FIELDS.includes(field)) {
        setError(field, { type: 'server', message })
      }
    }
    if (fields.participants) setParticipantsError(fields.participants)
    if (fields.payerUserId) setPayerError(fields.payerUserId)
    if (Object.keys(fields).length === 0) {
      setFormError(result.detail || GROUP_EXPENSE_NETWORK_ERROR_MESSAGE)
    }
  }

  return (
    <main className="page group-page group-expense">
      <nav className="group-page__back">
        <Link to={`/groups/${id}`}>Volver al grupo</Link>
      </nav>
      <h1>Nuevo gasto</h1>

      {load.status === 'loading' && (
        <div role="status" className="group-expense__loading">
          <span className="button-spinner" aria-hidden="true" />
          <p>Cargando…</p>
        </div>
      )}

      {load.status === 'error' && (
        <div role="alert" data-testid="group-expense-load-error">
          <p>{load.error}</p>
          {load.retryable && (
            <button
              type="button"
              onClick={() => {
                setLoad({ status: 'loading', group: null, members: [], error: '' })
                setRetryToken((token) => token + 1)
              }}
            >
              Reintentar
            </button>
          )}
        </div>
      )}

      {load.status === 'ready' && !canCreateExpenses && (
        <p role="alert" data-testid="group-expense-forbidden">
          {CANNOT_CREATE_MESSAGE}
        </p>
      )}

      {load.status === 'ready' && canCreateExpenses && (
        <form className="group-card group-expense__form" onSubmit={handleSubmit(onSubmit)} noValidate>
          {formError && (
            <p role="alert" data-testid="group-expense-error">
              {formError}
            </p>
          )}

          <TextField
            label="Monto"
            inputMode="decimal"
            autoComplete="off"
            error={errors.amount?.message}
            {...register('amount', {
              validate: (value) => {
                const parsed = parseArgentineAmount(value)
                return parsed.ok ? true : parsed.error
              },
            })}
          />

          <SelectField
            label="Moneda"
            options={CURRENCY_OPTIONS}
            error={errors.currency?.message}
            {...register('currency', {
              validate: (value) => (value ? true : 'La moneda es obligatoria'),
            })}
          />

          <TextField
            label="Fecha"
            placeholder="dd/mm/aaaa"
            autoComplete="off"
            error={errors.date?.message}
            {...register('date', { validate: validateDate })}
          />

          <TextField
            label="Descripción"
            autoComplete="off"
            error={errors.description?.message}
            {...register('description', {
              validate: (value) => validateDescription(value) || true,
            })}
          />

          <SelectField
            label="¿Quién pagó?"
            options={payerOptions}
            value={payerKey ?? ''}
            disabled={!canExpenseForOthers}
            error={payerError}
            onChange={(event) => {
              setPayerError('')
              setPayerKey(event.target.value || null)
            }}
          />

          <fieldset className="group-expense__participants">
            <legend>Participantes</legend>
            {members.map((member) => {
              const key = participantKey(member)
              return (
                <label key={key} className="group-expense__participant">
                  <input
                    type="checkbox"
                    checked={selected.includes(key)}
                    onChange={() => toggleParticipant(key)}
                  />
                  {member.name}
                  {member.simulatedProfileId != null && ' (perfil simulado)'}
                </label>
              )
            })}
            {participantsError && (
              <p role="alert" data-testid="participants-error">
                {participantsError}
              </p>
            )}
          </fieldset>

          <ExpenseSharePreview shares={shares} names={names} payerKey={payerKey} />

          <SubmitButton loading={isSubmitting} loadingLabel="Guardando…" disabled={!hasOtherParticipant}>
            Guardar gasto
          </SubmitButton>
        </form>
      )}
    </main>
  )
}
