import { defineVitestConfig } from '@nuxt/test-utils/config'

export default defineVitestConfig({
  test: {
    // Two environments on purpose. Pure units — utils, pricing-adjacent helpers, formatters — run
    // in plain node and stay in the millisecond range, which is what makes a red/green/refactor
    // loop usable. Only tests that genuinely need a Nuxt runtime opt in with:
    //
    //   // @vitest-environment nuxt
    //
    // at the top of the file. Making `nuxt` the default would put a full app bootstrap in front of
    // every assertion and the loop stops being tight enough to write tests first.
    environment: 'node',
    include: ['app/**/*.spec.ts', 'test/**/*.spec.ts'],
  },
})
