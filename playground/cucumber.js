// No `paths` here: cucumber-js appends CLI feature paths to configured ones instead of replacing them,
// which would make every gutter run execute all features. The default is features/**/*.feature anyway.
const common = {
  require: ['features/**/*.js'],
}

module.exports = {
  default: common,
  // Retries failing scenarios; use with features/flaky.feature
  retry: { ...common, retry: 2 },
}
