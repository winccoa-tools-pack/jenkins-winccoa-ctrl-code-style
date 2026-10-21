package org.winccoa.tools

import spock.lang.Specification

class CtrlStyleCheckSpec extends Specification {

  private Class helper

  def setup() {
    GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
    helper = loader.parseClass(new File('src/org/winccoa/tools/CtrlStyleCheck.groovy'))
  }

  def 'defaults to the worker as its only source and uses released checker defaults'() {
    when:
    Map config = helper.configuration([workerPath: 'UiFiles', version: '3.22'])

    then:
    config.workerPath == 'UiFiles'
    config.sourcePaths == ['UiFiles']
    config.packageVersion == '0.2.1'
    config.timeoutMs == 120000
    config.reportDirectory == '.ctl-style-reports'
    config.reportRoot == ''
    !config.showDiff
  }

  def 'installs the pinned npm package before invoking its CLI entry point'() {
    given:
    Map config = helper.configuration([version: '3.22', packageVersion: '0.2.1'])

    when:
    String install = helper.installCommand(config)
    String command = helper.command(config, 'UiFiles', 0)

    then:
    install.contains('npm install --prefix "$WORKSPACE/.ctl-style-tool"')
    install.contains("@winccoa-tools-pack/npm-winccoa-ctrl-code-style@0.2.1")
    command.contains('node "$cli" check "$worker"')
    !command.contains('npx --yes')
  }

  def 'global pipeline step parses with the helper available'() {
    when:
    GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
    loader.parseClass(new File('src/org/winccoa/tools/CtrlStyleCheck.groovy'))
    Class step = loader.parseClass(new File('vars/winccoaCtrlStyleCheck.groovy'))

    then:
    step.name == 'winccoaCtrlStyleCheck'
  }

  def 'first source registers and later sources reuse the worker config'() {
    given:
    Map config = helper.configuration([workerPath: 'UiFiles', sourcePaths: ['UiFiles', 'Test'], version: '3.22'])

    when:
    String first = helper.command(config, 'UiFiles', 0)
    String second = helper.command(config, 'Test', 1)

    then:
    first.contains('check "$worker"')
    !first.contains('--no-register')
    second.contains('--no-register')
    first.contains('--report-format sarif')
    first.contains('report_root="$(dirname -- "$worker")"')
    first.contains('--source "$source"')
  }

  def 'uses the worker parent when report root is omitted for a symlinked worker'() {
    when:
    Map config = helper.configuration([workerPath: '.oa-style-checkout/UiFiles',
                       sourcePaths: ['.oa-style-checkout/UiFiles'], version: '3.22'])

    then:
    config.reportRoot == ''
    String command = helper.command(config, config.sourcePaths[0], 0)
    command.contains('report_root_path=')
    command.contains('report_root="$(dirname -- "$worker")"')
  }

  def 'quotes an explicit report root as a workspace-relative path'() {
    when:
    Map config = helper.configuration([workerPath: 'project/worker', reportRoot: 'project',
                                       version: '3.22'])
    String command = helper.command(config, 'project/source', 0)

    then:
    command.contains("report_root_path='project'")
    command.contains('report_root="$(realpath -- "$WORKSPACE/$report_root_path")"')
  }

  def 'quotes single quotes in shell arguments'() {
    expect:
    helper.shellQuote("path/with'quote") == "'path/with'\"'\"'quote'"
  }

  def 'rejects paths that escape the workspace'() {
    when:
    helper.configuration([workerPath: '../outside', version: '3.22'])

    then:
    thrown(IllegalArgumentException)
  }

  def 'classifies clean SARIF, formatting findings, and execution failures'() {
    given:
    String clean = '{"version":"2.1.0","runs":[{"results":[]}]}'
    String finding = '{"version":"2.1.0","runs":[{"results":[{"ruleId":"format"}]}]}'
    String failedRun = '{"version":"2.1.0","runs":[{"results":[],"invocations":[{"executionSuccessful":false}]}]}'

    expect:
    helper.result(0, clean) == 'SUCCESS'
    helper.result(2, finding) == 'UNSTABLE'
    helper.result(0, failedRun) == 'FAILURE'
    helper.result(2, null) == 'FAILURE'
  }
}