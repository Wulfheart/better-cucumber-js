const { Before, After } = require('@cucumber/cucumber')

Before(function (testCase) {
  this.scenarioName = testCase.pickle.name
})

After(function () {
  this.numbers = []
})

Before({ tags: '@failing-before' }, function () {
  throw new Error('Before hook failed on purpose')
})

After({ tags: '@failing-after' }, function () {
  throw new Error('After hook failed on purpose')
})
