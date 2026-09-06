# VM Migration Ansible CI/CD Template

신규 클라우드 환경으로 VM을 이관할 때, 권역별·환경별 설정과 반복 배포 단계를 분리한 GitHub Actions 템플릿입니다.

GitHub와 연결된 자동화 자산은 모두 `.github` 아래에 둡니다. [cicd.yml](.github/workflows/cicd.yml)은 `test.yml -> build.yml -> deploy.yml` 세 reusable workflow만 호출하고, 각 workflow가 `.github/ansible`의 실제 Ansible 플레이북을 실행합니다. 국가·환경별 값과 빌드·배포 구현은 Ansible에만 둡니다.

## 구조

~~~text
.
├── .github/
│   ├── actions/
│   │   └── setup-ansible/action.yml
│   ├── ansible/
│   │   ├── ansible.cfg
│   │   ├── hosts.yml                      # controller only; targets are added at runtime
│   │   ├── requirements.yml               # pinned community.docker collection
│   │   ├── playbooks/                    # 모든 국가/환경에서 재사용
│   │   │   ├── test.yml
│   │   │   ├── build.yml
│   │   │   ├── deploy.yml
│   │   │   └── deploy_single_server.yml
│   │   └── vars/
│   │       ├── environments/{stg,prd}.yml # 환경 공통 메타데이터
│   │       └── regions/                   # 권역 + 환경별 실제 배포 값
│   │           ├── kr/{stg,prd}.yml
│   │           ├── eu/{stg,prd}.yml
│   │           └── na/{stg,prd}.yml
│   └── workflows/
│       ├── cicd.yml                      # 진입 workflow와 stage 순서
│       ├── test.yml                      # Ansible test.yml 호출
│       ├── build.yml                     # Ansible build.yml 호출
│       ├── deploy.yml                    # Ansible deploy.yml 호출
│       └── validate.yml
├── Dockerfile
└── static/index.html
~~~

`environments`는 검증/운영 공통 정책을, `regions`는 KR/EU/NA와 환경 조합별 레지스트리·대상 VM·포트·볼륨·런타임 환경변수를 의미합니다.

## 호출 흐름

~~~text
cicd.yml
  -> test.yml   -> .github/ansible/playbooks/test.yml
  -> build.yml  -> .github/ansible/playbooks/build.yml
  -> deploy.yml -> .github/ansible/playbooks/deploy.yml
                  -> deploy_single_server.yml
~~~

`cicd.yml`은 branch trigger, 수동 `country`/`deploy_env` 입력, `test -> build -> deploy` 의존성, 불변 이미지 버전만 관리합니다. 레지스트리 주소, 프로젝트 이름, Dockerfile, 대상 서버, 포트, 볼륨, 애플리케이션 환경변수는 workflow에 두지 않습니다.

나중에 특정 권역이나 환경에 별도 scan, 승인, migration 같은 단계가 필요해지면 그때만 별도 reusable workflow를 추가해 해당 stage 앞뒤에 연결합니다. 현재는 공통 stage만 유지합니다.

## 역할 분리

| 위치 | 책임 |
| --- | --- |
| `.github/workflows/cicd.yml` | 진입점: branch/manual 문맥과 stage 순서 정의 |
| `.github/workflows/test.yml` | checkout, Ansible 준비, test playbook 호출 |
| `.github/workflows/build.yml` | checkout, Ansible 준비, build playbook 호출 |
| `.github/workflows/deploy.yml` | Environment 시크릿 준비, deploy playbook 호출 |
| `.github/ansible/vars/environments/{stg,prd}.yml` | SSH 사용자/포트, 테스트 명령, 순차 배포 수, 이미지 정리 정책 |
| `.github/ansible/vars/regions/{region}/{env}.yml` | 이미지 이름, 레지스트리, Dockerfile, 대상 VM, 포트, 볼륨, 런타임 환경변수 |
| `.github/ansible/playbooks/build.yml` | GHCR 로그인, 불변 이미지 버전 생성, 로컬 이미지 정리, Docker API build/push |
| `.github/ansible/playbooks/deploy.yml` | 대상 VM 동적 inventory 생성과 순차 배포 |
| `.github/ansible/playbooks/deploy_single_server.yml` | VM 1대의 pull, Docker 네트워크/컨테이너 재생성, 헬스체크, 이전 이미지 정리 |

