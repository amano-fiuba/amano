import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import TextField from './TextField'
import SelectField from './SelectField'
import DatePickerField from './DatePickerField'
import CurrencySelect from './CurrencySelect'
import ConvertedAmount from './debts/ConvertedAmount'
import ExpenseSummaryDialog from './ExpenseSummaryDialog'
import { formatAmount } from '../validation/expenseFormat'
import { getCurrencyQuote } from '../api/currencyClient'
import { convertAmount } from '../pages/groups/convertPreview'
import { useSnackbar } from './Snackbar'
import {
  EXPENSE_NETWORK_ERROR_MESSAGE,
  createExpense,
} from '../api/expenseClient'
import {
  CATEGORY_OPTIONS,
  FREQUENCY_OPTIONS,
  PAYMENT_METHOD_OPTIONS,
  TYPE_OPTIONS,
  currencyLabel,
  labelOf,
} from '../validation/expenseCatalog'
import {
  DATE_FUTURE,
  normalizeDescription,
  parseArgentineAmount,
  parseArgentineDate,
  validateDescription,
} from '../validation/expenseValidation'
import { deviceTimeZone } from '../hooks/useCurrentDate'

export const EXPENSE_SUCCESS_MESSAGE = 'El gasto se guardó correctamente'
export const EXPENSE_REUSED_KEY_MESSAGE =
  'La operación ya fue registrada con datos distintos. Reintentá el guardado.'
export const EXPENSE_SESSION_EXPIRED_MESSAGE =
  'Tu sesión expiró. Iniciá sesión nuevamente.'
// 027 (FR-019): leaving with loaded data asks first.
export const EXPENSE_DISCARD_CONFIRM = '¿Descartar el gasto? Se perderán los datos cargados'
// 027 (FR-035): estimated amount in pesos for an expense in another currency.
export const PERSONAL_QUOTE_LOADING = 'Calculando la cotización…'
export const PERSONAL_QUOTE_NOTE = 'Estimación con la cotización oficial del BCRA. El gasto se guarda en su moneda.'
export const PERSONAL_QUOTE_ERROR =
  'No pudimos obtener la cotización en este momento. Podés guardar el gasto igual.'
export const PERSONAL_QUOTE_DEBOUNCE_MS = 400

const REQUIRED_SELECT_MESSAGES = {
  currency: 'La moneda es obligatoria',
  category: 'La categoría es obligatoria',
  type: 'El tipo de gasto es obligatorio',
  paymentMethod: 'El medio de pago es obligatorio',
}

// Feature 009 (FR-001): a recurring expense needs its frequency.
export const FREQUENCY_REQUIRED_MESSAGE =
  'La frecuencia es obligatoria para un gasto recurrente'

const FIELD_NAMES = [
  'amount',
  'currency',
  'date',
  'category',
  'type',
  'frequency',
  'paymentMethod',
  'description',
]

/**
 * Expense form (T029): required fields + optional description, submission with
 * the operation's Idempotency-Key (generated on the first confirm, kept across
 * failures, renewed only after success) and the exact FR-032 reset. Server field
 * errors are surfaced next to their field; network/5xx keep the entered data.
 * Feature 009: choosing "Recurrente" reveals the required "Frecuencia" select,
 * which is only sent for recurring expenses (FR-001/FR-002).
 * Feature 027 (FR-010, FR-011, FR-018, FR-019): calendar date field, the user's
 * currencies plus "Ver más" (BCRA catalog), group-like card surface and a
 * Cancelar / Guardar footer. Never a shared-expense panel. "Guardar" opens a final
 * summary; the expense is saved only after "Confirmar".
 */
