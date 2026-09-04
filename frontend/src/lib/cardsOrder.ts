import type { CardResponse } from '../types'

/** Update a card in place, or append a newly issued card at the end (DB insert order). */
export function upsertCardInList(
  previous: CardResponse[],
  updated: CardResponse,
): CardResponse[] {
  const idx = previous.findIndex((card) => card.id === updated.id)
  if (idx === -1) return [...previous, updated]
  const next = [...previous]
  next[idx] = updated
  return next
}
