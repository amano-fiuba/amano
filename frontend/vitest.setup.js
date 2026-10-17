import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'

// Después de cada prueba, desmonta lo que se dibujó para que no se mezclen
afterEach(() => {
  cleanup()
})
