const assert = require('node:assert')
const { Given, Then } = require('@cucumber/cucumber')

function checkAnswer(actual) {
  // Nested call so the failure has a multi-frame stack trace
  assert.strictEqual(actual, 42, 'the answer should be 42')
}

Then('this step fails', function () {
  checkAnswer(41)
})

Then('this step throws a plain error', function () {
  throw new Error('Something went wrong\nwith a second line of detail')
})

Then('this step is pending', function () {
  return 'pending'
})

Then('this step is skipped', function () {
  return 'skipped'
})

Then('this step takes {int} ms', function (ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
})

// Two definitions match the same text, so the step is ambiguous
Then('an ambiguous step', function () {})
Then(/^an? ambiguous step$/, function () {})

Given('a passing step', function () {})
