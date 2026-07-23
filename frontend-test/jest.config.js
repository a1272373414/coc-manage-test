module.exports = {
  testEnvironment: 'jsdom',
  testMatch: ['**/tests/**/*.test.js'],
  setupFiles: ['<rootDir>/setup.js'],
  verbose: true,
  testTimeout: 10000
};
