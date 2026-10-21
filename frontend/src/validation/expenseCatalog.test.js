import { describe, expect, it } from 'vitest'

import {
  CATEGORY_OPTIONS,
  CURRENCY_OPTIONS,
  PAYMENT_METHOD_OPTIONS,
  TYPE_OPTIONS,
  codesOf,
  labelOf,
} from './expenseCatalog'

describe('expenseCatalog', () => {
  it('fixes the currency codes and their Spanish labels', () => {
    expect(codesOf(CURRENCY_OPTIONS)).toEqual(['ARS', 'USD', 'EUR'])
    expect(labelOf(CURRENCY_OPTIONS, 'ARS')).toBe('Peso argentino')
    expect(labelOf(CURRENCY_OPTIONS, 'USD')).toBe('Dólar estadounidense')
    expect(labelOf(CURRENCY_OPTIONS, 'EUR')).toBe('Euro')
  })

  it('fixes the category codes and their Spanish labels', () => {
    expect(codesOf(CATEGORY_OPTIONS)).toEqual([
      'FOOD',
      'TRANSPORT',
      'HOUSING',
      'SERVICES',
      'HEALTH',
      'EDUCATION',
      'LEISURE',
      'SHOPPING',
      'PETS',
      'OTHER',
    ])
    expect(labelOf(CATEGORY_OPTIONS, 'FOOD')).toBe('Alimentos')
    expect(labelOf(CATEGORY_OPTIONS, 'EDUCATION')).toBe('Educación')
    expect(labelOf(CATEGORY_OPTIONS, 'OTHER')).toBe('Otros')
  })

  it('fixes the expense type codes', () => {
    expect(codesOf(TYPE_OPTIONS)).toEqual(['RECURRING', 'ONE_TIME'])
    expect(labelOf(TYPE_OPTIONS, 'RECURRING')).toBe('Recurrente')
    expect(labelOf(TYPE_OPTIONS, 'ONE_TIME')).toBe('Único')
  })

  it('fixes the payment method codes', () => {
    expect(codesOf(PAYMENT_METHOD_OPTIONS)).toEqual([
      'CASH',
      'DEBIT_CARD',
      'CREDIT_CARD',
      'TRANSFER',
      'VIRTUAL_WALLET',
      'OTHER',
    ])
    expect(labelOf(PAYMENT_METHOD_OPTIONS, 'DEBIT_CARD')).toBe('Tarjeta de débito')
    expect(labelOf(PAYMENT_METHOD_OPTIONS, 'VIRTUAL_WALLET')).toBe('Billetera virtual')
  })

  it('returns an empty label for an unknown code', () => {
    expect(labelOf(CATEGORY_OPTIONS, 'UNKNOWN')).toBe('')
  })

  it('exposes frozen catalogs so they cannot be mutated at runtime', () => {
    expect(Object.isFrozen(CURRENCY_OPTIONS)).toBe(true)
    expect(Object.isFrozen(CATEGORY_OPTIONS)).toBe(true)
    expect(Object.isFrozen(TYPE_OPTIONS)).toBe(true)
    expect(Object.isFrozen(PAYMENT_METHOD_OPTIONS)).toBe(true)
  })
})
