package org.winccoa.tools

import groovy.json.JsonSlurperClassic

/** Configuration and result handling for the public Jenkins pipeline step. */
class CtrlStyleCheck implements Serializable {

  static Map configuration(Map options) {
    Set<String> supported = ['workerPath', 'sourcePaths', 'version', 'packageVersion', 'timeoutMs',
                             'reportDirectory', 'reportRoot', 'showDiff'] as Set
    if (options.keySet().any { !supported.contains(it) }) {
      throw new IllegalArgumentException('Unsupported option; supported options: ' + supported.join(', '))
    }

    String workerPath = safeRelativePath(options.workerPath ?: '.')
    List sourcePaths = options.sourcePaths == null ? [workerPath] : options.sourcePaths
    if (!(sourcePaths instanceof List) || sourcePaths.isEmpty()) {
      throw new IllegalArgumentException('sourcePaths must be a non-empty list of workspace-relative paths')
    }
    sourcePaths = sourcePaths.collect { Object path -> safeRelativePath(path) }

    String version = options.version?.toString()?.trim()
    if (!(version ==~ /\d+\.\d+/)) {
      throw new IllegalArgumentException('version must be a WinCC OA major.minor version, for example 3.21')
    }
    String packageVersion = (options.packageVersion ?: '0.2.1').toString().trim()
    if (!(packageVersion ==~ /\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?/)) {
      throw new IllegalArgumentException('packageVersion must be an exact published version')
    }
    String timeoutMs = (options.timeoutMs == null ? 120000 : options.timeoutMs).toString()
    if (!(timeoutMs ==~ /[1-9]\d{0,8}/)) {
      throw new IllegalArgumentException('timeoutMs must be a positive integer below 1000000000')
    }
    String reportDirectory = safeRelativePath(options.reportDirectory ?: '.ctl-style-reports')
    String reportRoot = options.reportRoot == null ? '' : safeRelativePath(options.reportRoot)
    boolean showDiff = options.showDiff == null ? true : options.showDiff as boolean

    return [workerPath: workerPath, sourcePaths: sourcePaths, version: version,
            packageVersion: packageVersion, timeoutMs: timeoutMs.toInteger(),
            reportDirectory: reportDirectory, reportRoot: reportRoot, showDiff: showDiff]
  }

  static String installCommand(Map config) {
    return '''npm install --prefix "$WORKSPACE/.ctl-style-tool" --no-save --no-audit --no-fund ''' +
      shellQuote('@winccoa-tools-pack/npm-winccoa-ctrl-code-style@' + config.packageVersion) + '''
test -f "$WORKSPACE/.ctl-style-tool/node_modules/@winccoa-tools-pack/npm-winccoa-ctrl-code-style/dist/cjs/cli.js"
'''
  }

  static String command(Map config, String sourcePath, int index) {
    String reportName = index + '-' + sourcePath.replaceAll(/[^A-Za-z0-9._-]+/, '_')
    String reportPath = config.reportDirectory + '/' + reportName + '.sarif'
    String logPath = config.reportDirectory + '/' + reportName + '.log'
    String registrationOption = index == 0 ? '' : ' --no-register'
    String diffOption = config.showDiff ? '' : ' --no-diff'

    return '''set +e
  report_directory=''' + shellQuote(config.reportDirectory) + '''
  mkdir -p -- "$WORKSPACE/$report_directory"
worker_path=''' + shellQuote(config.workerPath) + '''
source_path=''' + shellQuote(sourcePath) + '''
report_root_path=''' + shellQuote(config.reportRoot) + '''
report_path=''' + shellQuote(reportPath) + '''
log_path=''' + shellQuote(logPath) + '''
worker="$(realpath -- "$WORKSPACE/$worker_path")"
source="$WORKSPACE/$source_path"
if [ -n "$report_root_path" ]; then
  report_root="$WORKSPACE/$report_root_path"
else
  report_root="$(dirname -- "$worker")"
fi
cli="$WORKSPACE/.ctl-style-tool/node_modules/@winccoa-tools-pack/npm-winccoa-ctrl-code-style/dist/cjs/cli.js"
test -f "$cli" || { echo "Style checker CLI not installed: $cli" >&2; exit 127; }
status=0
GITHUB_WORKSPACE="$WORKSPACE" node "$cli" check "$worker" -v ''' + shellQuote(config.version) + ' -t ' +
      shellQuote(config.timeoutMs.toString()) + ''' --report-root "$report_root" \
  --report-format sarif --report-file "$WORKSPACE/$report_path"''' +
      registrationOption + diffOption + ''' --source "$source" > "$WORKSPACE/$log_path" 2>&1 || status=$?
cat "$WORKSPACE/$log_path"
exit "$status"
'''
  }

  static String reportPath(Map config, String sourcePath, int index) {
    String reportName = index + '-' + sourcePath.replaceAll(/[^A-Za-z0-9._-]+/, '_')
    return config.reportDirectory + '/' + reportName + '.sarif'
  }

  static String result(int status, Object report) {
    if (!(report instanceof Map) || report.version != '2.1.0' ||
        !(report.runs instanceof List) || report.runs.isEmpty()) {
      return 'FAILURE'
    }
    int findings = 0
    for (def run : report.runs) {
      if (!(run instanceof Map) || !(run.results instanceof List) ||
          (run.invocations != null && !(run.invocations instanceof List)) ||
          run.invocations?.any { !(it instanceof Map) || it.executionSuccessful == false }) {
        return 'FAILURE'
      }
      findings += run.results.size()
    }
    if (status == 0 && findings == 0) {
      return 'SUCCESS'
    }
    return status == 2 && findings > 0 ? 'UNSTABLE' : 'FAILURE'
  }

  static String shellQuote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'"
  }

  private static String safeRelativePath(Object value) {
    String path = value?.toString()?.trim()?.replace('\\', '/')
    if (!path || path.startsWith('/') || path ==~ /^[A-Za-z]:.*/ ||
        path.split('/').any { it == '..' } || path.contains('\n') || path.contains('\r')) {
      throw new IllegalArgumentException('Paths must be non-empty and stay inside the Jenkins workspace')
    }
    return path.replaceAll('^\\./', '')
  }
}