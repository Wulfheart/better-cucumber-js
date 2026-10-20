const { Then } = require('@cucumber/cucumber')

// Module state survives between retries of the same scenario within one run
let attempts = 0

Then('this step fails on the first attempt only', function () {
  attempts += 1
  if (attempts === 1) throw new Error(`Flaky failure on attempt ${attempts}`)
})
