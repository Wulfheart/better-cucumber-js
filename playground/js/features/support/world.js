const { setWorldConstructor, World } = require('@cucumber/cucumber')

class CalculatorWorld extends World {
  constructor(options) {
    super(options)
    this.numbers = []
    this.result = undefined
  }
}

setWorldConstructor(CalculatorWorld)
