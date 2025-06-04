pipeline {
  agent any
  environment {
    DOCKER_IMAGE = 'jaerimw/spring-backend'                // Docker Hub 저장소명
    DOCKERHUB_CREDENTIALS_ID = 'docker-hub'                // Docker Hub 자격증명 ID
    SSH_KEY_ID = 'ssh-backend'                         // EC2 SSH Key 자격증명 ID
    TARGET_SERVER_ID = 'target-server-address'             // EC2 서버 주소 자격증명 ID
    CONTAINER_NAME = 'spring-backend'                      // 도커 컨테이너 이름
  }
  triggers {
    githubPush()
  }
  stages {
    stage('Checkout') {
      steps {
        git branch: 'main', url: 'https://github.com/FISA-PJ/SpringBackEnd.git'
      }
    }
    stage('Build JAR') {
      steps {
        sh 'chmod +x gradlew'
        sh './gradlew build'
      }
    }
    stage('Build Docker Image') {
      steps {
        sh 'docker build -t $DOCKER_IMAGE:$BUILD_NUMBER .'
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
            // Docker Hub 로그인
            sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
            
            // 재시도 로직을 포함한 push
            def maxRetries = 3
            def pushSuccess = false
            
            for (int i = 0; i < maxRetries && !pushSuccess; i++) {
              try {
                echo "Docker push 시도 ${i + 1}/${maxRetries}..."
                
                // 이미지 push (타임아웃 설정)
                timeout(time: 10, unit: 'MINUTES') {
                  sh '''
                    # 병렬 업로드 수 제한
                    export DOCKER_BUILDKIT=0
                    docker push $DOCKER_IMAGE:$BUILD_NUMBER
                  '''
                }
                
                // latest 태그 생성 및 push
                sh 'docker tag $DOCKER_IMAGE:$BUILD_NUMBER $DOCKER_IMAGE:latest'
                
                timeout(time: 10, unit: 'MINUTES') {
                  sh 'docker push $DOCKER_IMAGE:latest'
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
    stage('Deploy to Remote Server') {
      steps {
        sshagent (credentials: ["${SSH_KEY_ID}"]) {
          withCredentials([string(credentialsId: "${TARGET_SERVER_ID}", variable: 'TARGET_SERVER')]) {
            script {
              // 원격 서버 배포도 재시도 로직 추가
              def maxRetries = 2
              def deploySuccess = false
              
              echo "배포 대상 서버: ${TARGET_SERVER}"
              
              for (int i = 0; i < maxRetries && !deploySuccess; i++) {
                try {
                  echo "배포 시도 ${i + 1}/${maxRetries}..."
                  
                  timeout(time: 5, unit: 'MINUTES') {
                    sh """
                      ssh -o StrictHostKeyChecking=no -o ConnectTimeout=30 ${TARGET_SERVER} '
                        # Docker 이미지 pull (재시도 포함)
                        for retry in 1 2 3; do
                          if docker pull $DOCKER_IMAGE:latest; then
                            break
                          else
                            echo "Pull 재시도 \$retry/3..."
                            sleep 10
                          fi
                        done &&
                        
                        # 기존 컨테이너 정리
                        docker stop $CONTAINER_NAME || true &&
                        docker rm $CONTAINER_NAME || true &&
                        
                        # 새 컨테이너 실행
                        docker run -d --name $CONTAINER_NAME -p 8080:8080 $DOCKER_IMAGE:latest &&
                        
                        # 컨테이너 상태 확인
                        sleep 5 &&
                        docker ps | grep $CONTAINER_NAME
                      '
                    """
                  }
                  
                  deploySuccess = true
                  echo "배포 성공!"
                  
                } catch (Exception e) {
                  echo "배포 시도 ${i + 1} 실패: ${e.getMessage()}"
                  if (i < maxRetries - 1) {
                    echo "10초 대기 후 재시도..."
                    sleep(10)
                  } else {
                    error("배포가 ${maxRetries}번 모두 실패했습니다.")
                  }
                }
              }
            }
          }
        }
      }
    }
    stage('Cleanup Local') {
      steps {
        sh '''
          # 로컬 이미지 정리 (에러 무시)
          docker rmi $DOCKER_IMAGE:$BUILD_NUMBER || true
          docker rmi $DOCKER_IMAGE:latest || true
          
          # 사용하지 않는 이미지 정리
          docker image prune -f || true
        '''
      }
    }
  }
  
  post {
    always {
      // Docker 로그아웃
      sh 'docker logout || true'
    }
    failure {
      echo "파이프라인 실패. 로그를 확인하세요."
    }
    success {
      echo "파이프라인 성공적으로 완료!"
    }
  }
}