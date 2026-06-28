pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven-3.9.12'
    }

    environment {
        APP_NAME = "employee-backend.jar"
    }

    stages {

        stage('Checkout') {
            steps {
                echo "Source code already checked out by Jenkins."
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                pkill -f "java -jar" || true
                cp target/*.jar ~/deploy/backend/$APP_NAME
                nohup java -jar ~/deploy/backend/$APP_NAME > ~/logs/backend.log 2>&1 &
                '''
            }
        }
    }

    post {
        success {
            echo 'Backend deployed successfully.'
        }

        failure {
            echo 'Backend build failed.'
        }
    }
}
