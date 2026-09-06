# VM Migration Ansible CI/CD Template

신규 클라우드 환경으로 VM을 이관할 때, GitLab CI에 섞여 있던 권역별·환경별 설정과 반복 배포 스테이지를 분리한 GitHub Actions 템플릿입니다.

GitLab의 `include: local`처럼 임의 경로 YAML을 합치는 기능은 GitHub Actions에 없습니다. 대신 `.github/workflows` 아래의 `workflow_call` reusable workflow를 계층적으로 호출합니다. 이 템플릿은 GitLab의 `root -> common -> country pipeline` 구성을 GitHub 방식으로 옮기고, 실제 테스트·빌드·배포 구현은 Ansible 플레이북에 남깁니다.

## 구조

~~~text
.
├── .cicd/
│   ├── pipeline/                         # Ansible 권역 식별자
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
│   └── workflows/
│       ├── cicd.yml                      # 진입 workflow
│       ├── pipeline-router.yml            # 국가별 workflow 선택
│       ├── pipeline-{kr,eu,na}.yml        # 국가별 pipeline
│       ├── pipeline-common.yml             # test -> build -> deploy 연결
│       ├── stage-{test,build,deploy}.yml  # stage별 reusable workflow
│       └── validate.yml
├── Dockerfile
└── static/index.html
~~~

`.cicd/pipeline/*.yml`은 Ansible이 읽는 권역 식별자입니다. GitHub reusable workflow는 GitHub 제약상 반드시 `.github/workflows/*.yml`에 둡니다.

## GitHub 호출 흐름

~~~text
cicd.yml
  -> pipeline-router.yml
    -> pipeline-kr.yml | pipeline-eu.yml | pipeline-na.yml
      -> pipeline-common.yml
        -> stage-test.yml   -> ansible/playbooks/test.yml
        -> stage-build.yml  -> ansible/playbooks/build.yml
        -> stage-deploy.yml -> ansible/playbooks/deploy.yml
                              -> deploy_single_server.yml
~~~

[cicd.yml](.github/workflows/cicd.yml)은 브랜치 trigger, 수동 `country`/`deploy_env` 입력, 이미지 버전만 정해 router workflow를 호출합니다. 레지스트리 주소, 프로젝트 이름, Dockerfile, 대상 서버, 포트, 볼륨, 애플리케이션 환경변수는 workflow에 두지 않습니다.

`pipeline-router.yml`은 GitHub가 동적 `uses` 경로를 지원하지 않는 제약을 처리합니다. 세 개의 정적 reusable workflow 중 입력 country와 일치하는 것 하나만 호출합니다. 각 국가 workflow는 country 값을 고정해 공통 pipeline에 넘기므로, GitLab의 국가별 include 파일과 같은 역할을 합니다.

`pipeline-common.yml`은 `test -> build -> deploy` 의존성만 정의합니다. 세 stage workflow는 준비와 Ansible 플레이북 호출만 하며, 국가·환경별 설정 해석은 Ansible이 담당합니다.

## 역할 분리

| 위치 | 책임 |
| --- | --- |
| `.github/workflows/cicd.yml` | 진입점: branch/manual 문맥과 불변 이미지 버전 전달 |
| `.github/workflows/pipeline-router.yml` | country에 맞는 정적 pipeline workflow 선택 |
| `.github/workflows/pipeline-{kr,eu,na}.yml` | 국가를 고정하고 공통 pipeline 호출 |
| `.github/workflows/pipeline-common.yml` | stage 간 순서만 정의 |
| `.github/workflows/stage-*.yml` | checkout, Ansible 준비, 해당 playbook 호출 |
| `.cicd/pipeline/{kr,eu,na}.yml` | Ansible 권역 식별자 검증 |
| `.cicd/vars/{stg,prd}.yml` | SSH 사용자/포트, 테스트 명령, 순차 배포 수, 이미지 정리 정책 |
| `.cicd/ansible/vars/{country}/{env}.yml` | 이미지 이름, 레지스트리, Dockerfile, 대상 VM, 포트, 볼륨, 런타임 환경변수 |
| `.cicd/ansible/playbooks/build.yml` | GHCR 로그인, 불변 이미지 버전 생성, 로컬 이미지 정리, Docker build/push |
| `.cicd/ansible/playbooks/deploy.yml` | 대상 VM 동적 inventory 생성과 순차 배포 |
| `.cicd/ansible/playbooks/deploy_single_server.yml` | VM 1대의 pull, Compose 재생성, 헬스체크, 이전 이미지 정리 |

## Ansible 변수 로딩

각 playbook은 내부 `vars_files`에서 아래 세 파일을 직접 로드합니다. workflow가 YAML을 파싱하거나 수십 개의 `-e` 인자를 전달하지 않습니다.

~~~text
.cicd/pipeline/${DEPLOY_COUNTRY}.yml
.cicd/vars/${DEPLOY_ENV}.yml
.cicd/ansible/vars/${DEPLOY_COUNTRY}/${DEPLOY_ENV}.yml
~~~

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

`build.yml`은 지정된 이미지 태그와 dangling 이미지를 정리한 뒤, 권역별 `REGION_PARAMETER`, 버전, Git revision OCI label을 넣어 이미지를 빌드합니다. 그 다음 `ghcr.io/<owner>/<build_name>:v1.<run>`에 push합니다.

`deploy.yml`은 `target_servers`를 SSH runtime inventory로 등록합니다. `deploy_single_server.yml`은 각 VM에서 GHCR 로그인, 명시된 불변 태그 pull, Compose 파일 렌더링과 컨테이너 재생성, 포트/HTTP 헬스체크, 이전 이미지 제거, 다음 서버 전 안정화 대기를 수행합니다. Docker Remote API 포트는 열지 않고 SSH로만 VM Docker를 실행합니다.

빌드는 runner Docker CLI를 사용하고, 배포는 `docker compose`와 legacy `docker-compose`를 자동 선택합니다. 따라서 VM의 Docker/Compose 세대가 달라도 같은 플레이북을 재사용할 수 있습니다.

## GitHub 환경과 시크릿

GitHub Environments를 `kr-stg`, `kr-prd`, `eu-stg`, `eu-prd`, `na-stg`, `na-prd`로 만들고 각 Environment에 아래 시크릿을 둡니다.

- `SSH_PRIVATE_KEY`
- `SSH_KNOWN_HOSTS`

`stage-deploy.yml`이 해당 Environment를 직접 선언하므로, environment 시크릿을 중첩 workflow input으로 전달할 필요가 없습니다. `GITHUB_TOKEN`은 build/deploy stage에서 `github.token`으로 사용합니다. 최상위 workflow는 nested workflow의 권한을 높일 수 없다는 제약 때문에 `packages: write`를 허용하고, deploy stage는 이를 `packages: read`로 낮춥니다.

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

실제 로컬 빌드/push에는 아래 값만 추가합니다. GitHub Actions에서는 기본 GitHub 환경변수를 Ansible이 자동으로 사용합니다.

~~~bash
export REGISTRY_NAMESPACE=<github-owner-or-namespace>
export REGISTRY_USERNAME=<github-username>
export REGISTRY_TOKEN=<github-token-with-packages-write>
ansible-playbook .cicd/ansible/playbooks/build.yml
~~~
