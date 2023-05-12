pipeline {
    agent { label 'Slavik' }
    stages {
        stage('Stop stack') {
            steps {
                sh 'docker compose down'
            }
        }
        stage('Clear docker') {
            steps {
                sh 'docker system prune -af'
            }
        }
        stage('Delete files') {
            steps {
                sh 'rm docker-compose.yml'
                sh 'rm Dockerfile'
            }
        }
        stage('Copy new files') {
            steps {
                sh 'cp /var/jenkins/infra/docker_compose.yml docker-compose.yml'
                sh 'cp /var/jenkins/infra/Dockerfile Dockerfile'
            }
        }
        stage('Build and start stack') {
            steps {
                sh 'docker compose up -d --env-file=.env'
            }
        }
    }
}