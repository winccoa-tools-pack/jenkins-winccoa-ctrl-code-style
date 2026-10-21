# jenkins-winccoa-ctrl-code-style

Jenkins Shared Library step for running
[`@winccoa-tools-pack/npm-winccoa-ctrl-code-style`](https://github.com/winccoa-tools-pack/npm-winccoa-ctrl-code-style)
on an existing Linux agent or container.

The library keeps Jenkins orchestration small and leaves agent allocation,
checkout, Docker image selection, and credentials to the consuming Jenkinsfile.
The first source check registers the runnable worker and bundled StyleCheck
project and starts CTRL in that same CLI invocation. Additional checks run
serially with `--no-register` against the registered worker.

## Add the library

Configure this repository as a Jenkins Global Pipeline Library named
`winccoa-ctrl-style`, then call the `winccoaCtrlStyleCheck` global step.

## Example

The agent/container must contain the requested WinCC OA version, Node.js 20 or
newer, npm, and access to the npm package registry.

```groovy
pipeline {
  agent {
    docker {
      image 'ghcr.io/winccoa-tools-pack/winccoa-images:3.21.6-debian12-amd64-all'
      args '-v /var/run/docker.sock:/var/run/docker.sock'
    }
  }
  stages {
    stage('CTL style') {
      steps {
        winccoaCtrlStyleCheck(
          workerPath: 'UiFiles',
          sourcePaths: ['UiFiles', 'Test', 'Subprojects', 'AddOns'],
          reportRoot: '.',
          version: '3.21',
          packageVersion: '0.2.1'
        )
      }
    }
  }
}
```

If Docker is managed by the consuming job, the library step can run inside its
existing `docker.image(...).inside { ... }` block or on a preconfigured Linux
agent. The library itself does not start nested containers.

## Step options

| Option | Required | Default | Description |
| --- | --- | --- | --- |
| `workerPath` | No | `.` | Runnable WinCC OA worker project, relative to workspace |
| `sourcePaths` | No | `[workerPath]` | Non-empty list of source trees, relative to workspace |
| `version` | Yes | - | WinCC OA major.minor version, such as `3.21` |
| `packageVersion` | No | `0.2.1` | Exact published npm checker version |
| `timeoutMs` | No | `120000` | Timeout passed to WCCOActrl |
| `reportDirectory` | No | `.ctl-style-reports` | Workspace-relative log and SARIF directory |
| `reportRoot` | No | Worker parent | Optional workspace-relative root used for SARIF source locations |
| `showDiff` | No | `false` | Include formatter diffs in captured logs |

All configured paths must stay inside the Jenkins workspace. The package
creates one SARIF report and one log per source path. Formatting findings make
the build `UNSTABLE`; invalid/missing reports and execution failures fail it.
Reports are published with Warnings NG and archived after all scans.
The `.ctl-style-tool` installation is retained because the registered worker
config references the bundled StyleCheck sub-project inside that directory.

## Development

Requirements: Java 17 or newer and Maven.

```powershell
mvn -B clean test
```

The Spock tests validate configuration, path safety, serial registration
behavior, report classification, and shell quoting without a live WinCC OA
installation.