pipeline {
  agent any

  tools {
    jdk 'Java17'  // Jenkins Global Tool Configuration에서 설정한 이름
  }

  environment {
    DOCKER_IMAGE = 'jaerimw/spring-backend'
    DOCKERHUB_CREDENTIALS_ID = 'docker-hub'
    KUBECONFIG_CREDENTIALS_ID = 'kubeconfig'
    NAMESPACE = 'backend'
    DEPLOYMENT_NAME = 'backend'
    CONTAINER_PORT = '8080'
    SERVICE_PORT = '80'
    
    // Java 17 환경 강제 설정
    JAVA_HOME = "${tool 'Java17'}"
    PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
  }

  triggers {
    githubPush()
  }

  stages {
    stage('Environment Check') {
      steps {
        script {
          echo "🔍 Java 17 환경 확인..."
          sh '''
            echo "Java Version:"
            java -version
            echo "JAVA_HOME: $JAVA_HOME"
            echo "Docker Version:"
            docker --version
          '''
        }
      }
    }

    stage('Checkout') {
      steps {
        script {
          try {
            echo "📥 Git repository 체크아웃..."
            git branch: 'main',
                url: 'https://github.com/FISA-PJ/SpringBackEnd.git'
            echo "✅ Git checkout 완료"
          } catch (Exception e) {
            error "❌ Git checkout 실패: ${e.getMessage()}"
          }
        }
      }
    }

    stage('Build JAR with Java 17') {
      steps {
        script {
          try {
            echo "🔨 Java 17로 Spring Boot 빌드..."
            
            sh 'chmod +x gradlew'
            
            sh '''
              echo "=== Java 17 빌드 환경 ==="
              java -version
              echo "JAVA_HOME: $JAVA_HOME"
              ./gradlew clean build -x test --no-daemon
              ls -la build/libs/
            '''
            
            echo "✅ JAR 빌드 완료"
          } catch (Exception e) {
            error "❌ JAR 빌드 실패: ${e.getMessage()}"
          }
        }
      }
    }

    stage('Build Docker Image') {
      steps {
        script {
          try {
            echo "🐳 Docker 이미지 빌드..."

            sh """
              docker build -t ${DOCKER_IMAGE}:${BUILD_NUMBER} .
              docker tag ${DOCKER_IMAGE}:${BUILD_NUMBER} ${DOCKER_IMAGE}:latest
              docker images | grep ${DOCKER_IMAGE}
            """
            
            echo "✅ Docker 이미지 빌드 완료"
          } catch (Exception e) {
            error "❌ Docker 이미지 빌드 실패: ${e.getMessage()}"
          }
        }
      }
    }

    stage('Push to DockerHub') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: "${DOCKERHUB_CREDENTIALS_ID}",
          usernameVariable: 'DOCKER_USER',
          passwordVariable: 'DOCKER_PASS'
        )]) {
          script {
            try {
              echo "📤 Docker Hub 푸시..."

              sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'

              sh """
                docker push ${DOCKER_IMAGE}:${BUILD_NUMBER}
                docker push ${DOCKER_IMAGE}:latest
              """
              
              echo "✅ Docker Hub 푸시 완료"
            } catch (Exception e) {
              error "❌ Docker Hub 푸시 실패: ${e.getMessage()}"
            } finally {
              sh 'docker logout || true'
            }
          }
        }
      }
    }

    stage('Update Deployment YAML') {
      steps {
        script {
          try {
            echo "📝 Deployment YAML 업데이트..."

            sh """
              sed -i 's|image: ${DOCKER_IMAGE}:.*|image: ${DOCKER_IMAGE}:${BUILD_NUMBER}|g' k8s/backend/deployment.yaml
              cat k8s/backend/deployment.yaml | grep -A 3 -B 3 "image:"
            """
            
            echo "✅ YAML 업데이트 완료"
          } catch (Exception e) {
            error "❌ YAML 업데이트 실패: ${e.getMessage()}"
          }
        }
      }
    }

    stage('Deploy to EKS') {
      steps {
        withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIALS_ID}", variable: 'KUBECONFIG')]) {
          script {
            try {
              echo "🚀 EKS 배포..."

              sh """
                export KUBECONFIG=\$KUBECONFIG
                kubectl apply -f k8s/backend/ --namespace=${NAMESPACE}
                kubectl rollout status deployment/${DEPLOYMENT_NAME} --namespace=${NAMESPACE} --timeout=300s
                kubectl get pods -n ${NAMESPACE}
              """
              
              echo "✅ EKS 배포 완료"
            } catch (Exception e) {
              error "❌ EKS 배포 실패: ${e.getMessage()}"
            }
          }
        }
      }
    }
  }

  post {
    always {
      script {
        sh 'docker logout || true'
        if (fileExists('build/libs/*.jar')) {
          archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true
        }
      }
    }

    success {
      echo """
      🎉 Java 17 빌드 성공!
      
      ✅ 빌드 번호: ${BUILD_NUMBER}
      ✅ Docker 이미지: ${DOCKER_IMAGE}:${BUILD_NUMBER}
      ✅ Java 버전: Java 17
      """
    }

    failure {
      echo """
      ❌ 빌드 실패!
      
      Jenkins에 Java 17이 올바르게 설정되었는지 확인하세요.
      """
    }
  }
}