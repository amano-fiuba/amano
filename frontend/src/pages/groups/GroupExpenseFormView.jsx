import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { getGroup, getMembers, getExchangeQuote } from '../../api/groupsClient'
import {
  GROUP_EXPENSE_NETWORK_ERROR_MESSAGE,
  GROUP_EXPENSE_UPDATE_ERROR_MESSAGE,
  createGroupExpense,
  getGroupExpense,
  updateGroupExpense,
} from '../../api/groupExpensesClient'
import TextField from '../../components/TextField'
import SelectField from '../../components/SelectField'
import SubmitButton from '../../components/SubmitButton'
import ExpenseSharePreview from '../../components/groups/ExpenseSharePreview'
import ConvertedAmount from '../../components/debts/ConvertedAmount'
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
import { convertAmount } from './convertPreview'
import { customShares, summarizeCustom } from './splitCustom'
import {
  formatPercentage,
  parsePercentage,
  percentageShares,
  summarizePercentages,
} from './splitPercentage'
import { formatAmount, formatIsoDate } from '../../validation/expenseFormat'
import { participantKey, toParticipant, userIdOf } from './participantKeys'

// Fixed texts of the screen (specs/017-debt-states, US3), exported for tests.
export const GROUP_EXPENSE_SUCCESS_MESSAGE = 'Gasto registrado'
export const FORM_LOAD_ERROR_MESSAGE = 'No se pudo cargar el grupo. Intentá nuevamente'
export const NOT_A_MEMBER_MESSAGE = 'No existe el grupo solicitado o no sos miembro'
export const CANNOT_CREATE_MESSAGE = 'No podés registrar gastos en este grupo'
export const PARTICIPANTS_REQUIRED_MESSAGE = 'Elegí al menos un participante además de quien pagó'
export const REUSED_KEY_MESSAGE =
  'La operación ya fue registrada con datos distintos. Reintentá el guardado.'
// 022 T070: preview of the converted amount (the final quote is fixed on save).
export const QUOTE_NOTICE = 'La cotización final se fija al guardar'
export const QUOTE_LOADING_MESSAGE = 'Calculando la cotización…'
export const QUOTE_ERROR_MESSAGE =
  'No pudimos obtener la cotización en este momento. Podés guardar el gasto igual: la cotización final se fija al guardar.'
export const QUOTE_DEBOUNCE_MS = 400
// 022 T072/T073: split method, hints of the split inputs and 403 fallback.
const SPLIT_OPTIONS = Object.freeze([
  { code: 'EQUAL', label: 'En partes iguales' },
  { code: 'CUSTOM', label: 'Importes personalizados' },
  { code: 'PERCENTAGE', label: 'Porcentajes' },
])
export const SPLIT_HINT_CUSTOM = 'Cargá el importe de cada participante en la moneda del gasto'
export const SPLIT_HINT_PERCENTAGE = 'Cargá el porcentaje de cada participante'
export const SPLIT_NEEDS_AMOUNT = 'Ingresá el monto del gasto para poder repartirlo'
export const PAYER_ALWAYS_PARTICIPATES = 'Quien pagó siempre participa del reparto'
export const SPLIT_ESTIMATE_NOTE = 'Montos estimados en la moneda del grupo'
export const ONLY_OWN_PAYMENTS_MESSAGE = 'Solo podés registrar gastos pagados por vos'
// 022 T077: personal expenses (create without participants) and their edition.
export const PERSONAL_LABEL = 'Es un gasto personal (sin deudas con nadie)'
export const PERSONAL_NOTE = 'Se registra solo a nombre de quien pagó y no genera deudas.'
export const EDIT_SUCCESS_MESSAGE = 'Gasto actualizado'
export const EDIT_NOTHING_MESSAGE = 'Enviá al menos un campo para editar'
export const EDIT_NOT_PERSONAL_MESSAGE = 'No se permite editar un gasto que ya tiene participantes'
export const EDIT_NOT_ALLOWED_MESSAGE = 'No podés editar este gasto'
export const EXPENSE_GONE_MESSAGE = 'No existe el gasto solicitado o no sos miembro del grupo'

const SERVER_FIELDS = ['amount', 'currency', 'date', 'description']

