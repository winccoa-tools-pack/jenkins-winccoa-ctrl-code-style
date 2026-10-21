#!groovy

import org.winccoa.tools.CtrlStyleCheck

/** Runs the WinCC OA CTL formatter check on the current Linux agent/container. */
void call(Map options = [:]) {
  Map config = CtrlStyleCheck.configuration(options)
  boolean failed = false
  boolean findings = false

  dir(config.reportDirectory) {
    deleteDir()
  }

  try {
    stage('Install CTL style checker') {
      dir('.ctl-style-tool') {
        deleteDir()
      }
      sh(script: CtrlStyleCheck.installCommand(config))
    }
    config.sourcePaths.eachWithIndex { String sourcePath, int index ->
      stage('CTL style ' + sourcePath) {
        String report = CtrlStyleCheck.reportPath(config, sourcePath, index)
        int status = sh(returnStatus: true, script: CtrlStyleCheck.command(config, sourcePath, index))
        String outcome = CtrlStyleCheck.result(status, fileExists(report) ? readFile(report) : null)
        failed = failed || outcome == 'FAILURE'
        findings = findings || outcome == 'UNSTABLE'
        if (fileExists(report)) {
          recordIssues(enabledForFailure: true, tools: [sarif(pattern: report,
            id: 'winccoa-ctl-style-' + index, name: 'WinCC OA CTL Style ' + sourcePath)])
        }
        echo('CTL style ' + sourcePath + ': ' + outcome + ' (exit ' + status + ')')
      }
    }
  } finally {
    archiveArtifacts(artifacts: config.reportDirectory + '/*', allowEmptyArchive: true)
  }

  if (failed) {
    error('WinCC OA CTL style execution failed; inspect the archived logs and reports')
  }
  if (findings) {
    unstable('WinCC OA CTL formatting changes required; see the SARIF reports')
  }
}