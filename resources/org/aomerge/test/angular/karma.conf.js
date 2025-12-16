module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-junit-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma')
    ],
    client: {
      clearContext: false // deja visible el output Jasmine en navegador
    },
    browsers: ['ChromeHeadlessNoSandbox'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-dev-shm-usage']
      }
    },
    singleRun: true, // Termina tras una corrida (ideal para CI/containers)
    reporters: ['progress', 'junit', 'coverage'],
    junitReporter: {
      outputDir: '/app/test-results', // Carpeta donde se guardan los XML
      outputFile: 'junit-results.xml',
      useBrowserName: false
    },
    restartOnFileChange: true
  });
};