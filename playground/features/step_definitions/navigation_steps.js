const assert = require('node:assert')
const { Given, Then, defineParameterType } = require('@cucumber/cucumber')

defineParameterType({
  name: 'color',
  regexp: /red|green|blue/,
  transformer: (name) => name.toUpperCase(),
})

// regexp as a string, and as an array of strings and regexes (alternatives)
defineParameterType({ name: 'shape', regexp: 'circle|square' })
defineParameterType({ name: 'size', regexp: ['small', /big|huge/] })

Given('a {size} {shape}', function (size, shape) {
  this.thing = `${size} ${shape}`
})

// Several capture groups, a named group and an optional group
Then(/^it is (?<size>small|big|huge) and (circle|square)( indeed)?$/, function (size, shape, indeed) {
  assert.strictEqual(this.thing, `${size} ${shape}`)
})

// Not literals, so the IDE can't resolve these steps (known limitation); cucumber-js runs them fine
const builtPattern = new RegExp('^a step with a built regex$')
Given(builtPattern, function () {})

Given('a {color} ball', function (color) {
  this.color = color
})

Then(/^the ball is (RED|GREEN|BLUE)$/, function (color) {
  assert.strictEqual(this.color, color)
})

Given(`a step defined with a template literal`, function () {})

Given('I have {int} cucumber(s) in my belly/stomach', function (count) {
  this.cucumbers = count
})

Then(/many cucumbers/i, function () {
  assert.ok(this.cucumbers > 1)
})
