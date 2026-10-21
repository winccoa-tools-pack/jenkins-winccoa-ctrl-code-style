pipeline {
  agent any
  stages {
    stage('Test') {
      steps {
        sh 'mvn -B clean test'
      }
    }
  }
}