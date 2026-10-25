import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import TextField from './TextField'
import SelectField from './SelectField'
import {
  EXPENSE_NETWORK_ERROR_MESSAGE,
  createExpense,
} from '../api/expenseClient'
import {
  CATEGORY_OPTIONS,
  CURRENCY_OPTIONS,
  FREQUENCY_OPTIONS,
  PAYMENT_METHOD_OPTIONS,
  TYPE_OPTIONS,
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
export const SUCCESS_VISIBLE_MS = 5000

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
 */
export default function ExpenseForm({ today, initialCurrency = 'ARS' }) {
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    setError,
    clearErrors,
    control,
    formState: { errors, isSubmitting, dirtyFields },
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

  const [idempotencyKey, setIdempotencyKey] = useState(null)
  const [success, setSuccess] = useState('')
  const [formError, setFormError] = useState('')
  const [sessionExpired, setSessionExpired] = useState(false)

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

  // FR-031: the success notification closes by itself after 5 s.
  useEffect(() => {
    if (!success) {
      return undefined
    }
    const timer = setTimeout(() => setSuccess(''), SUCCESS_VISIBLE_MS)
    return () => clearTimeout(timer)
  }, [success])

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

  const onSubmit = async (values) => {
    clearErrors()
    setFormError('')
    setSessionExpired(false)
    setSuccess('')

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

    // One key per save operation: kept on any failure, renewed on success.
    const key = idempotencyKey ?? crypto.randomUUID()
    if (idempotencyKey === null) {
      setIdempotencyKey(key)
    }
    const result = await createExpense(payload, key)

    if (result.ok) {
      setIdempotencyKey(null)
      resetForm()
      setSuccess(EXPENSE_SUCCESS_MESSAGE)
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
      {success && (
        <div role="status" data-testid="expense-success" className="form-success">
          <span>{success}</span>
          <button
            type="button"
            className="text-button"
            aria-label="Cerrar notificación"
            onClick={() => setSuccess('')}
          >
            Cerrar
          </button>
        </div>
      )}
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

      <SelectField
        label="Moneda"
        options={CURRENCY_OPTIONS}
        error={errors.currency?.message}
        {...register('currency', {
          validate: validateRequiredSelect('currency'),
        })}
      />

      <TextField
        label="Fecha"
        placeholder="dd/mm/aaaa"
        autoComplete="off"
        error={errors.date?.message}
        {...register('date', { validate: validateDate })}
      />

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

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Guardando…' : 'Guardar gasto'}
      </button>
    </form>
  )
}
