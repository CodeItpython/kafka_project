pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
    }

    triggers {
        pollSCM('* * * * *')
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage('Verify Backend') {
            steps {
                dir('backend') {
                    sh './gradlew test'
                }
            }
        }

        stage('Verify Frontend') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }
    }

    post {
        success {
            build job: 'kafka-chat-local-deploy', wait: false, propagate: false
        }
    }
}
