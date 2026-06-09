# Jenkins Local CI/CD

이 프로젝트의 Jenkins 흐름은 두 단계로 나뉜다.

- 기본 Jenkins: 백엔드/프론트 검증만 수행한다.
- 로컬 배포 Jenkins: Docker socket과 kubeconfig를 mount해서 Docker 이미지 빌드와 Kubernetes 반영까지 수행한다.

로컬 배포 Jenkins는 로컬 Docker와 Kubernetes를 제어할 수 있으므로 신뢰할 수 있는 개인 PC에서만 사용한다.

## 배포 흐름

```text
GitHub push
-> Jenkins pipeline
-> backend ./gradlew test
-> frontend npm ci && npm run build
```

## 기본 Jenkins 실행

```bash
cd /Users/gunwoo/Documents/KAFKA
docker compose up -d jenkins
```

브라우저에서 접속한다.

```text
http://localhost:18081
```

초기 관리자 비밀번호 확인:

```bash
docker exec kafka-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

## Jenkins Pipeline 생성

Suggested plugins 설치가 끝난 뒤에는 다음 순서로 진행한다.

1. `Create First Admin User` 화면에서 관리자 계정을 만든다.
2. `Instance Configuration` 화면의 Jenkins URL은 `http://localhost:18081/` 그대로 둔다.
3. `Start using Jenkins`를 누른다.
4. 왼쪽 메뉴에서 `New Item`을 누른다.
5. 이름은 `kafka-chat-ci`로 입력하고 `Pipeline`을 선택한다.
6. Pipeline 설정에서 `Definition`을 `Pipeline script from SCM`으로 바꾼다.
7. `SCM`은 `Git`을 선택한다.
8. Repository URL에는 GitHub 저장소 주소를 넣는다.
9. Branch Specifier에는 현재 작업 브랜치인 `*/codex/gradle-k8s-setup`를 넣는다.
10. Script Path는 기본 검증용이면 `Jenkinsfile`, 로컬 Kubernetes 배포용이면 `Jenkinsfile.local-deploy`를 넣는다.
11. 저장 후 `Build Now`를 눌러 실행한다.

기본 `Jenkinsfile`은 안전하게 테스트와 빌드만 수행한다. Docker 이미지 빌드와 Kubernetes 반영까지 하려면 아래의 로컬 배포 모드로 Jenkins를 다시 실행해야 한다.

## 로컬 Kubernetes 배포까지 Jenkins에서 실행하기

Docker 이미지 빌드와 Kubernetes 배포까지 Jenkins에 맡기려면 별도 override compose 파일을 사용한다.

```bash
cd /Users/gunwoo/Documents/KAFKA
docker compose -f docker-compose.yml -f docker-compose.jenkins-deploy.yml up -d --build jenkins
```

이 모드에서는:

- Jenkins 컨테이너가 `/var/run/docker.sock`을 mount해서 호스트 Docker Desktop에 이미지 빌드 명령을 보낸다.
- Jenkins 컨테이너가 `~/.kube`를 read-only로 mount해서 Docker Desktop Kubernetes context를 사용한다.
- Pipeline script path는 `Jenkinsfile.local-deploy`를 사용한다.
- Docker 이미지는 `kafka-auth-service:local`, `kafka-discovery-service:local`, `kafka-frontend:local` 태그로 빌드된다.
- Kubernetes manifest는 이 로컬 이미지 태그를 사용한다.

## 수동 배포

Jenkins를 쓰지 않고 같은 흐름을 직접 실행하려면:

```bash
cd /Users/gunwoo/Documents/KAFKA
scripts/deploy-local-k8s.sh
```

## 주의사항

- 현재 구성은 로컬 학습용이다.
- 운영 환경에서는 Jenkins agent, Docker registry, Kubernetes credentials, secret 관리, 이미지 tag 전략을 분리해야 한다.
- 같은 `:local` 태그를 계속 쓰기 때문에 Kubernetes에 새 이미지를 반영하려면 rollout restart가 필요하다.
- Docker socket을 mount한 Jenkins는 강한 권한을 갖는다. 운영에서는 전용 agent와 제한된 credential을 사용한다.