// "Nuevo gasto" of a group (specs/017-debt-states, US3; plan.md §Diseño del
// frontend): amount, currency, date, description, payer and participants, with
// the live preview of the equal split. One Idempotency-Key per save operation,
// kept across network failures (same rule as ExpenseForm of 007).
export default function GroupExpenseFormView() {
  const { id, expenseId } = useParams()
  const isEdit = Boolean(expenseId)
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
  const [splitMethod, setSplitMethod] = useState('EQUAL')
  const [personal, setPersonal] = useState(false)
  const method = personal ? 'EQUAL' : splitMethod
  // Typed values per method and participant key (kept when switching method).
  const [splitValues, setSplitValues] = useState({ CUSTOM: {}, PERCENTAGE: {} })
  const [payerError, setPayerError] = useState('')
  // Last answer of exchange-quote, tagged with the request it belongs to.
  const [quote, setQuote] = useState({ key: null, status: 'idle', converted: null })

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    control,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({
    mode: 'onBlur',
    defaultValues: { amount: '', currency: 'ARS', date: today, description: '' },
  })
  const amountValue = useWatch({ control, name: 'amount' })
  const currencyValue = useWatch({ control, name: 'currency' })
  const dateValue = useWatch({ control, name: 'date' })

  useEffect(() => {
    let cancelled = false
    Promise.all([
      getGroup(id),
      getMembers(id),
      isEdit ? getGroupExpense(id, expenseId) : Promise.resolve({ ok: true }),
    ]).then(([groupResult, membersResult, expenseResult]) => {
      if (cancelled) return
      const failed = [groupResult, membersResult, expenseResult].find((result) => !result.ok)
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
          error: retryable
            ? FORM_LOAD_ERROR_MESSAGE
            : failed === expenseResult ? EXPENSE_GONE_MESSAGE : NOT_A_MEMBER_MESSAGE,
          retryable,
        })
        return
      }
      const members = Array.isArray(membersResult.members) ? membersResult.members : []
      const expense = isEdit ? expenseResult.expense : null
      setLoad({ status: 'ready', group: groupResult.group, members, expense, error: '' })
      if (expense) {
        // 022 T077: edition starts from the stored values, in the form notation.
        reset({
          amount: String(expense.amount).replace('.', ','),
          currency: expense.currency,
          date: formatIsoDate(expense.date),
          description: expense.description ?? '',
        })
      } else {
        // 022: the expense currency starts as the group's currency.
        setValue('currency', groupResult.group?.primaryCurrency || 'ARS')
      }
      setSelected(members.map(participantKey))
      setPayerKey(currentUserId != null ? `u:${currentUserId}` : null)
    })
    return () => {
      cancelled = true
    }
  }, [id, expenseId, isEdit, retryToken, currentUserId, setUser, setValue, reset])

  const { group, members } = load
  // 022 T077: only a personal expense (one share, no debts) can be edited, by who
  // registered it, its payer or an admin. The backend stays the authority.
  const editedExpense = isEdit ? load.expense : null
  let editBlocked = ''
  if (editedExpense) {
    const personalExpense =
      (editedExpense.shares?.length ?? 0) <= 1 && (editedExpense.debts?.length ?? 0) === 0
    const mayEdit =
      group?.currentUserRole === 'ADMIN' ||
      (currentUserId != null &&
        (editedExpense.createdBy?.userId === currentUserId || editedExpense.payer?.userId === currentUserId))
    if (!personalExpense) editBlocked = EDIT_NOT_PERSONAL_MESSAGE
    else if (!mayEdit) editBlocked = EDIT_NOT_ALLOWED_MESSAGE
  }
  const { canCreateExpenses, canExpenseForOthers } = expensePermissions(group)
  const names = useMemo(
    () => Object.fromEntries(members.map((member) => [participantKey(member), member.name])),
    [members],
  )
  const registered = members.filter((member) => member.userId != null)
  const payerOptions = registered.map((member) => ({ code: participantKey(member), label: member.name }))

  // Participants keep the on-screen order: it is the order sent to the API and
  // the one the preview uses for the remaining cents.
  // With CUSTOM and PERCENTAGE the payer always takes part in the split.
  const nonEqual = method !== 'EQUAL'
  // A personal expense has the payer as its only participant (no debts).
  const orderedSelection = personal
    ? (payerKey ? [payerKey] : [])
    : members
      .map(participantKey)
      .filter((key) => selected.includes(key) || (nonEqual && key === payerKey))
  const parsedAmount = parseArgentineAmount(amountValue)
  const hasOtherParticipant = personal || orderedSelection.some((key) => key !== payerKey)

  function toggleParticipant(key) {
    setParticipantsError('')
    setSelected((previous) =>
      previous.includes(key) ? previous.filter((item) => item !== key) : [...previous, key],
    )
  }

  const todayIso = parseArgentineDate(today).iso

  // 022 T070: the preview only exists when the chosen currency differs from the
  // group's and the amount and date are valid; it asks exchange-quote when one
  // of those three changes (debounced) and never blocks the submit.
  const groupCurrency = group?.primaryCurrency || 'ARS'
  const quoteAmount = parsedAmount.ok ? parsedAmount.canonical : null
  const parsedDate = parseArgentineDate(dateValue)
  const quoteDate =
    parsedDate.ok && (todayIso == null || parsedDate.iso <= todayIso) ? parsedDate.iso : null
  const needsQuote =
    load.status === 'ready' && Boolean(currencyValue) && currencyValue !== groupCurrency &&
    quoteAmount !== null && quoteDate !== null
  const quoteKey = needsQuote ? `${currencyValue}|${quoteDate}|${quoteAmount}` : null

  useEffect(() => {
    if (quoteKey === null) return undefined
    let cancelled = false
    const timer = setTimeout(async () => {
      const result = await getExchangeQuote(id, currencyValue, quoteDate)
      if (cancelled) return
      if (result.status === 401) {
        setUser?.(null)
        return
      }
      const converted = result.ok ? convertAmount(quoteAmount, result.quote?.rate) : null
      setQuote(
        converted === null
          ? { key: quoteKey, status: 'error', converted: null }
          : { key: quoteKey, status: 'ready', converted },
      )
    }, QUOTE_DEBOUNCE_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [id, quoteKey, currencyValue, quoteDate, quoteAmount, setUser])

  // An answer for another request (stale inputs) counts as "still loading".
  const quoteStatus = !needsQuote ? 'idle' : quote.key === quoteKey ? quote.status : 'loading'

  // 022 T072: split by method. CUSTOM amounts are in the original currency and add
  // up to the original amount; PERCENTAGE adds up to 100. The estimated shares are
  // in the group currency (the converted total comes from the quote preview).
  const typed = splitValues[method] ?? {}
  const foreignCurrency = Boolean(currencyValue) && currencyValue !== groupCurrency
  const estimatedTotal = !parsedAmount.ok
    ? null
    : foreignCurrency
      ? (quoteStatus === 'ready' ? quote.converted : null)
      : parsedAmount.canonical
  const customEntries = orderedSelection.map((key) => {
    const parsed = parseArgentineAmount(typed[key] ?? '')
    return { key, amount: parsed.ok ? parsed.canonical : null }
  })
  const percentageEntries = orderedSelection.map((key) => ({ key, percentage: typed[key] ?? '' }))
  const customSummary =
    method === 'CUSTOM'
      ? summarizeCustom(parsedAmount.ok ? parsedAmount.canonical : null, customEntries)
      : null
  const percentageSummary = method === 'PERCENTAGE' ? summarizePercentages(percentageEntries) : null
  const splitSummary = customSummary ?? percentageSummary
  const splitValid = !nonEqual || Boolean(splitSummary?.ok)
  const anyTyped = orderedSelection.some((key) => String(typed[key] ?? '').trim() !== '')
  let shares = null
  if (method === 'EQUAL') {
    shares = parsedAmount.ok ? splitEqually(parsedAmount.canonical, orderedSelection, payerKey) : null
  } else if (method === 'CUSTOM' && estimatedTotal) {
    shares = customShares(estimatedTotal, parsedAmount.canonical, customEntries, payerKey)
  } else if (method === 'PERCENTAGE' && estimatedTotal) {
    shares = percentageShares(estimatedTotal, percentageEntries, payerKey)
  }

  function setSplitValue(key, value) {
    setParticipantsError('')
    setSplitValues((previous) => ({
      ...previous,
      [method]: { ...previous[method], [key]: value },
    }))
  }

  function splitDifferenceText() {
    if (method === 'CUSTOM') {
      if (!parsedAmount.ok) return SPLIT_NEEDS_AMOUNT
      const diff = customSummary.differenceCents
      const money = (cents) => `$ ${formatAmount(`${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`)} ${currencyValue}`
      if (diff > 0) return `Faltan ${money(diff)}`
      if (diff < 0) return `Te pasaste por ${money(-diff)}`
      return 'Los importes suman exactamente el total'
    }
    const diff = percentageSummary.differenceHundredths
    if (diff > 0) return `Falta repartir ${formatPercentage(diff)} %`
    if (diff < 0) return `Te pasaste por ${formatPercentage(-diff)} %`
    return 'Los porcentajes suman exactamente 100 %'
  }
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

  // 022 T077: PATCH of a personal expense; only the changed fields travel.
  async function submitEdit(values) {
    const expense = load.expense
    const amount = parseArgentineAmount(values.amount)
    const date = parseArgentineDate(values.date)
    const description = normalizeDescription(values.description) ?? ''
    const changes = {}
    if (amount.ok && amount.canonical !== expense.amount) changes.amount = amount.canonical
    if (values.currency !== expense.currency) changes.currency = values.currency
    if (date.ok && date.iso !== expense.date) changes.date = date.iso
    if (description !== (expense.description ?? '')) changes.description = description
    if (Object.keys(changes).length === 0) {
      setFormError(EDIT_NOTHING_MESSAGE)
      return
    }

    const result = await updateGroupExpense(id, expenseId, changes)
    if (result.ok) {
      showSnackbar?.(EDIT_SUCCESS_MESSAGE)
      navigate(`/groups/${id}/expenses/${expenseId}`)
      return
    }
    if (result.network) {
      setFormError(GROUP_EXPENSE_UPDATE_ERROR_MESSAGE)
      return
    }
    if (result.status === 401) {
      setUser?.(null)
      return
    }
    // 400 per field next to its input; 403, 404, 409 and 503 carry the server's voseo message.
    const fields = result.fields ?? {}
    for (const [field, message] of Object.entries(fields)) {
      if (SERVER_FIELDS.includes(field)) {
        setError(field, { type: 'server', message })
      }
    }
    const unplaced = Object.entries(fields)
      .filter(([field]) => !SERVER_FIELDS.includes(field))
      .map(([, message]) => message)
    if (unplaced.length > 0) {
      setFormError(unplaced.join(' '))
    } else if (Object.keys(fields).length === 0) {
      setFormError(result.detail || GROUP_EXPENSE_UPDATE_ERROR_MESSAGE)
    }
  }

  const onSubmit = async (values) => {
    clearErrors()
    setFormError('')
    setParticipantsError('')
    setPayerError('')
    if (isEdit) {
      await submitEdit(values)
      return
    }
    if (!hasOtherParticipant) {
      setParticipantsError(PARTICIPANTS_REQUIRED_MESSAGE)
      return
    }
    if (!splitValid) {
      setParticipantsError(splitSummary?.error || SPLIT_NEEDS_AMOUNT)
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
      splitMethod: method,
      participants: orderedSelection.map((key) => {
        const participant = toParticipant(key)
        if (method === 'CUSTOM') {
          participant.amount = parseArgentineAmount(typed[key]).canonical
        } else if (method === 'PERCENTAGE') {
          participant.percentage = parsePercentage(typed[key]).canonical
        }
        return participant
      }),
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
    if (result.status === 403) {
      setFormError(result.detail || ONLY_OWN_PAYMENTS_MESSAGE)
      return
    }
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
    if (fields.splitMethod) setFormError(fields.splitMethod)
    if (Object.keys(fields).length === 0) {
      setFormError(result.detail || GROUP_EXPENSE_NETWORK_ERROR_MESSAGE)
    }
  }

  return (
    <main className="page group-page group-expense">
      <nav className="group-page__back">
        <Link to={isEdit ? `/groups/${id}/expenses/${expenseId}` : `/groups/${id}`}>
          {isEdit ? 'Volver al gasto' : 'Volver al grupo'}
        </Link>
      </nav>
      <h1>{isEdit ? 'Editar gasto' : 'Nuevo gasto'}</h1>

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

      {load.status === 'ready' && isEdit && editBlocked && (
        <p role="alert" data-testid="group-expense-edit-blocked">
          {editBlocked}
        </p>
      )}

      {load.status === 'ready' && !isEdit && !canCreateExpenses && (
        <p role="alert" data-testid="group-expense-forbidden">
          {CANNOT_CREATE_MESSAGE}
        </p>
      )}

      {load.status === 'ready' && (isEdit ? !editBlocked : canCreateExpenses) && (
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

          <div className="group-expense__quote" aria-live="polite" data-testid="quote-preview">
            {quoteStatus === 'loading' && <p>{QUOTE_LOADING_MESSAGE}</p>}
            {quoteStatus === 'ready' && (
              <>
                <p>
                  Monto estimado:{' '}
                  <ConvertedAmount
                    amount={quoteAmount}
                    currency={currencyValue}
                    convertedAmount={quote.converted}
                    convertedCurrency={groupCurrency}
                  />
                </p>
                <p className="group-expense__quote-note">{QUOTE_NOTICE}</p>
              </>
            )}
            {quoteStatus === 'error' && <p>{QUOTE_ERROR_MESSAGE}</p>}
          </div>

          <TextField
            label="Descripción"
            autoComplete="off"
            error={errors.description?.message}
            {...register('description', {
              validate: (value) => validateDescription(value) || true,
            })}
          />

          {!isEdit && (
            <>
              <SelectField
                label="¿Quién pagó?"
                options={payerOptions}
                placeholder={null}
                value={payerKey ?? ''}
                disabled={!canExpenseForOthers}
                error={payerError}
                onChange={(event) => {
                  setPayerError('')
                  setPayerKey(event.target.value || null)
                }}
              />

              <label className="group-expense__personal">
                <input
                  type="checkbox"
                  checked={personal}
                  onChange={(event) => {
                    setParticipantsError('')
                    setPersonal(event.target.checked)
                  }}
                />
                {PERSONAL_LABEL}
              </label>

              {personal ? (
                <p className="field__hint" data-testid="personal-note">{PERSONAL_NOTE}</p>
              ) : (
                <>
                  <SelectField
                    label="¿Cómo se reparte?"
                    options={SPLIT_OPTIONS}
                    placeholder={null}
                    value={splitMethod}
                    onChange={(event) => {
                      setParticipantsError('')
                      setSplitMethod(event.target.value)
                    }}
                  />

                  <fieldset className="group-expense__participants">
                    <legend>Participantes</legend>
                    {nonEqual && <p className="field__hint">{PAYER_ALWAYS_PARTICIPATES}</p>}
                    {members.map((member) => {
                      const key = participantKey(member)
                      const included = orderedSelection.includes(key)
                      const lockedPayer = nonEqual && key === payerKey
                      return (
                        <div key={key} className="group-expense__participant-row">
                          <label className="group-expense__participant">
                            <input
                              type="checkbox"
                              checked={included}
                              disabled={lockedPayer}
                              onChange={() => toggleParticipant(key)}
                            />
                            {member.name}
                            {member.simulatedProfileId != null && ' (perfil simulado)'}
                          </label>
                          {nonEqual && included && (
                            <TextField
                              label={`${method === 'CUSTOM' ? 'Importe' : 'Porcentaje'} de ${member.name}`}
                              inputMode="decimal"
                              autoComplete="off"
                              value={typed[key] ?? ''}
                              onChange={(event) => setSplitValue(key, event.target.value)}
                            />
                          )}
                        </div>
                      )
                    })}
                    {participantsError && (
                      <p role="alert" data-testid="participants-error">
                        {participantsError}
                      </p>
                    )}
                  </fieldset>

                  {nonEqual && (
                    <div className="group-expense__difference" aria-live="polite" data-testid="split-difference">
                      {!anyTyped && !(method === 'CUSTOM' && !parsedAmount.ok) ? (
                        <p>{method === 'CUSTOM' ? SPLIT_HINT_CUSTOM : SPLIT_HINT_PERCENTAGE}</p>
                      ) : (
                        <>
                          <p>{splitDifferenceText()}</p>
                          {splitSummary?.error && anyTyped && <p>{splitSummary.error}</p>}
                        </>
                      )}
                    </div>
                  )}

                  <ExpenseSharePreview
                    shares={shares}
                    names={names}
                    payerKey={payerKey}
                    showNote={method === 'EQUAL'}
                  />
                  {nonEqual && shares && foreignCurrency && (
                    <p className="group-expense__preview-note">{SPLIT_ESTIMATE_NOTE}</p>
                  )}
                </>
              )}
            </>
          )}

          <SubmitButton
            loading={isSubmitting}
            loadingLabel="Guardando…"
            disabled={!isEdit && (!hasOtherParticipant || !splitValid)}
          >
            {isEdit ? 'Guardar cambios' : 'Guardar gasto'}
          </SubmitButton>
        </form>
      )}
    </main>
  )
}
