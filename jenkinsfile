pipeline {
  agent any

  environment {
    DOCKER_IMAGE = 'jaerimw/spring-backend'                // Docker Hub 저장소명
    DOCKERHUB_CREDENTIALS_ID = 'docker-hub'                // Docker Hub 자격증명 ID
    SSH_KEY_ID = 'ssh-key-backend'                         // EC2 SSH Key 자격증명 ID
    TARGET_SERVER = 'ubuntu@10.0.101.237'                  // 대상 EC2 서버
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
          sh '''
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
            docker push $DOCKER_IMAGE:$BUILD_NUMBER
            docker tag $DOCKER_IMAGE:$BUILD_NUMBER $DOCKER_IMAGE:latest
            docker push $DOCKER_IMAGE:latest
          '''
        }
      }
    }

    stage('Deploy to Remote Server') {
      steps {
        sshagent (credentials: ["${SSH_KEY_ID}"]) {
          sh """
            ssh -o StrictHostKeyChecking=no $TARGET_SERVER '
              docker pull $DOCKER_IMAGE:latest &&
              docker stop $CONTAINER_NAME || true &&
              docker rm $CONTAINER_NAME || true &&
              docker run -d --name $CONTAINER_NAME -p 8080:8080 $DOCKER_IMAGE:latest
            '
          """
        }
      }
    }

    stage('Cleanup Local') {
      steps {
        sh '''
          docker rmi $DOCKER_IMAGE:$BUILD_NUMBER || true
          docker rmi $DOCKER_IMAGE:latest || true
        '''
      }
    }
  }
}
