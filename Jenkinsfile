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
                script {
                    def slurper = new JsonSlurperClassic()
                    def taskDef = slurper.parse(new File("${env.WORKSPACE}/current-task-def.json"))

                    taskDef.containerDefinitions.each { c ->
                        if (c.name == env.CONTAINER_NAME) {
                            c.image = env.IMAGE_URI
                        }
                    }

                    def registerPayload = [:]
                    registerPayload.family                  = taskDef.family
                    registerPayload.networkMode             = taskDef.networkMode
                    registerPayload.containerDefinitions    = taskDef.containerDefinitions
                    registerPayload.requiresCompatibilities = taskDef.requiresCompatibilities
                    registerPayload.cpu                     = taskDef.cpu
                    registerPayload.memory                  = taskDef.memory

                    if (taskDef.taskRoleArn)          registerPayload.taskRoleArn = taskDef.taskRoleArn
                    if (taskDef.executionRoleArn)     registerPayload.executionRoleArn = taskDef.executionRoleArn
                    if (taskDef.volumes)              registerPayload.volumes = taskDef.volumes
                    if (taskDef.placementConstraints) registerPayload.placementConstraints = taskDef.placementConstraints
                    if (taskDef.runtimePlatform)      registerPayload.runtimePlatform = taskDef.runtimePlatform
                    if (taskDef.proxyConfiguration)   registerPayload.proxyConfiguration = taskDef.proxyConfiguration
                    if (taskDef.inferenceAccelerators) registerPayload.inferenceAccelerators = taskDef.inferenceAccelerators
                    if (taskDef.ephemeralStorage)     registerPayload.ephemeralStorage = taskDef.ephemeralStorage
                    if (taskDef.pidMode)              registerPayload.pidMode = taskDef.pidMode
                    if (taskDef.ipcMode)              registerPayload.ipcMode = taskDef.ipcMode

                    writeFile file: 'new-task-def.json',
                              text: JsonOutput.prettyPrint(JsonOutput.toJson(registerPayload))

                    echo "New task definition JSON prepared with image: ${env.IMAGE_URI}"
                }
            }
        }

        stage('Register New Task Definition Revision') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: "${AWS_CREDENTIALS_ID}"
                ]]) {
                    sh '''
                        set -e
                        aws ecs register-task-definition \
                          --cli-input-json file://new-task-def.json \
                          --region ${AWS_REGION} \
                          --output json > register-output.json

                        aws ecs describe-task-definition \
                          --task-definition ${TASK_FAMILY} \
                          --region ${AWS_REGION} \
                          --query 'taskDefinition.taskDefinitionArn' \
                          --output text > new-taskdef-arn.txt
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