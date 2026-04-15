import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

pipeline {
    agent any

    environment {
        AWS_REGION      = 'ap-northeast-1'
        AWS_ACCOUNT_ID  = '854139532460'
        ECR_URI         = '854139532460.dkr.ecr.ap-northeast-1.amazonaws.com/aws-ms-lab/service-a'

        CLUSTER_NAME    = 'aws-ms-lab-cluster'
        SERVICE_NAME    = 'aws-ms-lab-service-a-task-service-priw3f2z'
        TASK_FAMILY     = 'aws-ms-lab-service-a-task'
        CONTAINER_NAME  = 'service-a-container'

        APP_DIR         = 'service-a'
        IMAGE_TAG       = "build-${BUILD_NUMBER}"
        IMAGE_URI       = "${ECR_URI}:${IMAGE_TAG}"

        // Jenkins Credentials ID
        AWS_CREDENTIALS_ID = 'aws-ms-lab-credentials'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Verify Tools') {
            steps {
                sh '''
                    set -e
                    aws --version
                    docker --version
                    mvn -v
                    git --version
					python3 --version
                '''
            }
        }

        stage('Build JAR') {
            steps {
                dir("${APP_DIR}") {
                    sh '''
                        set -e
                        mvn clean package -DskipTests
                    '''
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir("${APP_DIR}") {
                    sh '''
                        set -e
                        docker build -t service-a:${IMAGE_TAG} .
                        docker tag service-a:${IMAGE_TAG} ${IMAGE_URI}
                    '''
                }
            }
        }

        stage('Login ECR') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: "${AWS_CREDENTIALS_ID}"
                ]]) {
                    sh '''
                        set -e
                        aws ecr get-login-password --region ${AWS_REGION} \
                        | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
                    '''
                }
            }
        }

        stage('Push Image to ECR') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: "${AWS_CREDENTIALS_ID}"
                ]]) {
                    sh '''
                        set -e
                        docker push ${IMAGE_URI}
                    '''
                }
            }
        }

        stage('Export Current Task Definition') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: "${AWS_CREDENTIALS_ID}"
                ]]) {
                    sh '''
                        set -e
                        aws ecs describe-task-definition \
                          --task-definition ${TASK_FAMILY} \
                          --region ${AWS_REGION} \
                          --query 'taskDefinition' \
                          --output json > current-task-def.json
                    '''
                }
            }
        }

		stage('Prepare New Task Definition JSON') {
			steps {
				sh '''
					set -e

					cat > prepare_taskdef.py <<'PY'
		import json
		import os
		import sys

		container_name = os.environ.get("CONTAINER_NAME")
		image_uri = os.environ.get("IMAGE_URI")

		print("DEBUG CONTAINER_NAME =", container_name)
		print("DEBUG IMAGE_URI      =", image_uri)

		if not container_name:
			print("ERROR: CONTAINER_NAME is empty")
			sys.exit(1)

		if not image_uri:
			print("ERROR: IMAGE_URI is empty")
			sys.exit(1)

		with open("current-task-def.json", "r", encoding="utf-8") as f:
			task_def = json.load(f)

		containers = task_def.get("containerDefinitions", [])
		print("DEBUG container names in current-task-def.json =",
			  [c.get("name") for c in containers])

		matched = False

		for c in containers:
			print("DEBUG checking container =", c.get("name"), "image =", c.get("image"))
			if c.get("name") == container_name:
				print("DEBUG matched container =", container_name)
				print("DEBUG old image =", c.get("image"))
				c["image"] = image_uri
				print("DEBUG new image =", c.get("image"))
				matched = True

		if not matched:
			print(f"ERROR: container name [{container_name}] not found in task definition")
			sys.exit(1)

		register_payload = {
			"family": task_def["family"],
			"networkMode": task_def["networkMode"],
			"containerDefinitions": task_def["containerDefinitions"],
			"requiresCompatibilities": task_def["requiresCompatibilities"],
			"cpu": task_def["cpu"],
			"memory": task_def["memory"]
		}

		optional_fields = [
			"taskRoleArn",
			"executionRoleArn",
			"volumes",
			"placementConstraints",
			"runtimePlatform",
			"proxyConfiguration",
			"inferenceAccelerators",
			"ephemeralStorage",
			"pidMode",
			"ipcMode"
		]

		for field in optional_fields:
			if field in task_def and task_def[field] not in (None, [], {}, ""):
				register_payload[field] = task_def[field]

		with open("new-task-def.json", "w", encoding="utf-8") as f:
			json.dump(register_payload, f, indent=2)

		print("DEBUG new-task-def.json written successfully")
		PY

					python3 prepare_taskdef.py
					rm -f prepare_taskdef.py

					echo "===== CHECK new-task-def.json image ====="
					grep -n '"image"' new-task-def.json || true
				'''
			}
		}

		stage('Register New Task Definition Revision') {
			steps {
				withCredentials([[
					$class: 'AmazonWebServicesCredentialsBinding',
					credentialsId: 'aws-ms-lab-credentials'
				]]) {
					sh '''
						set -e

						aws ecs register-task-definition \
						  --cli-input-json file://new-task-def.json \
						  --region ${AWS_REGION} \
						  > register-output.json

						cat > extract_taskdef_arn.py <<'PY'
		import json

		with open("register-output.json", "r", encoding="utf-8") as f:
			data = json.load(f)

		arn = data["taskDefinition"]["taskDefinitionArn"]

		with open("new-taskdef-arn.txt", "w", encoding="utf-8") as f:
			f.write(arn)

		print("DEBUG registered taskDefinitionArn =", arn)
		PY

						python3 extract_taskdef_arn.py
						rm -f extract_taskdef_arn.py

						echo "===== CHECK register-output image ====="
						grep -n '"image"' register-output.json || true

						echo "===== CHECK new-taskdef-arn.txt ====="
						cat new-taskdef-arn.txt
					'''
				}
			}
		}

        stage('Update ECS Service') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: "${AWS_CREDENTIALS_ID}"
                ]]) {
                    sh '''
                        set -e
                        NEW_TASKDEF_ARN=$(cat new-taskdef-arn.txt)

                        aws ecs update-service \
                          --cluster ${CLUSTER_NAME} \
                          --service ${SERVICE_NAME} \
                          --task-definition ${NEW_TASKDEF_ARN} \
                          --region ${AWS_REGION}
                    '''
                }
            }
        }

        stage('Wait for Rollout') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: "${AWS_CREDENTIALS_ID}"
                ]]) {
                    sh '''
                        set -e
                        aws ecs wait services-stable \
                          --cluster ${CLUSTER_NAME} \
                          --services ${SERVICE_NAME} \
                          --region ${AWS_REGION}
                    '''
                }
            }
        }

        stage('Deployment Summary') {
            steps {
                sh '''
                    set -e
                    echo "Deployment completed."
                    echo "IMAGE_URI=${IMAGE_URI}"
                    echo "CLUSTER_NAME=${CLUSTER_NAME}"
                    echo "SERVICE_NAME=${SERVICE_NAME}"
                    echo "TASK_FAMILY=${TASK_FAMILY}"
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '*.json, *.txt', allowEmptyArchive: true
        }
        success {
            echo "service-a 已成功部署到 AWS ECS。"
        }
        failure {
            echo "部署失敗，請檢查 Jenkins console output、ECS Events、CloudWatch Logs。"
        }
    }
}