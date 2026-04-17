pipeline {
    agent any

    parameters {
        choice(name: 'DEPLOY_MODE', choices: ['MANUAL', 'AUTO'], description: 'Deploy mode')
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

        stage('Clean Workspace') {
            steps {
                cleanWs()
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
                    ).trim().split("\n")

                    echo "Changed files: ${changedFiles}"

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
            archiveArtifacts artifacts: '**/*', fingerprint: true
        }
    }
}

def deployService(serviceName) {

    def imageTag = "build-${env.BUILD_NUMBER}"
    def imageUri = "${env.ECR_BASE}/${serviceName}:${imageTag}"
    def taskFamily = "aws-ms-lab-${serviceName}-task"
    def serviceNameFull = "aws-ms-lab-${serviceName}-task-service"

    echo "===== DEPLOY ${serviceName} ====="
    echo "IMAGE_URI=${imageUri}"

    dir(serviceName) {

        sh """
        mvn clean package -DskipTests
        """

        sh """
        docker build -t ${imageUri} .
        """

        sh """
        aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
        docker push ${imageUri}
        """

        sh """
        aws ecs describe-task-definition \
          --task-definition ${taskFamily} \
          --region ${AWS_REGION} \
          --query taskDefinition \
          --output json > current-task-def.json
        """

        // ✅ 產生乾淨 Python（無 tab 問題）
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
            keys_to_replace["SPRING_DATASOURCE_URL"] = "jdbc:mysql://appdb.c3ai6e6kuro7.ap-northeast-1.rds.amazonaws.com:3306/appdb?useSSL=false&serverTimezone=Asia/Taipei&characterEncoding=utf8"
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

with open("new-task-def.json", "w") as f:
    json.dump(data, f)
"""

        sh "python3 prepare_taskdef.py"

        sh """
        aws ecs register-task-definition \
          --region ${AWS_REGION} \
          --cli-input-json file://new-task-def.json
        """

        sh """
        aws ecs update-service \
          --cluster ${CLUSTER_NAME} \
          --service ${serviceNameFull} \
          --force-new-deployment \
          --region ${AWS_REGION}
        """
    }
}