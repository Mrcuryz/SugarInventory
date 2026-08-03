import assert from 'node:assert/strict'
import test from 'node:test'

import {
  REVIEW_ANSWER_SUMMARY_MAX_LENGTH,
  REVIEW_ANSWER_TEXT_MAX_LENGTH,
  safeReviewAnswerSummary,
  safeReviewAnswerText
} from './reviewPayload.mjs'

test('bounds the background review fields to the Java DTO contracts', () => {
  const longAnswer = '知识引用'.repeat(4000)

  assert.equal(safeReviewAnswerSummary(longAnswer).length, REVIEW_ANSWER_SUMMARY_MAX_LENGTH)
  assert.equal(safeReviewAnswerText(longAnswer).length, REVIEW_ANSWER_TEXT_MAX_LENGTH)
})

test('does not leave an unmatched UTF-16 surrogate at the truncation boundary', () => {
  const value = `${'a'.repeat(REVIEW_ANSWER_SUMMARY_MAX_LENGTH - 1)}😀后续内容`
  const summary = safeReviewAnswerSummary(value)

  assert.equal(summary.length, REVIEW_ANSWER_SUMMARY_MAX_LENGTH - 1)
  assert.doesNotMatch(summary, /[\uD800-\uDBFF]$/)
})

test('preserves short review text and normalizes nullish input', () => {
  assert.equal(safeReviewAnswerSummary('简短回答'), '简短回答')
  assert.equal(safeReviewAnswerText(null), '')
})
