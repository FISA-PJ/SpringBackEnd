pipeline {
  agent any

  environment {
    // Docker 이미지 설정
    DOCKER_IMAGE = 'jaerimw/backend'                       // Docker Hub 저장소명
    DOCKERHUB_CREDENTIALS_ID = 'docker-hub'               // Docker Hub 자격증명 ID

    // Kubernetes 설정
    KUBECONFIG_CREDENTIALS_ID = 'kubeconfig'              // Kubernetes config 자격증명 ID
    NAMESPACE = 'backend'                                 // Kubernetes 네임스페이스
    DEPLOYMENT_NAME = 'backend'                           // Kubernetes Deployment 이름

    // GitHub 설정
    GITHUB_CREDENTIALS_ID = 'github-token'                // GitHub 토큰 (private repo용)

    // 애플리케이션 설정
    CONTAINER_PORT = '8080'
    SERVICE_PORT = '80'
  }

  triggers {
    githubPush()  // GitHub webhook 트리거
  }

  stages {
    stage('Checkout') {
      steps {
        script {
          // Public repo인 경우 credentials 없이, Private repo인 경우 credentials 사용
          if (env.GITHUB_CREDENTIALS_ID) {
            git branch: 'main',
                url: 'https://github.com/FISA-PJ/SpringBackEnd.git',
                credentialsId: "${GITHUB_CREDENTIALS_ID}"
          } else {
            git branch: 'main',
                url: 'https://github.com/FISA-PJ/SpringBackEnd.git'
          }
        }
      }
    }

    stage('Build JAR') {
      steps {
        script {
          echo "Building Spring Boot application..."
          sh 'chmod +x gradlew'

          // Gradle 빌드 (테스트 포함)
          sh './gradlew clean build'

          // 빌드 결과 확인
          sh 'ls -la build/libs/'
        }
      }
    }

    stage('Build Docker Image') {
      steps {
        script {
          echo "Building Docker image: ${DOCKER_IMAGE}:${BUILD_NUMBER}"

          // Docker 이미지 빌드
          sh """
            docker build -t ${DOCKER_IMAGE}:${BUILD_NUMBER} .
            docker tag ${DOCKER_IMAGE}:${BUILD_NUMBER} ${DOCKER_IMAGE}:latest
          """

          // 이미지 빌드 확인
          sh "docker images | grep ${DOCKER_IMAGE}"
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
            echo "Pushing Docker image to Docker Hub..."

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
                echo "Docker push 성공!"

              } catch (Exception e) {
                echo "Push 시도 ${i + 1} 실패: ${e.getMessage()}"
                if (i < maxRetries - 1) {
                  echo "30초 대기 후 재시도..."
                  sleep(30)
                } else {
                  error("Docker push가 ${maxRetries}번 모두 실패했습니다.")
                }
              }
            }
          }
        }
      }
    }

    stage('Update Deployment YAML') {
      steps {
        script {
          echo "Updating deployment YAML with new image..."

          // deployment.yaml에서 이미지 태그 업데이트
          sh """
            # 이미지 태그를 새 빌드 번호로 변경
            sed -i 's|image: ${DOCKER_IMAGE}:.*|image: ${DOCKER_IMAGE}:${BUILD_NUMBER}|g' k8s/backend/deployment.yaml

            # 변경된 내용 확인
            echo "=== 업데이트된 deployment.yaml ==="
            cat k8s/backend/deployment.yaml | grep -A 5 -B 5 "image:"
          """
        }
      }
    }

    stage('Deploy to EKS') {
      steps {
        withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIALS_ID}", variable: 'KUBECONFIG')]) {
          script {
            echo "Deploying to EKS cluster..."

            sh """
              export KUBECONFIG=\$KUBECONFIG

              # 간단하게 apply로 배포
              kubectl apply -f k8s/backend/

              # 배포 상태 확인
              kubectl get pods -n ${NAMESPACE}
              kubectl get svc -n ${NAMESPACE}
            """
          }
        }
      }
    }

    stage('Health Check') {
      steps {
        withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIALS_ID}", variable: 'KUBECONFIG')]) {
          script {
            echo "애플리케이션 Health Check 수행..."

            sh """
              export KUBECONFIG=\$KUBECONFIG

              # Pod 상태 확인
              kubectl get pods -n ${NAMESPACE}

              # 서비스 상태 확인
              kubectl get svc -n ${NAMESPACE}
            """
          }
        }
      }
    }

    stage('Cleanup Local Images') {
      steps {
        script {
          echo "로컬 Docker 이미지 정리..."
          sh """
            # 로컬 이미지 정리 (에러 무시)
            docker rmi ${DOCKER_IMAGE}:${BUILD_NUMBER} || true
            docker rmi ${DOCKER_IMAGE}:latest || true

            # 사용하지 않는 이미지 정리
            docker image prune -f || true

            # 남은 이미지 확인
            docker images | head -10
          """
        }
      }
    }
  }

  post {
    always {
      script {
        // Docker 로그아웃
        sh 'docker logout || true'

        // 빌드 결과 아카이브
        archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true

        // 테스트 결과 발행
        publishTestResults testResultsPattern: 'build/test-results/test/*.xml'
      }
    }

    success {
      echo """
      🎉 파이프라인 성공적으로 완료!

      ✅ 빌드 번호: ${BUILD_NUMBER}
      ✅ Docker 이미지: ${DOCKER_IMAGE}:${BUILD_NUMBER}
      ✅ 배포 네임스페이스: ${NAMESPACE}
      ✅ 배포 시간: ${new Date()}

      애플리케이션이 EKS에 성공적으로 배포되었습니다.
      """
    }

    failure {
      echo """
      ❌ 파이프라인 실패!

      실패한 빌드: ${BUILD_NUMBER}
      실패 시간: ${new Date()}

      로그를 확인하여 문제를 해결하세요.
      """
    }

    unstable {
      echo "⚠️ 파이프라인이 불안정한 상태로 완료되었습니다. 테스트 결과를 확인하세요."
    }
  }
}