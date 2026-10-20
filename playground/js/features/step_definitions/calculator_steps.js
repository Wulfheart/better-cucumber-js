const assert = require('node:assert')
const { Given, When, Then } = require('@cucumber/cucumber')

Given('a calculator', function () {
  this.numbers = []
})

Given('the number {int}', function (n) {
  this.numbers.push(n)
})

Given('the numbers:', function (table) {
  this.numbers.push(...table.raw().flat().map(Number))
})

Given('the expression:', function (docString) {
  this.numbers.push(...docString.split('+').map((s) => Number(s.trim())))
})

When('I add them', function () {
  this.result = this.numbers.reduce((a, b) => a + b, 0)
})

When('I multiply them', function () {
  this.result = this.numbers.reduce((a, b) => a * b, 1)
})

Then('the result is {int}', function (expected) {
  assert.strictEqual(this.result, expected)
})
