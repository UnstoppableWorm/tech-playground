# VM Migration Ansible CI/CD Template

신규 클라우드 환경으로 VM을 이관할 때, GitLab CI에 섞여 있던 권역별·환경별 설정과 반복 배포 스테이지를 분리한 GitHub Actions 템플릿입니다.

핵심은 GitHub Actions가 배포 로직을 갖지 않는다는 점입니다. [cicd.yml](.github/workflows/cicd.yml)은 `test -> build -> deploy` 플레이북을 호출할 뿐이고, 국가·환경 변수 파일 선택, GHCR 로그인, 이미지 버저닝, 빌드, push, VM 접속, pull, 헬스체크, 기존 이미지 정리는 Ansible 플레이북 안에서 처리합니다.

## 구조

~~~text
.
├── .cicd/
│   ├── pipeline/                         # 권역 식별자
│   │   ├── kr.yml
│   │   ├── eu.yml
│   │   └── na.yml
│   ├── vars/                             # 검증/운영 공통 메타데이터
│   │   ├── stg.yml
│   │   └── prd.yml
│   └── ansible/
│       ├── inventories/hosts.yml
│       ├── playbooks/                    # 모든 국가/환경에서 재사용
│       │   ├── test.yml
│       │   ├── build.yml
│       │   ├── deploy.yml
│       │   └── deploy_single_server.yml
│       ├── templates/
│       └── vars/                         # 국가 + 환경별 실제 배포 값
│           ├── kr/{stg,prd}.yml
│           ├── eu/{stg,prd}.yml
│           └── na/{stg,prd}.yml
├── .github/
│   ├── actions/setup-ansible/action.yml
│   └── workflows/{cicd,validate}.yml
├── Dockerfile
└── static/index.html
~~~

## 역할 분리

| 위치 | 책임 |
| --- | --- |
| `.github/workflows/cicd.yml` | 브랜치/수동 실행 문맥을 `DEPLOY_COUNTRY`, `DEPLOY_ENV`, `IMAGE_VERSION`으로 정하고 세 플레이북 호출 |
| `.cicd/pipeline/{kr,eu,na}.yml` | 권역 식별자 검증 |
| `.cicd/vars/{stg,prd}.yml` | SSH 사용자/포트, 테스트 명령, 순차 배포 수, 이미지 정리 정책 |
| `.cicd/ansible/vars/{country}/{env}.yml` | 이미지 이름, 레지스트리, Dockerfile, 대상 VM, 포트, 볼륨, 런타임 환경변수 |
| `.cicd/ansible/playbooks/test.yml` | 선택된 국가/환경 파일을 읽고 애플리케이션 테스트 실행 |
| `.cicd/ansible/playbooks/build.yml` | GHCR 로그인, 불변 이미지 버전 생성, 로컬 이미지 정리, Docker build/push |
| `.cicd/ansible/playbooks/deploy.yml` | 대상 VM 동적 inventory 생성, 순차 배포 진행 |
| `.cicd/ansible/playbooks/deploy_single_server.yml` | VM 1대에서 pull, Compose 재생성, 헬스체크, 이전 이미지 정리 |

각 플레이북은 내부 `vars_files`에서 아래 세 파일을 직접 로드합니다. 따라서 workflow가 YAML을 파싱하거나 수십 개의 `-e` 인자를 전달하지 않습니다.

~~~text
.cicd/pipeline/${DEPLOY_COUNTRY}.yml
.cicd/vars/${DEPLOY_ENV}.yml
.cicd/ansible/vars/${DEPLOY_COUNTRY}/${DEPLOY_ENV}.yml
~~~

## GitHub Actions 흐름

`feature/**`와 `stg` 브랜치는 `kr/stg`, `prd` 브랜치는 `kr/prd`를 자동 선택합니다. `workflow_dispatch`에서는 `country`와 `deploy_env`만 선택해 EU/NA 또는 원하는 조합을 실행합니다. 이미지 버전은 항상 `v1.<GitHub run number>`으로 하나만 만들어 build와 deploy가 공유합니다.

~~~text
GitHub Actions
  test.yml
  build.yml
  deploy.yml
    deploy_single_server.yml (대상 VM마다 순차 실행)
