// Closed expense catalogs shared by the expense form (FR-010/FR-011, D6).
// The persisted/API value is the stable English code; the Spanish label lives
// only here. The exact code lists are fixed by expenseCatalog.test.js and must
// stay in sync with the backend enums and the V4 CHECK constraints.
export const CURRENCY_OPTIONS = Object.freeze([
  { code: 'ARS', label: 'Peso argentino' },
  { code: 'USD', label: 'Dólar estadounidense' },
  { code: 'EUR', label: 'Euro' },
])

export const CATEGORY_OPTIONS = Object.freeze([
  { code: 'FOOD', label: 'Alimentos' },
  { code: 'TRANSPORT', label: 'Transporte' },
  { code: 'HOUSING', label: 'Vivienda' },
  { code: 'SERVICES', label: 'Servicios' },
  { code: 'HEALTH', label: 'Salud' },
  { code: 'EDUCATION', label: 'Educación' },
  { code: 'LEISURE', label: 'Ocio' },
  { code: 'SHOPPING', label: 'Compras' },
  { code: 'PETS', label: 'Mascotas' },
  { code: 'OTHER', label: 'Otros' },
])

export const TYPE_OPTIONS = Object.freeze([
  { code: 'RECURRING', label: 'Recurrente' },
  { code: 'ONE_TIME', label: 'Único' },
])

// Feature 009 (specs/009-pause-recurring-expense): frequency of a recurring series
// and its state. Codes match RecurrenceFrequency/RecurrenceStatus and the V5 CHECKs.
export const FREQUENCY_OPTIONS = Object.freeze([
  { code: 'WEEKLY', label: 'Semanal' },
  { code: 'MONTHLY', label: 'Mensual' },
  { code: 'YEARLY', label: 'Anual' },
])

export const STATUS_OPTIONS = Object.freeze([
  { code: 'ACTIVE', label: 'Activo' },
  { code: 'PAUSED', label: 'Pausado' },
  { code: 'FINISHED', label: 'Finalizado' },
])

export const PAYMENT_METHOD_OPTIONS = Object.freeze([
  { code: 'CASH', label: 'Efectivo' },
  { code: 'DEBIT_CARD', label: 'Tarjeta de débito' },
  { code: 'CREDIT_CARD', label: 'Tarjeta de crédito' },
  { code: 'TRANSFER', label: 'Transferencia' },
  { code: 'VIRTUAL_WALLET', label: 'Billetera virtual' },
  { code: 'OTHER', label: 'Otro' },
])

/** Codes of an option catalog, in catalog order. */
export function codesOf(options) {
  return options.map((option) => option.code)
}

/** Spanish label of a code, or an empty string when the code is not in the catalog. */
export function labelOf(options, code) {
  return options.find((option) => option.code === code)?.label ?? ''
}
