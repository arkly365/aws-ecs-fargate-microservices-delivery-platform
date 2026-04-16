pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
    }

    parameters {
        choice(
            name: 'SERVICE_MODULE',
            choices: ['service-a', 'service-b'],
            description: '選擇要部署的微服務'
        )
    }

    environment {
        AWS_REGION   = 'ap-northeast-1'
        AWS_ACCOUNT  = '854139532460'
        CLUSTER_NAME = 'aws-ms-lab-cluster'
    }

    stages {
        stage('Init Variables') {
            steps {
                script {
                    env.SERVICE_DIR = params.SERVICE_MODULE
                    env.IMAGE_TAG   = "build-${BUILD_NUMBER}"

                    if (params.SERVICE_MODULE == 'service-a') {
                        env.ECR_URI          = "${env.AWS_ACCOUNT}.dkr.ecr.${env.AWS_REGION}.amazonaws.com/aws-ms-lab/service-a"
                        env.ECS_SERVICE_NAME = 'aws-ms-lab-service-a-task-service-priw3f2z'
                        env.TASK_FAMILY      = 'aws-ms-lab-service-a-task'
                        env.CONTAINER_NAME   = 'service-a-container'
                    } else if (params.SERVICE_MODULE == 'service-b') {
                        env.ECR_URI          = "${env.AWS_ACCOUNT}.dkr.ecr.${env.AWS_REGION}.amazonaws.com/aws-ms-lab/service-b"
                        env.ECS_SERVICE_NAME = 'aws-ms-lab-service-b-task-service'   // ← 這裡改成你真正的 service-b ECS Service 名稱
                        env.TASK_FAMILY      = 'aws-ms-lab-service-b-task'
                        env.CONTAINER_NAME   = 'service-b-container'
                    } else {
                        error("Unsupported SERVICE_MODULE: ${params.SERVICE_MODULE}")
                    }

                    env.IMAGE_URI = "${env.ECR_URI}:${env.IMAGE_TAG}"

                    echo "SERVICE_DIR      = ${env.SERVICE_DIR}"
                    echo "ECR_URI          = ${env.ECR_URI}"
                    echo "ECS_SERVICE_NAME = ${env.ECS_SERVICE_NAME}"
                    echo "TASK_FAMILY      = ${env.TASK_FAMILY}"
                    echo "CONTAINER_NAME   = ${env.CONTAINER_NAME}"
                    echo "IMAGE_TAG        = ${env.IMAGE_TAG}"
                    echo "IMAGE_URI        = ${env.IMAGE_URI}"
                }
            }
        }

        stage('Clean Workspace') {
            steps {
                deleteDir()
            }
        }

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
                dir("${SERVICE_DIR}") {
                    sh '''
                        set -e
                        mvn clean package -DskipTests
                    '''
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    set -e
                    docker build -t ${IMAGE_URI} ./${SERVICE_DIR}
                '''
            }
        }

        stage('Login ECR') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-ms-lab-credentials'
                ]]) {
                    sh '''
                        set -e
                        aws ecr get-login-password --region ${AWS_REGION} | \
                        docker login --username AWS --password-stdin ${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com
                    '''
                }
            }
        }

        stage('Push Image to ECR') {
            steps {
                sh '''
                    set -e
                    docker push ${IMAGE_URI}
                '''
            }
        }

        stage('Export Current Task Definition') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-ms-lab-credentials'
                ]]) {
                    sh '''
                        set -e

                        aws ecs describe-task-definition \
                          --task-definition ${TASK_FAMILY} \
                          --region ${AWS_REGION} \
                          --query taskDefinition \
                          --output json \
                          > current-task-def.json

                        echo "===== CHECK current-task-def.json image ====="
                        grep -n '"image"' current-task-def.json || true
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
print("DEBUG container names in current-task-def.json =", [c.get("name") for c in containers])

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
                    credentialsId: 'aws-ms-lab-credentials'
                ]]) {
                    sh '''
                        set -e

                        echo "===== USING TASK DEF ARN ====="
                        cat new-taskdef-arn.txt

                        aws ecs update-service \
                          --cluster ${CLUSTER_NAME} \
                          --service ${ECS_SERVICE_NAME} \
                          --task-definition "$(cat new-taskdef-arn.txt)" \
                          --force-new-deployment \
                          --region ${AWS_REGION}
                    '''
                }
            }
        }

        stage('Wait for Rollout') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-ms-lab-credentials'
                ]]) {
                    sh '''
                        set -e

                        aws ecs wait services-stable \
                          --cluster ${CLUSTER_NAME} \
                          --services ${ECS_SERVICE_NAME} \
                          --region ${AWS_REGION}

                        echo "ECS service is stable now."
                    '''
                }
            }
        }

        stage('Deployment Summary') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-ms-lab-credentials'
                ]]) {
                    sh '''
                        set -e

                        echo "Deployment completed."
                        echo "SERVICE_MODULE=${SERVICE_DIR}"
                        echo "IMAGE_URI=${IMAGE_URI}"
                        echo "CLUSTER_NAME=${CLUSTER_NAME}"
                        echo "ECS_SERVICE_NAME=${ECS_SERVICE_NAME}"
                        echo "TASK_FAMILY=${TASK_FAMILY}"

                        echo "===== SERVICE SUMMARY ====="
                        aws ecs describe-services \
                          --cluster ${CLUSTER_NAME} \
                          --services ${ECS_SERVICE_NAME} \
                          --region ${AWS_REGION} \
                          --query 'services[0].{serviceName:serviceName,status:status,taskDefinition:taskDefinition,desiredCount:desiredCount,runningCount:runningCount}' \
                          --output table || true
                    '''
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'current-task-def.json,new-task-def.json,register-output.json,new-taskdef-arn.txt', allowEmptyArchive: true
        }
        success {
            echo "Pipeline 成功：${params.SERVICE_MODULE} 已成功部署到 AWS ECS。"
        }
        failure {
            echo "Pipeline 失敗：請檢查 console 與 artifacts。"
        }
    }
}