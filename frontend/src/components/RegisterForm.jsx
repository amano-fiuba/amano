import { useState } from 'react'
import { useForm } from 'react-hook-form'
import {
  validateFirstName,
  validateLastName,
  validateEmail,
  normalizeEmail,
  validatePassword,
  validateConfirmPassword,
  validateAcceptTerms,
} from '../validation/registerValidation'
import { NETWORK_ERROR_MESSAGE, register } from '../api/authClient'

// Registration form (task T022; FR-001, FR-012, FR-015, FR-018, FR-019):
// rules come from registerValidation.js; server messages arrive via ProblemDetail
// fields and are shown without wiping the already-entered data.
export default function RegisterForm({ onRegistered }) {
  const {
    handleSubmit,
    register: bindField,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ mode: 'onBlur' })

  const [formError, setFormError] = useState('')

  const rules = (validate) => ({
    validate: (value) => {
      const message = validate(value)
      return message === '' || message
    },
  })

  const onSubmit = async (values) => {
    setFormError('')
    const formData = new FormData()
    formData.append('firstName', values.firstName)
    formData.append('lastName', values.lastName ?? '')
    formData.append('email', normalizeEmail(values.email))
    formData.append('password', values.password)
    formData.append('confirmPassword', values.confirmPassword)
    formData.append('acceptTerms', values.acceptTerms ? 'true' : 'false')

    const result = await register(formData)

    if (result.ok) {
      onRegistered(result.profile)
      return
    }
    if (result.network) {
      // FR-019: fixed message, keep the data, button re-enabled by isSubmitting=false.
      setFormError(NETWORK_ERROR_MESSAGE)
      return
    }
    const { fields, detail } = result
    if (fields && Object.keys(fields).length > 0) {
      for (const [field, message] of Object.entries(fields)) {
        setError(field, { type: 'server', message })
      }
    }
    else if (detail) {
      setFormError(detail)
    }
    else {
      setFormError(NETWORK_ERROR_MESSAGE)
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <div>
        <label htmlFor="firstName">Nombre</label>
        <input
          id="firstName"
          {...bindField('firstName', {
            required: 'El nombre es obligatorio',
            ...rules(validateFirstName),
          })}
        />
        {errors.firstName && <p role="alert">{errors.firstName.message}</p>}
      </div>

      <div>
        <label htmlFor="lastName">Apellido</label>
        <input
          id="lastName"
          {...bindField('lastName', rules(validateLastName))}
        />
        {errors.lastName && <p role="alert">{errors.lastName.message}</p>}
      </div>

      <div>
        <label htmlFor="email">Correo electrónico</label>
        <input
          id="email"
          type="email"
          {...bindField('email', {
            required: 'El correo electrónico es obligatorio',
            ...rules(validateEmail),
          })}
        />
        {errors.email && <p role="alert">{errors.email.message}</p>}
      </div>

      <div>
        <label htmlFor="password">Contraseña</label>
        <input
          id="password"
          /* FR-008: hidden by default. */
          type="password"
          autoComplete="new-password"
          {...bindField('password', {
            required: 'La contraseña es obligatoria',
            ...rules(validatePassword),
          })}
        />
        {errors.password && <p role="alert">{errors.password.message}</p>}
      </div>

      <div>
        <label htmlFor="confirmPassword">Confirmar contraseña</label>
        <input
          id="confirmPassword"
          type="password"
          autoComplete="new-password"
          {...bindField('confirmPassword', {
            required: 'Debes confirmar la contraseña',
            validate: () =>
              validateConfirmPassword(
                document.getElementById('password')?.value,
                document.getElementById('confirmPassword')?.value,
              ) || true,
          })}
        />
        {errors.confirmPassword && <p role="alert">{errors.confirmPassword.message}</p>}
      </div>

      <div>
        <label>
          <input
            type="checkbox"
            {...bindField('acceptTerms', rules(validateAcceptTerms))}
          />
          Acepto los Términos y Condiciones
        </label>
        {errors.acceptTerms && <p role="alert">{errors.acceptTerms.message}</p>}
      </div>

      {formError && (
        <p role="alert" data-testid="form-error">
          {formError}
        </p>
      )}

      {/* FR-015: disabled immediately on submit; re-enabled by RHF on any outcome. */}
      <button type="submit" disabled={isSubmitting}>
        Crear cuenta
      </button>
    </form>
  )
}
