module.exports = {
  plugins: {
    autoprefixer: {
      // Suppress warnings about newer CSS values like 'end' vs 'flex-end'
      // This is particularly useful for third-party libraries like ag-grid
      ignoreUnknownVersions: true,
      // You can also add specific browser targets if needed
      // overrideBrowserslist: ['> 1%', 'last 2 versions', 'not dead']
    },
  },
}
