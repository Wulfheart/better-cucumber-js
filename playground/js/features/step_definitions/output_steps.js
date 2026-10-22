const { Before, After, Given, Then } = require('@cucumber/cucumber')

// 1x1 red pixel
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==',
  'base64',
)

Given('the step writes {string} to stdout', function (text) {
  console.log(text)
})

Given('the step writes {string} to stderr', function (text) {
  console.error(text)
})

Given('the step writes {int} lines to stdout', function (count) {
  for (let i = 1; i <= count; i++) console.log(`line ${i} of ${count}`)
})

Then('the step writes stdout and stderr in turns', function () {
  for (let i = 1; i <= 3; i++) {
    console.log(`stdout ${i}`)
    console.error(`stderr ${i}`)
  }
})

Then('the step writes a line every {int} ms, {int} times', async function (ms, times) {
  for (let i = 1; i <= times; i++) {
    console.log(`tick ${i} of ${times}`)
    await new Promise((resolve) => setTimeout(resolve, ms))
  }
})

Given('the step logs {string}', function (text) {
  this.log(text)
})

Given('the step logs the table:', function (table) {
  this.log(JSON.stringify(table.raw()))
})

Given('the step attaches a text document', function () {
  this.attach('A plain text attachment\nwith two lines', 'text/plain')
})

Given('the step attaches JSON', function () {
  this.attach(JSON.stringify({ numbers: [1, 2, 3], ok: true }, null, 2), 'application/json')
})

Given('the step attaches a PNG image', function () {
  this.attach(PNG, 'image/png')
})

Before({ tags: '@output-hooks' }, function () {
  console.log('written by a Before hook')
  this.log('logged by a Before hook')
})

After({ tags: '@output-hooks' }, function () {
  console.log('written by an After hook')
  this.log('logged by an After hook')
})
