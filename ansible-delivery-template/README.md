# VM Migration Ansible CI/CD Template

신규 클라우드 환경으로 VM을 이관할 때, GitLab CI 안에 섞여 있던 권역별·환경별 설정과 반복 배포 스테이지를 분리한 GitHub Actions 템플릿입니다.

GitHub Actions는 브랜치 규칙 또는 수동 실행, runner 선택, GitHub Container Registry(GHCR) 인증을 담당합니다. 테스트, 이미지 버저닝, 이미지 빌드와 push, VM 접속, 지정 버전 pull, 기존 이미지 정리, Compose 배포는 Ansible 플레이북으로 고정해 여러 애플리케이션이 같은 절차를 재사용합니다.

이 디렉터리는 현재 워크스페이스의 하위 프로젝트입니다. 별도 저장소로 분리하면 아래 구조가 저장소 루트 기준으로 그대로 동작합니다.

## 구조

~~~text
.
├── .cicd/
│   ├── pipeline/                         # 권역별 테스트/빌드/배포 구성
│   │   ├── kr.yml
│   │   ├── eu.yml
│   │   └── na.yml
│   ├── ansible/
│   │   ├── inventories/hosts.yml
│   │   ├── playbooks/                    # 모든 환경에서 재사용
│   │   │   ├── test.yml
│   │   │   ├── build.yml
│   │   │   ├── deploy.yml
│   │   │   └── deploy_single_server.yml
│   │   ├── templates/
│   │   └── vars/                         # 국가 + 환경별 값
│   │       ├── kr/{stg,prd}.yml
│   │       ├── eu/{stg,prd}.yml
│   │       └── na/{stg,prd}.yml
│   └── vars/                             # runner, SSH, 테스트, 배포 정책
│       ├── stg.yml
│       └── prd.yml
├── .github/workflows/
│   ├── cicd.yml
│   └── validate.yml
├── .github/actions/
│   └── setup-ansible/action.yml          # test/build/deploy 공통 준비
├── Dockerfile
└── static/index.html
~~~

## 분리 기준

| 위치 | 책임 |
| --- | --- |
| .cicd/pipeline/{kr,eu,na}.yml | 권역 식별자와 Ansible 변수 루트 |
| .cicd/vars/{stg,prd}.yml | GitHub runner, SSH 사용자/포트, 테스트 명령, 순차 배포 수, 이미지 정리 정책 |
| .github/actions/setup-ansible | checkout, Python, 고정 Ansible 버전을 세 stage에서 공통 사용 |
| .cicd/ansible/vars/{country}/{env}.yml | `build_name`, 레지스트리, Dockerfile, `target_servers`, 포트, 볼륨, 런타임 환경변수 |
| .cicd/ansible/playbooks/build.yml | 레지스트리 로그인, 이전 로컬 태그·dangling 이미지 정리, 권역별 이미지 빌드와 push |
| .cicd/ansible/playbooks/deploy.yml | 대상 그룹 검증과 공통 이미지 정보 계산 |
| .cicd/ansible/playbooks/deploy_single_server.yml | VM 1대의 pull, 재생성, 포트·헬스체크, 이전 이미지 정리, 다음 서버 전 안정화 대기 |

권역이나 검증/운영 환경을 추가할 때는 공통 플레이북을 복사하지 않습니다. 파이프라인 파일과 변수 파일만 추가 또는 수정합니다.

## 국가/환경 변수 파일

사진의 구조처럼 권역과 환경의 실제 차이는 `.cicd/ansible/vars/{country}/{env}.yml` 한 파일에 평면으로 둡니다. `target_servers`에 서버별 `extra_env`를 두면 공통 환경변수와 개별 VM 변수도 함께 관리할 수 있습니다.

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

`docker_registry`는 무료로 테스트 가능한 GHCR을 기본값으로 사용합니다. `registry_namespace`와 로그인 자격 증명은 파일에 넣지 않으며, workflow가 GitHub repository owner와 `GITHUB_TOKEN`으로 주입합니다.

## GitHub Actions 흐름

사진의 GitLab `workflow.rules`와 환경별 `include`는 GitHub Actions의 `push` trigger와 최소 `resolve` job으로 대응합니다. `resolve`는 country/environment, 이미지 버전, runner와 변수 루트만 결정하며 작업명 같은 중복 메타데이터는 읽지 않습니다. 자동 실행은 원본 KR 파이프라인과 같은 기준으로 연결합니다.

| GitHub 이벤트 | 선택 결과 | 실행 흐름 |
| --- | --- | --- |
| `push` to `feature/**` | `kr`, `stg`, `build_and_deploy` | test -> build -> deploy |
| `push` to `stg` | `kr`, `stg`, `build_and_deploy` | test -> build -> deploy |
| `push` to `prd` | `kr`, `prd`, `build_and_deploy` | test -> build -> deploy |
| `workflow_dispatch` | 선택한 country/environment/mode | 선택한 흐름 |

`workflow_dispatch`에서는 `country`, `deploy_env`, `release_mode`, `image_version`을 선택하면 다음 변수 파일이 함께 주입됩니다. EU와 NA, 또는 브랜치 규칙과 다른 조합은 이 수동 실행으로 배포합니다.

~~~text
kr + stg
  -> .cicd/pipeline/kr.yml
  -> .cicd/vars/stg.yml
  -> .cicd/ansible/vars/kr/stg.yml
  -> test.yml
  -> build.yml
  -> deploy.yml
     -> deploy_single_server.yml (대상 VM마다 1회)
