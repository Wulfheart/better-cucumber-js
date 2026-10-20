const { When, Then } = require('@cucumber/cucumber')

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

When('I wait {int} ms while logging progress', async function (ms) {
  const tick = 500
  for (let waited = 0; waited < ms; waited += tick) {
    console.log(`waited ${waited} of ${ms} ms`)
    await sleep(Math.min(tick, ms - waited))
  }
  console.log(`done after ${ms} ms`)
})

Then('a step that fails after {int} ms', async function (ms) {
  await sleep(ms)
  throw new Error(`Failed after ${ms} ms`)
})

// Longer than its own 1 s timeout, so cucumber-js fails it with a timeout error
Then('a step that exceeds its timeout', { timeout: 1000 }, async function () {
  await sleep(2000)
})
