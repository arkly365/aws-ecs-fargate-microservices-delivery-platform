pipeline {
    agent any

    parameters {
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'Optional rollback tag, e.g. build-8')
    }

    environment {
        IMAGE_NAME = 'arkly365/sample-java-app'
        PRIVATE_REGISTRY_IMAGE = 'localhost:5000/sample-java-app'
    }

    stages {
	
		stage('Init') {
			steps {
				echo 'Pipeline started'
				echo "Branch = ${env.BRANCH_NAME}"
				echo "ROLLBACK_TAG = ${params.ROLLBACK_TAG}"
			}
		}

        stage('Check Tools') {
            steps {
                sh 'git --version'
                sh 'mvn -version'
                sh 'docker --version'
                sh 'docker-compose version'
            }
        }

        stage('Maven Test') {
            when {
                expression { return !params.ROLLBACK_TAG?.trim() }
            }
            steps {
                sh 'mvn clean test'
            }
        }

        stage('SonarQube Scan') {
            when {
                expression { return !params.ROLLBACK_TAG?.trim() }
            }
            steps {
                withSonarQubeEnv('sonarqube-local') {
                    sh '''
                        mvn sonar:sonar \
                          -Dsonar.projectKey=sample-java-app \
                          -Dsonar.projectName=sample-java-app
                    '''
                }
            }
        }

        stage('Maven Package') {
            when {
                expression { return !params.ROLLBACK_TAG?.trim() }
            }
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker Build') {
            when {
                expression { return !params.ROLLBACK_TAG?.trim() }
            }
            steps {
                sh 'docker build -t sample-java-app:build-${BUILD_NUMBER} .'
            }
        }

        stage('Trivy Report') {
            when {
                expression { return !params.ROLLBACK_TAG?.trim() }
            }
            steps {
                sh '''
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v trivy_cache:/root/.cache/ \
                      aquasec/trivy:0.62.0 image \
                      --scanners vuln \
                      --severity HIGH,CRITICAL \
                      --ignore-unfixed \
                      --no-progress \
                      --format table \
                      --exit-code 0 \
                      sample-java-app:build-${BUILD_NUMBER} \
                      > trivy-image-report.txt
                '''
                sh 'test -f trivy-image-report.txt'
            }
        }

        stage('Push to Private Registry') {
            when {
                expression { return !params.ROLLBACK_TAG?.trim() }
            }
            steps {
                script {
                    def stableTag = (env.BRANCH_NAME == 'master') ? 'master-latest' : 'develop-latest'

                    sh """
                        docker tag sample-java-app:build-${BUILD_NUMBER} ${PRIVATE_REGISTRY_IMAGE}:build-${BUILD_NUMBER}
                        docker tag sample-java-app:build-${BUILD_NUMBER} ${PRIVATE_REGISTRY_IMAGE}:${stableTag}

                        docker push ${PRIVATE_REGISTRY_IMAGE}:build-${BUILD_NUMBER}
                        docker push ${PRIVATE_REGISTRY_IMAGE}:${stableTag}
                    """
                }
            }
        }

		stage('Deploy with Docker Compose') {
			steps {
				script {
					def deployTag
					def composeFile
					def verifyUrl

					if (params.ROLLBACK_TAG?.trim()) {
						deployTag = params.ROLLBACK_TAG.trim()
					} else {
						deployTag = (env.BRANCH_NAME == 'master') ? 'master-latest' : 'develop-latest'
					}

					if (env.BRANCH_NAME == 'master') {
						composeFile = 'deploy/docker-compose.prod.yml'
						verifyUrl = 'http://host.docker.internal:8082/hello'
					} else if (env.BRANCH_NAME == 'develop') {
						composeFile = 'deploy/docker-compose.dev.yml'
						verifyUrl = 'http://host.docker.internal:8081/hello'
					} else {
						echo "Skip deploy for branch: ${env.BRANCH_NAME}"
						return
					}

					echo "Deploy tag = ${deployTag}"
					echo "Compose file = ${composeFile}"
					echo "Verify URL = ${verifyUrl}"

					sh """
						export IMAGE_TAG='${deployTag}'
						export APP_BRANCH='${env.BRANCH_NAME}'
						docker-compose -f ${composeFile} up -d --remove-orphans
					"""
				}
			}
		}

		stage('Verify Deployment') {
			steps {
				script {
					if (env.BRANCH_NAME == 'master') {
						sh '''
							for i in 1 2 3 4 5; do
							  echo "Verify attempt $i"
							  curl -f http://host.docker.internal:8082/hello && \
							  curl -f http://host.docker.internal:8082/version && exit 0
							  sleep 5
							done
							exit 1
						'''
					} else if (env.BRANCH_NAME == 'develop') {
						sh '''
							for i in 1 2 3 4 5; do
							  echo "Verify attempt $i"
							  curl -f http://host.docker.internal:8081/hello && \
							  curl -f http://host.docker.internal:8081/version && exit 0
							  sleep 5
							done
							exit 1
						'''
					} else {
						echo "Skip verify for branch: ${env.BRANCH_NAME}"
					}
				}
			}
		}

		
		stage('OWASP ZAP Baseline Scan') {
			steps {
				script {
					def targetUrl = ''
					def reportFile = ''

					if (env.BRANCH_NAME == 'master') {
						targetUrl = 'http://host.docker.internal:8082/hello'
						reportFile = 'zap-baseline-prod.txt'
					} else if (env.BRANCH_NAME == 'develop') {
						targetUrl = 'http://host.docker.internal:8081/hello'
						reportFile = 'zap-baseline-dev.txt'
					} else {
						echo "Skip ZAP scan for branch: ${env.BRANCH_NAME}"
						return
					}

					echo "ZAP target = ${targetUrl}"

					sh """
						docker run --rm \
						  --user root \
						  -v "$WORKSPACE:/zap/wrk" \
						  ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
						  -t ${targetUrl} \
						  -m 1 \
						  2>&1 | tee ${reportFile} || true
					"""

					sh 'ls -la'
					sh 'test -f ${reportFile}'
				}
			}
		}
		
        
    }

    post {
        always {
			archiveArtifacts artifacts: 'trivy-image-report.txt, zap-baseline-*.txt', fingerprint: true, allowEmptyArchive: true
		}
		
    }
}