~~~

`build_and_deploy` 모드에서는 `test -> build -> deploy` 순서로 실행합니다. 테스트가 실패하면 빌드는 시작하지 않습니다. `deploy_only` 모드는 기존 GHCR 이미지 버전만 pull하여 배포하므로 테스트와 빌드를 건너뜁니다. 이 모드에서는 pull할 기존 `image_version`을 반드시 입력해야 합니다.

브랜치 push는 자동으로 `build_and_deploy`를 선택합니다. `prd` Environment에는 required reviewer 또는 deployment protection rule을 설정해 운영 배포가 승인 후에만 진행되도록 구성하는 것을 권장합니다.

`resolve` job은 `image_version`을 한 번 결정해 출력합니다. 입력하지 않으면 `v1.<GitHub run number>`을 사용하므로, GitLab의 `build.env` dotenv artifact처럼 build와 deploy가 같은 불변 태그를 공유합니다.

`test_stage.command`는 환경 변수 파일에서 관리합니다. 현재 템플릿은 Dockerfile과 정적 페이지를 검증하는 스모크 테스트를 실행합니다. 실제 애플리케이션에서는 같은 위치를 아래처럼 서비스의 테스트 명령으로 바꾸면 됩니다.

~~~yaml
test_stage:
  command:
    - ./gradlew
    - test
~~~

## GHCR 이미지 흐름

`build.yml`은 `ghcr.io/<GitHub 소유자>/<build_name>:<image_version>` 형태의 불변 태그를 생성합니다. 권역 변수 파일의 `dockerfile`과 `region_param`으로 Dockerfile과 `REGION_PARAMETER` build argument를 분리할 수 있습니다.

1. `GITHUB_TOKEN`으로 GHCR에 로그인하고 같은 버전의 로컬 이미지와 dangling 이미지를 정리합니다.
2. 버전, Git revision, 소스 저장소 OCI label과 권역별 build argument를 넣어 이미지를 빌드합니다.
3. 버전 태그를 GHCR에 push합니다.

`deploy.yml`은 `target_servers`를 실행 시점 SSH inventory로 등록한 뒤 `deploy_single_server.yml`을 대상 VM마다 실행합니다. 각 VM은 같은 이미지 버전을 직접 pull하고 국가·환경별 Compose 파일을 렌더링해 컨테이너를 재생성합니다. 포트와 헬스체크가 성공한 뒤에만 이전 이미지 ID를 제거하며, `stabilize_seconds`만큼 다음 VM 배포 전 대기할 수 있습니다.

build job의 `packages: write` 권한과 기본 `GITHUB_TOKEN`만으로 첫 GHCR 패키지가 생성됩니다. deploy job은 `packages: read` 토큰을 대상 VM의 `docker login ghcr.io`에 사용하므로 별도 레지스트리 시크릿이 필요하지 않습니다.

GitHub Actions 밖에서 이미지를 pull하거나 VM에서 장기 토큰으로 pull하려면 GitHub PAT에 `read:packages` 권한을 부여해 `REGISTRY_TOKEN`으로 사용합니다. 익명 pull이 필요하면 첫 push 후 GitHub Packages의 Container package visibility를 Public으로 변경합니다.

## Docker 호환성

빌드에는 CI runner에 설치된 Docker CLI를 사용하며 특정 Docker 이미지 버전에 묶지 않습니다. 배포 플레이북은 대상 VM에서 `docker compose`를 먼저 확인하고, 없으면 legacy `docker-compose`를 자동 선택합니다. 따라서 VM의 Compose 세대가 달라도 같은 플레이북을 재사용할 수 있습니다.

참고 구조처럼 서버별 배포 태스크는 분리했지만, Docker Remote API의 `tcp://<server>:2375`는 열지 않습니다. GitHub Actions runner가 SSH로 VM에 접속해 Docker 명령을 실행하므로 원격 Docker daemon 포트를 외부에 노출할 필요가 없습니다.

## GitHub 환경과 시크릿

GitHub Environments를 아래처럼 권역과 환경 조합으로 생성합니다.

- kr-stg, kr-prd
- eu-stg, eu-prd
- na-stg, na-prd

각 Environment에는 VM 접근용 시크릿만 설정합니다.

- SSH_PRIVATE_KEY
- SSH_KNOWN_HOSTS

`SSH_KNOWN_HOSTS`는 대상 VM의 host key를 포함해야 합니다. 실제 이관 환경에서는 `.cicd/ansible/vars/{country}/{env}.yml`의 예시 호스트, 배포 경로, 볼륨과 환경변수를 실값으로 바꿉니다. `hosts.yml`은 정적 서버 목록이 아니라 런타임 그룹 선언만 보관합니다.

## 로컬 검증

~~~bash
cd ansible-delivery-template
python -m pip install ansible-core==2.17.7
export ANSIBLE_CONFIG=.cicd/ansible/ansible.cfg

ansible-inventory --graph
ansible-playbook .cicd/ansible/playbooks/test.yml \
  -e country=kr -e deploy_env=stg -e project_root="$PWD" \
  -e @.cicd/pipeline/kr.yml -e @.cicd/vars/stg.yml \
  -e @.cicd/ansible/vars/kr/stg.yml
~~~

실제 로컬 GHCR push는 `REGISTRY_USERNAME`, `REGISTRY_TOKEN`, `registry_namespace`를 제공해 `build.yml`을 호출합니다. GitHub Actions workflow는 이 호출을 자동화하며, 저장소 owner를 `registry_namespace`로 주입합니다.
