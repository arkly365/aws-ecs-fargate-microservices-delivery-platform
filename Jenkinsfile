pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
    }

	parameters {
		choice(
			name: 'DEPLOY_MODE',
			choices: ['AUTO', 'MANUAL'],
			description: 'AUTO=根據本次 commit 自動判斷；MANUAL=手動指定服務'
		)

		choice(
			name: 'SERVICE_MODULE',
			choices: ['service-a', 'service-b'],
			description: 'MANUAL 模式時，要部署的微服務'
		)
	}

    environment {
        AWS_REGION   = 'ap-northeast-1'
        AWS_ACCOUNT  = '854139532460'
        CLUSTER_NAME = 'aws-ms-lab-cluster'
    }

    stages {
        stage('Clean Workspace') {
            steps {
                deleteDir()
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
                sh '''
                    set -e
                    git --no-pager log --oneline -n 3 || true
                '''
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

		stage('Detect Changed Services') {
			steps {
				script {
					env.DEPLOY_SERVICE_A = 'false'
					env.DEPLOY_SERVICE_B = 'false'

					def causes = currentBuild.rawBuild.getCauses()
					echo "Build causes: ${causes}"

					if (params.DEPLOY_MODE == 'MANUAL') {
						echo "DEPLOY_MODE=MANUAL"

						if (params.SERVICE_MODULE == 'service-a') {
							env.DEPLOY_SERVICE_A = 'true'
						} else if (params.SERVICE_MODULE == 'service-b') {
							env.DEPLOY_SERVICE_B = 'true'
						} else {
							error("Unsupported SERVICE_MODULE: ${params.SERVICE_MODULE}")
						}

					} else {
						echo "DEPLOY_MODE=AUTO"

						def changedFiles = sh(
							script: '''
								set +e

								if git rev-parse HEAD~1 >/dev/null 2>&1; then
								  git diff --name-only HEAD~1 HEAD
								else
								  git ls-files
								fi
							''',
							returnStdout: true
						).trim()

						echo "Changed files:\n${changedFiles}"

						def changedList = changedFiles ? changedFiles.split("\\n") : []

						if (changedList.any { it.startsWith('service-a/') }) {
							env.DEPLOY_SERVICE_A = 'true'
						}

						if (changedList.any { it.startsWith('service-b/') }) {
							env.DEPLOY_SERVICE_B = 'true'
						}
					}

					echo "DEPLOY_SERVICE_A=${env.DEPLOY_SERVICE_A}"
					echo "DEPLOY_SERVICE_B=${env.DEPLOY_SERVICE_B}"

					if (env.DEPLOY_SERVICE_A != 'true' && env.DEPLOY_SERVICE_B != 'true') {
						echo 'No service changes detected. Deployment will be skipped.'
					}
				}
			}
		}

        stage('Deploy service-a') {
            when {
                expression { env.DEPLOY_SERVICE_A == 'true' }
            }
            steps {
                script {
                    deployService('service-a')
                }
            }
        }

        stage('Deploy service-b') {
            when {
                expression { env.DEPLOY_SERVICE_B == 'true' }
            }
            steps {
                script {
                    deployService('service-b')
                }
            }
        }

        stage('No Deployment Needed') {
            when {
                expression { env.DEPLOY_SERVICE_A != 'true' && env.DEPLOY_SERVICE_B != 'true' }
            }
            steps {
                echo '沒有偵測到 service-a / service-b 變更，跳過部署。'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/current-task-def.json,**/new-task-def.json,**/register-output.json,**/new-taskdef-arn.txt', allowEmptyArchive: true
        }
        success {
            echo "Pipeline 成功。DEPLOY_MODE=${params.DEPLOY_MODE}"
        }
        failure {
            echo 'Pipeline 失敗，請檢查 console 與 artifacts。'
        }
    }
}

/* =========================
   共用部署函式
   ========================= */
def deployService(String serviceModule) {
    def config = getServiceConfig(serviceModule)

    env.SERVICE_DIR       = config.SERVICE_DIR
    env.ECR_URI           = config.ECR_URI
    env.ECS_SERVICE_NAME  = config.ECS_SERVICE_NAME
    env.TASK_FAMILY       = config.TASK_FAMILY
    env.CONTAINER_NAME    = config.CONTAINER_NAME
    env.IMAGE_TAG         = "build-${env.BUILD_NUMBER}"
    env.IMAGE_URI         = "${env.ECR_URI}:${env.IMAGE_TAG}"

    echo "===== DEPLOY CONFIG (${serviceModule}) ====="
    echo "SERVICE_DIR      = ${env.SERVICE_DIR}"
    echo "ECR_URI          = ${env.ECR_URI}"
    echo "ECS_SERVICE_NAME = ${env.ECS_SERVICE_NAME}"
    echo "TASK_FAMILY      = ${env.TASK_FAMILY}"
    echo "CONTAINER_NAME   = ${env.CONTAINER_NAME}"
    echo "IMAGE_TAG        = ${env.IMAGE_TAG}"
    echo "IMAGE_URI        = ${env.IMAGE_URI}"

    dir("${env.SERVICE_DIR}") {
        sh '''
            set -e
            mvn clean package -DskipTests
        '''
    }

    sh """
        set -e
        docker build -t ${env.IMAGE_URI} ./${env.SERVICE_DIR}
    """

    withCredentials([[
        $class: 'AmazonWebServicesCredentialsBinding',
        credentialsId: 'aws-ms-lab-credentials'
    ]]) {
        sh """
            set -e
            aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${env.AWS_ACCOUNT}.dkr.ecr.${env.AWS_REGION}.amazonaws.com
        """
    }

    sh """
        set -e
        docker push ${env.IMAGE_URI}
    """

    dir("${env.SERVICE_DIR}") {
        withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            credentialsId: 'aws-ms-lab-credentials'
        ]]) {

            sh """
                set -e

                aws ecs describe-task-definition \
                  --task-definition ${env.TASK_FAMILY} \
                  --region ${env.AWS_REGION} \
                  --query taskDefinition \
                  --output json \
                  > current-task-def.json

                echo "===== CHECK current-task-def.json image ====="
                grep -n '"image"' current-task-def.json || true
            """

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

        if "environment" not in c:
            c["environment"] = []

        c["environment"] = [e for e in c["environment"] if e.get("name") != "APP_IMAGE_TAG"]
        c["environment"].append({
            "name": "APP_IMAGE_TAG",
            "value": image_uri.split(":")[-1]
        })

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

            sh """
                set -e

                aws ecs register-task-definition \
                  --cli-input-json file://new-task-def.json \
                  --region ${env.AWS_REGION} \
                  > register-output.json
            """

            sh '''
                set -e

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

            sh """
                set -e

                echo "===== USING TASK DEF ARN ====="
                cat new-taskdef-arn.txt

                aws ecs update-service \
                  --cluster ${env.CLUSTER_NAME} \
                  --service ${env.ECS_SERVICE_NAME} \
                  --task-definition "\$(cat new-taskdef-arn.txt)" \
                  --force-new-deployment \
                  --region ${env.AWS_REGION}
            """

            sh """
                set -e

                aws ecs wait services-stable \
                  --cluster ${env.CLUSTER_NAME} \
                  --services ${env.ECS_SERVICE_NAME} \
                  --region ${env.AWS_REGION}

                echo "ECS service is stable now."
            """

            sh """
                set -e

                echo "Deployment completed."
                echo "SERVICE_MODULE=${serviceModule}"
                echo "IMAGE_URI=${env.IMAGE_URI}"
                echo "CLUSTER_NAME=${env.CLUSTER_NAME}"
                echo "ECS_SERVICE_NAME=${env.ECS_SERVICE_NAME}"
                echo "TASK_FAMILY=${env.TASK_FAMILY}"

                echo "===== SERVICE SUMMARY ====="
                aws ecs describe-services \
                  --cluster ${env.CLUSTER_NAME} \
                  --services ${env.ECS_SERVICE_NAME} \
                  --region ${env.AWS_REGION} \
                  --query 'services[0].{serviceName:serviceName,status:status,taskDefinition:taskDefinition,desiredCount:desiredCount,runningCount:runningCount}' \
                  --output table || true
            """
        }
    }
}

/* =========================
   服務對應設定
   ========================= */
def getServiceConfig(String serviceModule) {
    if (serviceModule == 'service-a') {
        return [
            SERVICE_DIR      : 'service-a',
            ECR_URI          : '854139532460.dkr.ecr.ap-northeast-1.amazonaws.com/aws-ms-lab/service-a',
            ECS_SERVICE_NAME : 'aws-ms-lab-service-a-task-service-priw3f2z',
            TASK_FAMILY      : 'aws-ms-lab-service-a-task',
            CONTAINER_NAME   : 'service-a-container'
        ]
    }

    if (serviceModule == 'service-b') {
        return [
            SERVICE_DIR      : 'service-b',
            ECR_URI          : '854139532460.dkr.ecr.ap-northeast-1.amazonaws.com/aws-ms-lab/service-b',
            ECS_SERVICE_NAME : 'aws-ms-lab-service-b-task-service',
            TASK_FAMILY      : 'aws-ms-lab-service-b-task',
            CONTAINER_NAME   : 'service-b-container'
        ]
    }

    error("Unsupported serviceModule: ${serviceModule}")
}