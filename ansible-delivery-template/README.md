# VM Migration Ansible CI/CD Template

신규 클라우드 VM 이관 시 권역과 환경별 차이를 Ansible 변수로 분리하고, 공통 테스트·빌드·배포 절차를 재사용하는 GitHub Actions 템플릿입니다.

`cicd.yml`은 단계 순서만 정의합니다. 실제 구현은 각 reusable workflow가 호출하는 Ansible playbook에 두므로, GitHub Actions YAML에 레지스트리, 서버, 포트, 볼륨, 런타임 환경변수를 섞지 않습니다.

## 구조

~~~text
.
├── .github/
│   ├── actions/setup-ansible/action.yml       # Java 21 + Ansible + Docker collection 준비
│   ├── ansible/
│   │   ├── ansible.cfg
│   │   ├── hosts.yml                          # controller only; targets are runtime inventory
│   │   ├── requirements.yml                   # pinned community.docker collection
│   │   ├── playbooks/
│   │   │   ├── test.yml                       # Maven test -> JAR smoke test
│   │   │   ├── build.yml                      # GHCR build and push
│   │   │   ├── deploy.yml                     # SSH runtime inventory and serial deploy
│   │   │   └── deploy_single_server.yml       # one VM deployment and health check
│   │   └── vars/
│   │       ├── environments/{stg,prd}.yml
│   │       └── regions/{kr,eu,na}/{stg,prd}.yml
│   └── workflows/
│       ├── cicd.yml                           # test -> build -> deploy only
│       ├── test.yml
│       ├── build.yml
│       ├── deploy.yml
│       └── validate.yml
├── pom.xml
├── Dockerfile
└── src/
    ├── main/java/com/example/delivery/DeliveryApplication.java
    └── test/java/com/example/delivery/DeliveryApplicationTest.java
~~~

## 호출 흐름

~~~text
cicd.yml
  -> test.yml   -> .github/ansible/playbooks/test.yml
  -> build.yml  -> .github/ansible/playbooks/build.yml
  -> deploy.yml -> .github/ansible/playbooks/deploy.yml
                  -> deploy_single_server.yml
~~~

수동 실행은 `country`와 `deploy_env`를 반드시 받습니다. 값이 없거나 지원하지 않는 값이면 playbook의 검증 단계에서 실패합니다. 기본 권역이나 기본 환경을 workflow에 두지 않습니다.

## Java 테스트

`test.yml`은 단순 파일 존재 확인을 하지 않습니다.

1. `mvn --batch-mode --no-transfer-progress test`로 JUnit 단위 테스트를 실행합니다.
2. 테스트를 통과한 소스를 `target/delivery-service.jar`로 패키징합니다.
3. `APP_COUNTRY`, `APP_ENV`를 주입한 뒤 `java -jar target/delivery-service.jar --smoke-test`를 실행합니다.

JAR의 smoke mode는 임시 포트에서 실제 HTTP 서버를 시작하고 `/actuator/health`에 요청한 뒤 정상 응답을 확인하고 종료합니다. 따라서 Docker build 이전에 Java 코드, 실행 가능한 JAR, 국가/환경 런타임 입력, 헬스 엔드포인트를 함께 검증합니다.

Ansible의 Java 관련 모듈은 Java keystore/인증서 관리나 Maven 저장소 아티팩트 다운로드용입니다. 애플리케이션의 Maven test 또는 JAR 실행을 대체하는 범용 Java 테스트 모듈은 없으므로, 이 경우에는 `ansible.builtin.command`의 `argv`가 맞는 방식입니다. 셸, 파이프, `sh -c`를 사용하지 않고 Maven과 Java 실행 파일에 인자를 직접 전달합니다.

## 변수 분리

`environments/{stg,prd}.yml`은 SSH 접속 정보, 순차 배포 수, 이미지 정리 같은 환경 공통 정책을 둡니다. `regions/{kr,eu,na}/{stg,prd}.yml`은 권역·환경 조합별 이미지명, 대상 VM, 포트, 볼륨, 런타임 환경변수를 둡니다.

~~~yaml
build_name: delivery-template-kr-prd
region_param: KR
spring_profile: "kr,prd"

docker_registry: ghcr.io
dockerfile: Dockerfile

target_servers:
  - name: kr-prd-app-1
    host: kr-prd-app-1.example.internal
    extra_env:
      APM_TARGET_NAME: DELIVERY_TEMPLATE_KR_PRD_01

port_mapping: "8080:8080"
health_check_port: 8080
health_check_path: /actuator/health
~~~

## 빌드와 배포

`build.yml`은 GHCR 로그인, 요청 태그와 dangling 이미지 정리, 불변 태그 이미지 빌드·push를 `community.docker.docker_login`, `docker_image`, `docker_prune`, `docker_image_info`로 처리합니다.

`deploy.yml`은 Environment 시크릿의 SSH 키와 known-hosts를 controller 임시 경로에 만들고, `target_servers`를 runtime inventory로 등록합니다. 각 대상 VM은 SSH로만 접근합니다. `deploy_single_server.yml`은 GHCR 로그인, 지정된 태그 pull, Docker network/container 재생성, `/actuator/health` 확인, 이전 이미지 정리를 모두 `community.docker` 모듈로 처리합니다. Docker CLI 문자열이나 Docker Remote API 포트는 사용하지 않습니다.

GitHub Environments를 `kr-stg`, `kr-prd`, `eu-stg`, `eu-prd`, `na-stg`, `na-prd`로 만들고 아래 시크릿을 각각 설정합니다.

- `SSH_PRIVATE_KEY`
- `SSH_KNOWN_HOSTS`

`prd` Environment에는 required reviewer 또는 deployment protection rule을 설정해 운영 배포를 보호하는 것을 권장합니다.

## 로컬 검증

Java 21, Maven, Python이 필요합니다.

~~~bash
cd ansible-delivery-template
python -m pip install ansible-core==2.17.7 requests
ansible-galaxy collection install \
  --requirements-file .github/ansible/requirements.yml \
  --collections-path .ansible/collections

export ANSIBLE_CONFIG=.github/ansible/ansible.cfg
export DEPLOY_COUNTRY=kr
export DEPLOY_ENV=stg
export IMAGE_VERSION=v1.local

ansible-playbook .github/ansible/playbooks/test.yml
~~~

로컬 build/push에는 아래 값만 추가합니다. GitHub Actions에서는 기본 GitHub 환경변수를 Ansible이 자동으로 사용합니다.

~~~bash
export REGISTRY_NAMESPACE=<github-owner-or-namespace>
export REGISTRY_USERNAME=<github-username>
export REGISTRY_TOKEN=<github-token-with-packages-write>
ansible-playbook .github/ansible/playbooks/build.yml
~~~
