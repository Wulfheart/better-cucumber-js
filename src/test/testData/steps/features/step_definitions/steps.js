const { Given, When, Then, defineParameterType } = require('@cucumber/cucumber')

defineParameterType({ name: 'color', regexp: /red|green|blue/ })
defineParameterType({ name: 'size', regexp: ['small', /big|huge/] })

Given('a calculator', function () {})

Given('the number {int}', function (number) {})

When('I say {string}', function (words) {})

Then(/^the result is (\d+)$/, function (result) {})

Then(/unanchored/, function () {})

Then(/^shouting$/i, function () {})

Given(`a template step`, function () {})

Given('a {color} ball', function (color) {})

Given('a {size} box', function (size) {})

Given('I have {int} cucumber(s) in my belly/stomach', function (count) {})

Given('the file a.txt', function () {})

Then('an ambiguous step', function () {})
Then(/^an? ambiguous step$/, function () {})

Given('a table:', function (dataTable) {})
