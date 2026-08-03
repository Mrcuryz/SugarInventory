export const REVIEW_ANSWER_TEXT_MAX_LENGTH = 10000
export const REVIEW_ANSWER_SUMMARY_MAX_LENGTH = 1000

const boundedUtf16Text = (value, maxLength) => {
  const text = String(value || '')
  if (text.length <= maxLength) return text
  let bounded = text.slice(0, maxLength)
  if (/[\uD800-\uDBFF]$/.test(bounded)) bounded = bounded.slice(0, -1)
  return bounded
}

export const safeReviewAnswerText = value =>
  boundedUtf16Text(value, REVIEW_ANSWER_TEXT_MAX_LENGTH)

export const safeReviewAnswerSummary = value =>
  boundedUtf16Text(value, REVIEW_ANSWER_SUMMARY_MAX_LENGTH)
