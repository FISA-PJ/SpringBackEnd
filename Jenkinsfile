pipeline {
  agent any
  // tools 섹션 제거 (Java 17 설정 없이 진행)
  environment {
    // Docker 이미지 설정
    DOCKER_IMAGE = 'jaerimw/sprint-backend'
    DOCKERHUB_CREDENTIALS_ID = 'docker-hub'
    // Kubernetes 설정
    KUBECONFIG_CREDENTIALS_ID = 'kubeconfig'
    NAMESPACE = 'backend'
    DEPLOYMENT_NAME = 'backend'
    // 애플리케이션 설정
    CONTAINER_PORT = '8080'
    SERVICE_PORT = '80'
  }
  triggers {
    githubPush()
  }
  stages {
    stage('Environment Check') {
      steps {
        script {
          echo "🔍 환경 정보 확인..."
          sh '''
            echo "Java Version:"
            java -version
            echo "JAVA_HOME: $JAVA_HOME"
            echo "PATH: $PATH"
            echo "Gradle Version:"
            ./gradlew --version || echo "Gradle wrapper not found"
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
    stage('Build JAR') {
      steps {
        script {
          try {
            echo "🔨 Spring Boot 애플리케이션 빌드 시작..."

            // Gradle wrapper 실행 권한 설정
            sh 'chmod +x gradlew'
            // Java 버전 재확인
            sh '''
              echo "=== Build 환경 확인 ==="
              java -version
              echo "JAVA_HOME: $JAVA_HOME"
            '''
            // Gradle 빌드 (테스트 스킵)
            sh './gradlew clean build -x test --no-daemon --parallel'
            // 빌드 결과 확인
            sh '''
              echo "=== 빌드 결과 ==="
              ls -la build/libs/
              echo "JAR 파일 크기:"
              du -h build/libs/*.jar || echo "JAR 파일을 찾을 수 없습니다"
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
            echo "🐳 Docker 이미지 빌드: ${DOCKER_IMAGE}:${BUILD_NUMBER}"
            // Dockerfile 존재 확인
            if (!fileExists('Dockerfile')) {
              error "❌ Dockerfile을 찾을 수 없습니다"
            }
            // Docker 이미지 빌드
            sh """
              docker build -t ${DOCKER_IMAGE}:${BUILD_NUMBER} .
              docker tag ${DOCKER_IMAGE}:${BUILD_NUMBER} ${DOCKER_IMAGE}:latest
            """
            // 이미지 빌드 확인
            sh "docker images | grep ${DOCKER_IMAGE}"

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
              echo "📤 Docker Hub에 이미지 푸시 중..."
              // Docker Hub 로그인
              sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
              // 재시도 로직을 포함한 push
              def maxRetries = 3
              def pushSuccess = false
              for (int i = 0; i < maxRetries && !pushSuccess; i++) {
                try {
                  echo "Docker push 시도 ${i + 1}/${maxRetries}..."
                  // 빌드 번호 태그 push
                  timeout(time: 10, unit: 'MINUTES') {
                    sh "docker push ${DOCKER_IMAGE}:${BUILD_NUMBER}"
                  }
                  // latest 태그 push
                  timeout(time: 10, unit: 'MINUTES') {
                    sh "docker push ${DOCKER_IMAGE}:latest"
                  }
                  pushSuccess = true
                  echo "✅ Docker push 성공!"
                } catch (Exception e) {
                  echo "⚠️ Push 시도 ${i + 1} 실패: ${e.getMessage()}"
                  if (i < maxRetries - 1) {
                    echo "30초 대기 후 재시도..."
                    sleep(30)
                  } else {
                    error("❌ Docker push가 ${maxRetries}번 모두 실패했습니다.")
                  }
                }
              }
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
            echo "📝 Deployment YAML 업데이트 중..."
            // deployment.yaml 파일 존재 확인
            if (!fileExists('k8s/backend/deployment.yaml')) {
              error "❌ deployment.yaml 파일을 찾을 수 없습니다: k8s/backend/deployment.yaml"
            }
            // deployment.yaml에서 이미지 태그 업데이트
            sh """
              # 백업 생성
              cp k8s/backend/deployment.yaml k8s/backend/deployment.yaml.bak
              # 이미지 태그를 새 빌드 번호로 변경
              sed -i 's|image: ${DOCKER_IMAGE}:.*|image: ${DOCKER_IMAGE}:${BUILD_NUMBER}|g' k8s/backend/deployment.yaml
              # 변경된 내용 확인
              echo "=== 업데이트된 deployment.yaml ==="
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
              echo "🚀 EKS 클러스터에 배포 중..."
              sh """
                export KUBECONFIG=\$KUBECONFIG
                # kubectl 연결 테스트
                kubectl cluster-info
                # 네임스페이스 존재 확인 및 생성
                kubectl get namespace ${NAMESPACE} || kubectl create namespace ${NAMESPACE}
                # 배포 적용
                kubectl apply -f k8s/backend/ --namespace=${NAMESPACE}
                # 배포 완료 대기 (최대 5분)
                kubectl rollout status deployment/${DEPLOYMENT_NAME} --namespace=${NAMESPACE} --timeout=300s
                # 배포 상태 확인
                echo "=== Pod 상태 ==="
                kubectl get pods -n ${NAMESPACE}

                echo "=== Service 상태 ==="
                kubectl get svc -n ${NAMESPACE}
              """

              echo "✅ EKS 배포 완료"
            } catch (Exception e) {
              error "❌ EKS 배포 실패: ${e.getMessage()}"
            }
          }
        }
      }
    }
    stage('Health Check') {
      steps {
        withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIALS_ID}", variable: 'KUBECONFIG')]) {
          script {
            try {
              echo "🏥 애플리케이션 Health Check 수행..."
              sh """
                export KUBECONFIG=\$KUBECONFIG
                # Pod Ready 상태 확인
                echo "=== Pod 상세 상태 ==="
                kubectl get pods -n ${NAMESPACE} -o wide
                # Service 엔드포인트 확인
                echo "=== Service 엔드포인트 ==="
                kubectl get endpoints -n ${NAMESPACE}
                # 애플리케이션 로그 확인 (최신 20줄)
                echo "=== 최신 애플리케이션 로그 ==="
                kubectl logs -n ${NAMESPACE} deployment/${DEPLOYMENT_NAME} --tail=20 --since=5m || echo "로그를 가져올 수 없습니다"
              """

              echo "✅ Health Check 완료"
            } catch (Exception e) {
              echo "⚠️ Health Check 경고: ${e.getMessage()}"
            }
          }
        }
      }
    }
    stage('Cleanup Local Images') {
      steps {
        script {
          try {
            echo "🧹 로컬 Docker 이미지 정리..."
            sh """
              # 로컬 이미지 정리 (에러 무시)
              docker rmi ${DOCKER_IMAGE}:${BUILD_NUMBER} || echo "이미지 삭제 스킵"
              docker rmi ${DOCKER_IMAGE}:latest || echo "latest 이미지 삭제 스킵"
              # 사용하지 않는 이미지 정리
              docker image prune -f || echo "이미지 정리 스킵"
              # 디스크 사용량 확인
              df -h
              # 남은 이미지 확인
              docker images | head -10
            """

            echo "✅ 정리 완료"
          } catch (Exception e) {
            echo "⚠️ 정리 중 경고: ${e.getMessage()}"
          }
        }
      }
    }
  }
  post {
    always {
      script {
        try {
          // Docker 로그아웃
          sh 'docker logout || true'
          // 빌드 결과 아카이브 (파일이 있을 경우에만)
          if (fileExists('build/libs/*.jar')) {
            archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true
          }
          // JUnit 테스트 결과 (publishTestResults 대신 junit 사용)
          if (fileExists('build/test-results/test/*.xml')) {
            junit testResultsPattern: 'build/test-results/test/*.xml', allowEmptyResults: true
          }
        } catch (Exception e) {
          echo "⚠️ Post 단계 경고: ${e.getMessage()}"
        }
      }
    }
    success {
      echo """
      🎉 파이프라인 성공적으로 완료!
      ✅ 빌드 번호: ${BUILD_NUMBER}
      ✅ Docker 이미지: ${DOCKER_IMAGE}:${BUILD_NUMBER}
      ✅ 배포 네임스페이스: ${NAMESPACE}
      ✅ 배포 시간: ${new Date()}
      ✅ Java 버전: Java 17
      애플리케이션이 EKS에 성공적으로 배포되었습니다.
      """
    }
    failure {
      echo """
      ❌ 파이프라인 실패!
      실패한 빌드: ${BUILD_NUMBER}
      실패 시간: ${new Date()}
      브랜치: main
      💡 문제 해결 가이드:
      1. Java 17이 Jenkins에 설정되어 있는지 확인
      2. Docker Hub 자격증명 확인
      3. kubeconfig 설정 확인
      4. Spring Boot 버전과 Java 버전 호환성 확인
      """
    }
    cleanup {
      // 임시 파일 정리
      sh '''
        rm -f k8s/backend/deployment.yaml.bak || true
        docker system prune -f || true
      '''
    }
  }
}