~~~

`cicd.yml`에는 레지스트리 주소, 프로젝트 이름, Dockerfile, 대상 서버, 포트, 볼륨, 애플리케이션 환경변수가 없습니다. 이 값들은 모두 Ansible 변수 파일에만 둡니다. GitHub Actions가 전달하는 것은 선택 문맥 세 개뿐입니다.

`build` job은 GHCR `packages: write`, `deploy` job은 `packages: read` 권한을 갖습니다. 플레이북은 GitHub 기본 환경변수 `GITHUB_REPOSITORY_OWNER`, `GITHUB_ACTOR`, `GITHUB_TOKEN`을 기본 GHCR 자격 증명으로 사용하므로 workflow에 레지스트리 변수를 추가할 필요가 없습니다.

## 국가/환경 변수 예시

국가와 환경의 실제 차이는 `.cicd/ansible/vars/{country}/{env}.yml` 한 파일에 평면으로 둡니다. 서버별 `extra_env`와 공통 `extra_env`를 함께 쓸 수 있습니다.

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
  - name: kr-prd-app-2
    host: kr-prd-app-2.example.internal
    extra_env:
      APM_TARGET_NAME: DELIVERY_TEMPLATE_KR_PRD_02

port_mapping: "8080:80"
deployment_root: /opt/delivery-template/kr-prd
restart_policy: always
extra_volumes:
  - /var/log/delivery-template:/app/log
extra_env:
  TZ: Asia/Seoul
  APP_REGION: kr
health_check_port: 8080
health_check_path: /
stabilize_seconds: 15
~~~

## Ansible 빌드와 배포

`build.yml`은 지정된 이미지 태그를 먼저 정리하고 dangling 이미지도 정리합니다. 그 다음 권역별 `REGION_PARAMETER`, 버전과 revision OCI label을 넣어 Docker 이미지를 빌드하고 `ghcr.io/<owner>/<build_name>:v1.<run>`에 push합니다.

`deploy.yml`은 `target_servers`를 SSH runtime inventory로 등록합니다. `deploy_single_server.yml`은 각 VM에서 GHCR 로그인, 명시된 불변 태그 pull, Compose 파일 렌더링과 컨테이너 재생성, 포트/HTTP 헬스체크, 이전 이미지 제거, 필요 시 다음 서버 전 안정화 대기를 수행합니다. Docker Remote API 포트는 열지 않고 SSH로만 VM Docker를 실행합니다.

빌드 runner와 대상 VM의 Docker/Compose 버전이 달라도, build는 runner Docker CLI를 사용하고 deploy는 `docker compose`와 legacy `docker-compose`를 자동 선택합니다.

## GitHub 환경과 시크릿

GitHub Environments를 `kr-stg`, `kr-prd`, `eu-stg`, `eu-prd`, `na-stg`, `na-prd`로 만들고 각 Environment에 아래 시크릿만 둡니다.

- `SSH_PRIVATE_KEY`
- `SSH_KNOWN_HOSTS`

`prd` Environment에는 required reviewer 또는 deployment protection rule을 설정해 운영 배포를 보호하는 것을 권장합니다. 실제 이관 환경에서는 국가/환경 변수 파일의 예시 호스트, 배포 경로, 볼륨과 환경변수를 실값으로 교체합니다.

## 로컬 검증

~~~bash
cd ansible-delivery-template
python -m pip install ansible-core==2.17.7
export ANSIBLE_CONFIG=.cicd/ansible/ansible.cfg
export DEPLOY_COUNTRY=kr
export DEPLOY_ENV=stg
export IMAGE_VERSION=v1.local

ansible-inventory --graph
ansible-playbook .cicd/ansible/playbooks/test.yml
~~~

실제 로컬 빌드/push에는 아래 값만 추가합니다. CI에서는 기본 GitHub 환경변수를 플레이북이 자동으로 사용합니다.

~~~bash
export REGISTRY_NAMESPACE=<github-owner-or-namespace>
export REGISTRY_USERNAME=<github-username>
export REGISTRY_TOKEN=<github-token-with-packages-write>
ansible-playbook .cicd/ansible/playbooks/build.yml
~~~