export default function ExpenseForm({ today, initialCurrency = 'ARS', preferredCurrencies = [], onCancel }) {
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    setError,
    clearErrors,
    control,
    formState: { errors, isSubmitting, dirtyFields, isDirty },
  } = useForm({
    mode: 'onBlur',
    defaultValues: {
      amount: '',
      currency: initialCurrency,
      date: today,
      category: '',
      type: '',
      frequency: '',
      paymentMethod: '',
      description: '',
    },
  })
  const selectedType = useWatch({ control, name: 'type' })
  const isRecurring = selectedType === 'RECURRING'
  const currencyValue = useWatch({ control, name: 'currency' })
  const amountValue = useWatch({ control, name: 'amount' })
  const dateValue = useWatch({ control, name: 'date' })
  // Last answer of the quote, tagged with the request it belongs to.
  const [quote, setQuote] = useState({ key: null, status: 'idle', converted: null })

  const showSnackbar = useSnackbar()
  const [idempotencyKey, setIdempotencyKey] = useState(null)
  const [formError, setFormError] = useState('')
  const [sessionExpired, setSessionExpired] = useState(false)
  // Validated payload waiting for "Confirmar" in the summary, with its rows.
  const [pending, setPending] = useState(null)
  const [saving, setSaving] = useState(false)

  // The preloaded date follows midnight only while the user has not edited it.
  useEffect(() => {
    if (!dirtyFields.date) {
      setValue('date', today, { shouldDirty: false })
    }
  }, [today, dirtyFields.date, setValue])

  // The profile currency may arrive after the first render (me()).
  useEffect(() => {
    if (!dirtyFields.currency) {
      setValue('currency', initialCurrency, { shouldDirty: false })
    }
  }, [initialCurrency, dirtyFields.currency, setValue])

  const resetForm = () => {
    reset({
      amount: '',
      currency: initialCurrency,
      date: today,
      category: '',
      type: '',
      frequency: '',
      paymentMethod: '',
      description: '',
    })
  }

  const todayIso = parseArgentineDate(today).iso

  // 027 (FR-035): like the group form (022 T070), a valid amount and date in a
  // currency other than ARS show an estimate in pesos. It never blocks the submit
  // and nothing of it is saved.
  const quoteAmount = parseArgentineAmount(amountValue)
  const quoteDate = parseArgentineDate(dateValue)
  const quoteKey =
    Boolean(currencyValue) && currencyValue !== 'ARS' && quoteAmount.ok && quoteDate.ok &&
    (todayIso == null || quoteDate.iso <= todayIso)
      ? `${currencyValue}|${quoteDate.iso}|${quoteAmount.canonical}`
      : null

  useEffect(() => {
    if (quoteKey === null) return undefined
    const [currency, date, amount] = quoteKey.split('|')
    let cancelled = false
    const timer = setTimeout(async () => {
      const result = await getCurrencyQuote(currency, date)
      if (cancelled) return
      const converted = result.ok ? convertAmount(amount, result.quote?.rate) : null
      setQuote({ key: quoteKey, status: converted === null ? 'error' : 'ready', converted })
    }, PERSONAL_QUOTE_DEBOUNCE_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [quoteKey])

  // An answer for another request (stale inputs) counts as "still loading".
  const quoteStatus = quoteKey === null ? 'idle' : quote.key === quoteKey ? quote.status : 'loading'
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
  const validateRequiredSelect = (field) => (value) =>
    value ? true : REQUIRED_SELECT_MESSAGES[field]

  const handleCancel = () => {
    if (isDirty && !window.confirm(EXPENSE_DISCARD_CONFIRM)) {
      return
    }
    onCancel?.()
  }

  const onSubmit = async (values) => {
    clearErrors()
    setFormError('')
    setSessionExpired(false)

    const amount = parseArgentineAmount(values.amount)
    const date = parseArgentineDate(values.date)
    const description = normalizeDescription(values.description)
    const payload = {
      amount: amount.ok ? amount.canonical : values.amount,
      currency: values.currency,
      date: date.ok ? date.iso : values.date,
      category: values.category,
      type: values.type,
      paymentMethod: values.paymentMethod,
      deviceTimeZone: deviceTimeZone(),
    }
    if (description !== null) {
      payload.description = description
    }
    if (values.type === 'RECURRING') {
      payload.frequency = values.frequency
    }

    const rows = [
      { label: 'Monto', value: `$ ${formatAmount(payload.amount)} ${payload.currency}` },
      ...(payload.currency !== 'ARS' && quoteStatus === 'ready'
        ? [{ label: 'Estimado en pesos', value: `$ ${formatAmount(quote.converted)} ARS` }]
        : []),
      { label: 'Moneda', value: currencyLabel(payload.currency) },
      { label: 'Fecha', value: values.date },
      { label: 'Categoría', value: labelOf(CATEGORY_OPTIONS, payload.category) },
      { label: 'Tipo de gasto', value: labelOf(TYPE_OPTIONS, payload.type) },
      ...(payload.frequency ? [{ label: 'Frecuencia', value: labelOf(FREQUENCY_OPTIONS, payload.frequency) }] : []),
      { label: 'Medio de pago', value: labelOf(PAYMENT_METHOD_OPTIONS, payload.paymentMethod) },
      ...(payload.description ? [{ label: 'Descripción', value: payload.description }] : []),
    ]
    setPending({ payload, rows })
  }

  const confirmSave = async () => {
    const { payload } = pending
    setSaving(true)
    try {
      await save(payload)
    } finally {
      setSaving(false)
      setPending(null)
    }
  }

  const save = async (payload) => {
    // One key per save operation: kept on any failure, renewed on success.
    const key = idempotencyKey ?? crypto.randomUUID()
    if (idempotencyKey === null) {
      setIdempotencyKey(key)
    }
    const result = await createExpense(payload, key)

    if (result.ok) {
      setIdempotencyKey(null)
      resetForm()
      // FR-031: the snackbar closes by itself after 5 s.
      showSnackbar(EXPENSE_SUCCESS_MESSAGE)
      return
    }
    if (result.network) {
      setFormError(EXPENSE_NETWORK_ERROR_MESSAGE)
      return
    }
    if (result.status === 401) {
      setSessionExpired(true)
      setFormError(EXPENSE_SESSION_EXPIRED_MESSAGE)
      return
    }
    if (result.reusedKey) {
      // The previous operation is already persisted: a new confirm must use a
      // fresh key so the user can intentionally register the edited payload.
      setIdempotencyKey(null)
      setFormError(result.detail || EXPENSE_REUSED_KEY_MESSAGE)
      return
    }
    const fields = result.fields ?? {}
    for (const [field, message] of Object.entries(fields)) {
      if (FIELD_NAMES.includes(field)) {
        setError(field, { type: 'server', message })
      }
    }
    if (Object.keys(fields).length === 0) {
      setFormError(result.detail || EXPENSE_NETWORK_ERROR_MESSAGE)
    }
  }

  return (
    <form className="expense-form" onSubmit={handleSubmit(onSubmit)} noValidate>
      {formError && (
        <p role="alert" data-testid="expense-error">
          {formError}
          {sessionExpired && (
            <>
              {' '}
              <a href="/login">Iniciar sesión</a>
            </>
          )}
        </p>
      )}

      <div className="group-card expense-form__card">
        <TextField
          label="Monto"
          inputMode="decimal"
          autoComplete="off"
          error={errors.amount?.message}
          {...register('amount', {
            validate: (value) => {
              const amount = parseArgentineAmount(value)
              return amount.ok ? true : amount.error
            },
          })}
        />

        <Controller
          name="currency"
          control={control}
          rules={{ validate: validateRequiredSelect('currency') }}
          render={({ field }) => (
            <CurrencySelect
              {...field}
              preferred={preferredCurrencies}
              error={errors.currency?.message}
            />
          )}
        />

        <Controller
          name="date"
          control={control}
          rules={{ validate: validateDate }}
          render={({ field }) => (
            <DatePickerField
              {...field}
              label="Fecha"
              max={todayIso ?? undefined}
              error={errors.date?.message}
            />
          )}
        />

        <div className="group-expense__quote" aria-live="polite" data-testid="personal-quote">
          {quoteStatus === 'loading' && <p>{PERSONAL_QUOTE_LOADING}</p>}
          {quoteStatus === 'ready' && (
            <>
              <p>
                Monto estimado:{' '}
                <ConvertedAmount
                  amount={quoteAmount.canonical}
                  currency={currencyValue}
                  convertedAmount={quote.converted}
                  convertedCurrency="ARS"
                />
              </p>
              <p className="group-expense__quote-note">{PERSONAL_QUOTE_NOTE}</p>
            </>
          )}
          {quoteStatus === 'error' && <p>{PERSONAL_QUOTE_ERROR}</p>}
        </div>

        <SelectField
          label="Categoría"
          options={CATEGORY_OPTIONS}
          error={errors.category?.message}
          {...register('category', {
            validate: validateRequiredSelect('category'),
          })}
        />

        <SelectField
          label="Tipo de gasto"
          options={TYPE_OPTIONS}
          error={errors.type?.message}
          {...register('type', {
            validate: validateRequiredSelect('type'),
          })}
        />

        {isRecurring && (
          <SelectField
            label="Frecuencia"
            options={FREQUENCY_OPTIONS}
            error={errors.frequency?.message}
            {...register('frequency', {
              validate: (value, values) =>
                values.type !== 'RECURRING' || value ? true : FREQUENCY_REQUIRED_MESSAGE,
            })}
          />
        )}

        <SelectField
          label="Medio de pago"
          options={PAYMENT_METHOD_OPTIONS}
          error={errors.paymentMethod?.message}
          {...register('paymentMethod', {
            validate: validateRequiredSelect('paymentMethod'),
          })}
        />

        <TextField
          label="Descripción"
          autoComplete="off"
          error={errors.description?.message}
          {...register('description', {
            validate: (value) => validateDescription(value) || true,
          })}
        />
      </div>

      <div className="expense-actions">
        <button type="button" className="expense-actions__cancel" onClick={handleCancel}>
          Cancelar
        </button>
        <button type="submit" disabled={isSubmitting || saving}>
          {isSubmitting || saving ? 'Guardando…' : 'Guardar'}
        </button>
      </div>
      {pending && (
        <ExpenseSummaryDialog
          rows={pending.rows}
          confirming={saving}
          onConfirm={confirmSave}
          onCancel={() => setPending(null)}
        />
      )}
    </form>
  )
}
