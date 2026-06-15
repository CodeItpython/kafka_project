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
-> CI 성공 시 kafka-chat-local-deploy pipeline
-> Git commit 기반 Docker image build
-> kubectl apply -k k8s
-> kubectl set image
```

현재 `Jenkinsfile`과 `Jenkinsfile.local-deploy`에는 `pollSCM('* * * * *')`가 들어 있다.
로컬 Jenkins가 GitHub에서 직접 webhook을 받을 수 없는 환경이어도 Jenkins가 약 1분마다 GitHub 브랜치를 확인하고 새 commit이 있으면 자동으로 pipeline을 실행한다.
또한 `kafka-chat-local-deploy`는 `kafka-chat-main-ci`가 성공하면 이어서 실행되도록 upstream trigger를 함께 둔다.

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
9. Branch Specifier에는 기본 배포 대상인 `*/main`을 넣는다.
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
- Docker 이미지는 Jenkins의 `GIT_COMMIT` 값을 태그로 사용해 `kafka-auth-service:{commit}`, `kafka-discovery-service:{commit}`, `kafka-frontend:{commit}` 형태로 빌드된다.
- Kubernetes manifest 적용 후 `kubectl set image`로 Deployment 이미지를 해당 commit 태그로 교체한다.

이 모드에서는 push 후 `kafka-chat-main-ci`가 먼저 실행되고, 성공하면 `kafka-chat-local-deploy`가 이어서 Docker 이미지 생성과 Kubernetes 반영을 실행한다.

현재 로컬 Jenkins는 다음 브랜치 전략으로 구성한다.

- `kafka-chat-ci`: `KAFKA_PROJECT_CI_BRANCH`를 감시한다. 기본값은 `*/main`이다.
- `kafka-chat-local-deploy`: `kafka-chat-main-ci` 성공 후 `KAFKA_PROJECT_DEPLOY_BRANCH`를 Docker 이미지로 빌드하고 로컬 Kubernetes에 반영한다. 기본값은 `*/main`이다.
- `kafka-chat-main-ci`: `KAFKA_PROJECT_MAIN_BRANCH`를 감시한다. 기본값은 `*/main`이며 배포 없이 검증만 수행한다.

기능 브랜치를 전부 자동 배포하지 않는 이유는, 미완성 브랜치가 로컬 Kubernetes를 계속 덮어쓰면 현재 테스트 중인 앱 상태가 흔들리기 때문이다. 기능 브랜치는 로컬/PR에서 검증하고, 통합 테스트 브랜치 또는 main으로 모았을 때 Jenkins 배포를 태우는 방식이 안정적이다.

배포 job이 성공하려면 Docker Desktop Kubernetes가 켜져 있고 `kubectl config current-context`가 유효한 context를 반환해야 한다. context 목록이 비어 있으면 Jenkins는 Docker 이미지는 빌드할 수 있어도 `kubectl apply -k k8s` 단계에서 실패한다.

Jenkins 컨테이너 안에서는 Docker Desktop Kubernetes의 `localhost` API 주소가 컨테이너 자기 자신을 가리킨다. 그래서 `Jenkinsfile.local-deploy`는 배포 전 `scripts/prepare-jenkins-kubeconfig.sh`를 실행해 임시 kubeconfig를 만들고, `docker-desktop` cluster server의 host만 `host.docker.internal`로 바꾼다. Docker Desktop이 동적 port를 쓰는 경우가 있으므로 port는 기존 kubeconfig 값을 유지한다.

기능 브랜치를 임시로 CI/CD 대상에 올리고 싶으면 Jenkins 컨테이너를 다시 만들 때 아래처럼 지정한다.

```bash
KAFKA_PROJECT_CI_BRANCH='*/codex/redis-state-jenkins-cd' \
KAFKA_PROJECT_DEPLOY_BRANCH='*/codex/redis-state-jenkins-cd' \
docker compose -f docker-compose.yml -f docker-compose.jenkins-deploy.yml up -d --build jenkins
```

## 수동 배포

Jenkins를 쓰지 않고 같은 흐름을 직접 실행하려면:

```bash
cd /Users/gunwoo/Documents/KAFKA
scripts/deploy-local-k8s.sh
```

## 주의사항

- 현재 구성은 로컬 학습용이다.
- 운영 환경에서는 Jenkins agent, Docker registry, Kubernetes credentials, secret 관리, 이미지 tag 전략을 분리해야 한다.
- Jenkins CD는 commit 기반 이미지 태그와 `kubectl set image`를 사용한다. 이 방식은 같은 `:local` 태그 재사용으로 Kubernetes가 예전 이미지를 계속 쓰는 문제를 줄인다.
- Docker socket을 mount한 Jenkins는 강한 권한을 갖는다. 운영에서는 전용 agent와 제한된 credential을 사용한다.
