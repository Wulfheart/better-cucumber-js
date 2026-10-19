const { Before, After } = require('@cucumber/cucumber')

Before(function (testCase) {
  this.scenarioName = testCase.pickle.name
})

After(function () {
  this.numbers = []
})
