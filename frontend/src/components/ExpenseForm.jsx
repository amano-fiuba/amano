import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import TextField from './TextField'
import SelectField from './SelectField'
import {
  EXPENSE_NETWORK_ERROR_MESSAGE,
  createExpense,
} from '../api/expenseClient'
import {
  CATEGORY_OPTIONS,
  CURRENCY_OPTIONS,
  PAYMENT_METHOD_OPTIONS,
  TYPE_OPTIONS,
} from '../validation/expenseCatalog'
import {
  normalizeDescription,
  parseArgentineAmount,
  parseArgentineDate,
} from '../validation/expenseValidation'
import { deviceTimeZone } from '../hooks/useCurrentDate'

export const EXPENSE_SUCCESS_MESSAGE = 'El gasto se guardó correctamente'
export const EXPENSE_REUSED_KEY_MESSAGE =
  'La operación ya fue registrada con datos distintos. Reintentá el guardado.'
export const SUCCESS_VISIBLE_MS = 5000

const FIELD_NAMES = [
  'amount',
  'currency',
  'date',
  'category',
  'type',
  'paymentMethod',
  'description',
]

/**
 * Expense form (T029): required fields + optional description, submission with
 * the operation's Idempotency-Key (generated on the first confirm, kept across
 * failures, renewed only after success) and the exact FR-032 reset. Server field
 * errors are surfaced next to their field; network/5xx keep the entered data.
 */
export default function ExpenseForm({ today, initialCurrency = 'ARS' }) {
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    setError,
    formState: { errors, isSubmitting, dirtyFields },
  } = useForm({
    defaultValues: {
      amount: '',
      currency: initialCurrency,
      date: today,
      category: '',
      type: '',
      paymentMethod: '',
      description: '',
    },
  })

  const [idempotencyKey, setIdempotencyKey] = useState(null)
  const [success, setSuccess] = useState('')
  const [formError, setFormError] = useState('')

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
      paymentMethod: '',
      description: '',
    })
  }

  const onSubmit = async (values) => {
    setFormError('')
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
    if (result.reusedKey) {
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
        </p>
      )}

      <TextField
        label="Monto"
        inputMode="decimal"
        autoComplete="off"
        error={errors.amount?.message}
        {...register('amount')}
      />

      <SelectField
        label="Moneda"
        options={CURRENCY_OPTIONS}
        error={errors.currency?.message}
        {...register('currency')}
      />

      <TextField
        label="Fecha"
        placeholder="dd/mm/aaaa"
        autoComplete="off"
        error={errors.date?.message}
        {...register('date')}
      />

      <SelectField
        label="Categoría"
        options={CATEGORY_OPTIONS}
        error={errors.category?.message}
        {...register('category')}
      />

      <SelectField
        label="Tipo de gasto"
        options={TYPE_OPTIONS}
        error={errors.type?.message}
        {...register('type')}
      />

      <SelectField
        label="Medio de pago"
        options={PAYMENT_METHOD_OPTIONS}
        error={errors.paymentMethod?.message}
        {...register('paymentMethod')}
      />

      <TextField
        label="Descripción"
        autoComplete="off"
        error={errors.description?.message}
        {...register('description')}
      />

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Guardando…' : 'Guardar gasto'}
      </button>
    </form>
  )
}