## Ansible 변수 로딩

각 playbook은 내부 `vars_files`에서 아래 두 파일을 직접 로드합니다. workflow가 YAML을 파싱하거나 수십 개의 `-e` 인자를 전달하지 않습니다.

~~~text
.github/ansible/vars/environments/${DEPLOY_ENV}.yml
.github/ansible/vars/regions/${DEPLOY_COUNTRY}/${DEPLOY_ENV}.yml
~~~

국가와 환경의 실제 차이는 `.github/ansible/vars/regions/{region}/{env}.yml` 한 파일에 평면으로 둡니다. 서버별 `extra_env`와 공통 `extra_env`를 함께 쓸 수 있습니다.

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

`build.yml`은 지정된 이미지 태그와 dangling 이미지를 정리한 뒤, 권역별 `REGION_PARAMETER`, 버전, Git revision OCI label을 넣어 이미지를 빌드합니다. 그 다음 `ghcr.io/<owner>/<build_name>:v1.<run>`에 push합니다. 이 단계는 `community.docker.docker_login`, `docker_image`, `docker_prune`, `docker_image_info`로 Docker API를 호출하며, CLI 문자열을 조립하지 않습니다.

`deploy.yml`은 Environment 시크릿으로 받은 SSH 키와 known-hosts를 runner 임시 경로에 안전하게 준비하고, `target_servers`를 SSH runtime inventory로 등록합니다. `deploy_single_server.yml`은 각 VM에서 GHCR 로그인, 명시된 불변 태그 pull, Docker 네트워크와 컨테이너 재생성, 포트/HTTP 헬스체크, 이전 이미지 제거, 다음 서버 전 안정화 대기를 수행합니다. 환경변수, 포트, 볼륨, 레이블, 재시작 정책은 `community.docker.docker_container` 인자로 직접 전달합니다. Docker Remote API 포트는 열지 않고 SSH로만 VM Docker를 실행합니다.

Compose 파일과 Compose 바이너리 의존성은 제거했습니다. 컨트롤러와 대상 VM에는 Docker API 1.25 이상, Python `requests`, 그리고 Docker socket에 접근할 수 있는 배포 사용자가 필요합니다. 컬렉션은 `.github/ansible/requirements.yml`에서 `community.docker` 5.2.2로 고정하고 setup action이 설치합니다.

## GitHub 환경과 시크릿

GitHub Environments를 `kr-stg`, `kr-prd`, `eu-stg`, `eu-prd`, `na-stg`, `na-prd`로 만들고 각 Environment에 아래 시크릿을 둡니다.

- `SSH_PRIVATE_KEY`
- `SSH_KNOWN_HOSTS`

`deploy.yml` reusable workflow가 해당 Environment를 직접 선언하므로, environment 시크릿을 workflow input으로 전달할 필요가 없습니다. `GITHUB_TOKEN`은 build/deploy workflow에서 `github.token`으로 사용합니다. 최상위 workflow는 reusable workflow의 권한을 높일 수 없다는 제약 때문에 `packages: write`를 허용하고, deploy workflow는 이를 `packages: read`로 낮춥니다.

`prd` Environment에는 required reviewer 또는 deployment protection rule을 설정해 운영 배포를 보호하는 것을 권장합니다. 실제 이관 환경에서는 권역/환경 변수 파일의 예시 호스트, 볼륨과 환경변수를 실값으로 교체합니다.

## 로컬 검증

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

ansible-inventory --graph
ansible-playbook .github/ansible/playbooks/test.yml
~~~

실제 로컬 빌드/push에는 아래 값만 추가합니다. GitHub Actions에서는 기본 GitHub 환경변수를 Ansible이 자동으로 사용합니다.

~~~bash
export REGISTRY_NAMESPACE=<github-owner-or-namespace>
export REGISTRY_USERNAME=<github-username>
export REGISTRY_TOKEN=<github-token-with-packages-write>
ansible-playbook .github/ansible/playbooks/build.yml
~~~
