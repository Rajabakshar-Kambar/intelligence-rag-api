pipeline {
    agent any

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        ECR_REGISTRY    = credentials('ecr-registry-url')
        ECR_REPO        = 'cloudspring-intelligence-rag-api'
        AWS_REGION      = 'ap-south-1'
        IMAGE_TAG       = "${env.GIT_COMMIT[0..7]}"
        IMAGE_FULL      = "${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
        IMAGE_LATEST    = "${ECR_REGISTRY}/${ECR_REPO}:latest"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh './mvnw clean verify -B'
            }
            post {
                always {
                    junit 'target/surefire-reports/**/*.xml'
                    publishHTML(target: [
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Coverage'
                    ])
                }
            }
        }

        stage('Security Scan') {
            steps {
                // OWASP Dependency-Check — fails build on CVSS >= 7
                sh '''
                    ./mvnw org.owasp:dependency-check-maven:check \
                        -DfailBuildOnCVSS=7 \
                        -DskipTestScope=true \
                        -B
                '''
            }
            post {
                always {
                    dependencyCheckPublisher pattern: 'target/dependency-check-report.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE_FULL} -t ${IMAGE_LATEST} ."
            }
        }

        stage('Push to ECR') {
            when {
                anyOf {
                    branch 'main'
                    branch 'release/*'
                }
            }
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'aws-ecr-credentials',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} | \
                            docker login --username AWS --password-stdin ${ECR_REGISTRY}
                        docker push ${IMAGE_FULL}
                        docker push ${IMAGE_LATEST}
                    """
                }
            }
        }

        stage('Deploy to Staging') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'aws-ecr-credentials',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    sh """
                        aws ecs update-service \
                            --cluster cloudspring-staging \
                            --service rag-api \
                            --force-new-deployment \
                            --region ${AWS_REGION}

                        aws ecs wait services-stable \
                            --cluster cloudspring-staging \
                            --services rag-api \
                            --region ${AWS_REGION}
                    """
                }
            }
        }

        stage('Smoke Test Staging') {
            when { branch 'main' }
            environment {
                STAGING_URL    = credentials('staging-api-url')
                STAGING_APIKEY = credentials('staging-api-key')
            }
            steps {
                sh """
                    STATUS=\$(curl -s -o /dev/null -w "%{http_code}" \
                        -H "X-Api-Key: ${STAGING_APIKEY}" \
                        "${STAGING_URL}/actuator/health")
                    if [ "\$STATUS" != "200" ]; then
                        echo "Staging health check failed with status \$STATUS"
                        exit 1
                    fi
                    echo "Staging health check passed."
                """
            }
        }

        stage('Deploy to Production') {
            when { branch 'release/*' }
            input {
                message "Deploy ${IMAGE_TAG} to production?"
                ok "Deploy"
                submitter "tech-lead,devops"
            }
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'aws-ecr-credentials',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    sh """
                        aws ecs update-service \
                            --cluster cloudspring-production \
                            --service rag-api \
                            --force-new-deployment \
                            --region ${AWS_REGION}

                        aws ecs wait services-stable \
                            --cluster cloudspring-production \
                            --services rag-api \
                            --region ${AWS_REGION}
                    """
                }
            }
        }
    }

    post {
        failure {
            emailext(
                to: 'engineering@cloudspring.com',
                subject: "Pipeline FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build failed.
Branch: ${env.BRANCH_NAME}
Commit: ${env.GIT_COMMIT}
See: ${env.BUILD_URL}"""
            )
        }
        always {
            cleanWs()
        }
    }
}