pipeline {
  agent any
  stages {
    stage('Test AWS') {
      steps {
        withCredentials([[
          $class: 'AmazonWebServicesCredentialsBinding',
          credentialsId: 'aws-ms-lab-credentials'
        ]]) {
          sh '''
            aws sts get-caller-identity
          '''
        }
      }
    }
  }
}