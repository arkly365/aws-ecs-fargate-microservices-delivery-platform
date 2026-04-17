pipeline {
    agent any

    parameters {
        choice(name: 'DEPLOY_MODE', choices: ['AUTO', 'MANUAL'], description: 'Deploy mode')
        booleanParam(name: 'DEPLOY_SERVICE_A', defaultValue: true, description: 'Deploy service-a')
        booleanParam(name: 'DEPLOY_SERVICE_B', defaultValue: true, description: 'Deploy service-b')
    }

    environment {
        AWS_REGION = 'ap-northeast-1'
        AWS_ACCOUNT_ID = '854139532460'
        ECR_BASE = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/aws-ms-lab"
        CLUSTER_NAME = 'aws-ms-lab-cluster'
    }

    stages {

        stage('Checkout SCM') {
            steps {
                checkout scm
            }
        }

        stage('Clean Workspace') {
            steps {
                cleanWs()
                checkout scm
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Detect Changed Services') {
            steps {
                script {
                    def changedFilesRaw = sh(
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

                    def changedFiles = changedFilesRaw ? changedFilesRaw.split("\n") : []

                    echo "Changed files:\n${changedFilesRaw}"

                    if (params.DEPLOY_MODE == 'AUTO') {
                        env.DEPLOY_SERVICE_A = changedFiles.any { it.startsWith('service-a/') } ? "true" : "false"
                        env.DEPLOY_SERVICE_B = changedFiles.any { it.startsWith('service-b/') } ? "true" : "false"
                    } else {
                        env.DEPLOY_SERVICE_A = params.DEPLOY_SERVICE_A.toString()
                        env.DEPLOY_SERVICE_B = params.DEPLOY_SERVICE_B.toString()
                    }

                    echo "DEPLOY_MODE=${params.DEPLOY_MODE}"
                    echo "DEPLOY_SERVICE_A=${env.DEPLOY_SERVICE_A}"
                    echo "DEPLOY_SERVICE_B=${env.DEPLOY_SERVICE_B}"
                }
            }
        }

        stage('Deploy service-a') {
            when {
                expression { env.DEPLOY_SERVICE_A == "true" }
            }
            steps {
                script {
                    deployService("service-a")
                }
            }
        }

        stage('Deploy service-b') {
            when {
                expression { env.DEPLOY_SERVICE_B == "true" }
            }
            steps {
                script {
                    deployService("service-b")
                }
            }
        }

        stage('No Deployment Needed') {
            when {
                expression { env.DEPLOY_SERVICE_A != "true" && env.DEPLOY_SERVICE_B != "true" }
            }
            steps {
                echo "No service changes detected. Skipping deployment."
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/current-task-def.json,**/new-task-def.json,**/register-output.json,**/new-taskdef-arn.txt,**/prepare_taskdef.py', allowEmptyArchive: true
        }
    }
}

def deployService(serviceName) {

    def imageTag = "build-${env.BUILD_NUMBER}"
    def imageUri = "${env.ECR_BASE}/${serviceName}:${imageTag}"
    def taskFamily = "aws-ms-lab-${serviceName}-task"

    def serviceNameFull
    if (serviceName == "service-a") {
        serviceNameFull = "aws-ms-lab-service-a-task-service-priw3f2z"
    } else if (serviceName == "service-b") {
        serviceNameFull = "aws-ms-lab-service-b-task-service"
    } else {
        error("Unsupported serviceName: ${serviceName}")
    }

    echo "===== DEPLOY ${serviceName} ====="
    echo "IMAGE_URI=${imageUri}"

    dir(serviceName) {

        sh """
        set -e
        mvn clean package -DskipTests
        """

        sh """
        set -e
        docker build -t ${imageUri} .
        """

        withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            credentialsId: 'aws-ms-lab-credentials'
        ]]) {
            sh """
            set -e
            aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
            docker push ${imageUri}
            """
        }

        withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            credentialsId: 'aws-ms-lab-credentials'
        ]]) {
            sh """
            set -e
            aws ecs describe-task-definition \
              --task-definition ${taskFamily} \
              --region ${AWS_REGION} \
              --query taskDefinition \
              --output json > current-task-def.json
            """
        }

        writeFile file: 'prepare_taskdef.py', text: """
import json

with open("current-task-def.json") as f:
    data = json.load(f)

container_name = "${serviceName}-container"
image_uri = "${imageUri}"

for c in data["containerDefinitions"]:
    if c["name"] == container_name:
        c["image"] = image_uri

        if "environment" not in c:
            c["environment"] = []

        keys_to_replace = {
            "APP_IMAGE_TAG": image_uri.split(":")[-1]
        }

        if container_name == "service-a-container":
            keys_to_replace["SPRING_DATASOURCE_URL"] = "jdbc:mysql://appdb.c3ai6e6kuro7.ap-northeast-1.rds.amazonaws.com:3306/service_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Taipei&characterEncoding=UTF-8"
            keys_to_replace["SPRING_DATASOURCE_USERNAME"] = "admin"
            keys_to_replace["SPRING_DATASOURCE_PASSWORD"] = "Admin1234!"
            keys_to_replace["SPRING_DATASOURCE_DRIVER_CLASS_NAME"] = "com.mysql.cj.jdbc.Driver"

        c["environment"] = [
            e for e in c["environment"]
            if e.get("name") not in keys_to_replace
        ]

        for k, v in keys_to_replace.items():
            c["environment"].append({
                "name": k,
                "value": v
            })

output = {
    "family": data["family"],
    "networkMode": data["networkMode"],
    "containerDefinitions": data["containerDefinitions"],
    "requiresCompatibilities": data["requiresCompatibilities"],
    "cpu": data["cpu"],
    "memory": data["memory"]
}

if data.get("taskRoleArn"):
    output["taskRoleArn"] = data["taskRoleArn"]

if data.get("executionRoleArn"):
    output["executionRoleArn"] = data["executionRoleArn"]

if data.get("volumes"):
    output["volumes"] = data["volumes"]

if data.get("placementConstraints"):
    output["placementConstraints"] = data["placementConstraints"]

if data.get("runtimePlatform"):
    output["runtimePlatform"] = data["runtimePlatform"]

with open("new-task-def.json", "w") as f:
    json.dump(output, f)
"""

        sh """
        set -e
        python3 prepare_taskdef.py
        """

        withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            credentialsId: 'aws-ms-lab-credentials'
        ]]) {
            sh """
            set -e
            aws ecs register-task-definition \
              --region ${AWS_REGION} \
              --cli-input-json file://new-task-def.json \
              > register-output.json
            """
        }

        sh '''
        set -e
        python3 - <<'PY'
import json
with open("register-output.json") as f:
    data = json.load(f)
arn = data["taskDefinition"]["taskDefinitionArn"]
with open("new-taskdef-arn.txt", "w") as f:
    f.write(arn)
print(arn)
PY
        '''

        withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            credentialsId: 'aws-ms-lab-credentials'
        ]]) {
            sh """
            set -e
            aws ecs update-service \
              --cluster ${CLUSTER_NAME} \
              --service ${serviceNameFull} \
              --task-definition \$(cat new-taskdef-arn.txt) \
              --force-new-deployment \
              --region ${AWS_REGION}
            """
        }

        withCredentials([[
            $class: 'AmazonWebServicesCredentialsBinding',
            credentialsId: 'aws-ms-lab-credentials'
        ]]) {
            sh """
            set -e
            aws ecs wait services-stable \
              --cluster ${CLUSTER_NAME} \
              --services ${serviceNameFull} \
              --region ${AWS_REGION}
            """
        }
    }
}