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
    !config.showDiff
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
    first.contains('--source "$source"')
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