# VM Migration Ansible CI/CD Template

신규 클라우드 환경으로 VM을 이관할 때, GitLab CI 안에 섞여 있던 권역별·환경별 설정과 반복된 배포 스테이지를 분리한 구조를 GitHub Actions용으로 옮긴 템플릿입니다.

GitHub Actions는 수동 실행, 시크릿 주입, runner 선택만 담당합니다. 실제 Harbor 로그인, 이미지 빌드와 push, VM 접속, 이미지 pull, Compose 배포 흐름은 Ansible 플레이북으로 고정해 여러 애플리케이션이 같은 절차를 재사용할 수 있게 합니다.

이 디렉터리는 현재 워크스페이스에서는 하위 프로젝트입니다. 별도 저장소로 분리하면 아래 구조가 저장소 루트 기준으로 그대로 동작합니다.

## 구조

~~~text
.
├── .cicd/
│   ├── pipeline/                         # 권역별 빌드/배포 구성
│   │   ├── kr.yml
│   │   ├── eu.yml
│   │   └── na.yml
│   ├── ansible/
│   │   ├── inventories/hosts.yml
│   │   ├── playbooks/                    # 모든 환경에서 재사용
│   │   │   ├── build.yml
│   │   │   └── deploy.yml
│   │   ├── roles/
│   │   │   ├── build_image/
│   │   │   └── deploy_container/
│   │   ├── templates/
│   │   └── vars/                         # 국가 + 환경별 값
│   │       ├── kr/{stg,prd}.yml
│   │       ├── eu/{stg,prd}.yml
│   │       └── na/{stg,prd}.yml
│   └── vars/                             # runner, 배포 정책 메타데이터
│       ├── stg.yml
│       └── prd.yml
├── .github/workflows/
│   ├── cicd.yml
│   └── validate.yml
├── Dockerfile
└── static/index.html
~~~

## 분리 기준

| 위치 | 책임 |
| --- | --- |
| .cicd/pipeline/{kr,eu,na}.yml | 권역별 파이프라인 이름과 해당 권역의 변수 루트 |
| .cicd/vars/{stg,prd}.yml | GitHub runner, 순차 배포 수, 이미지 정리, 환경 공통 런타임 값 |
| .cicd/ansible/vars/{country}/{env}.yml | Harbor 주소, 대상 VM 그룹, 포트, 도메인, 국가별 런타임 환경변수 |
| .cicd/ansible/playbooks/*.yml | 빌드와 배포의 공통 절차 |

권역이나 검증/운영 환경을 추가할 때는 공통 플레이북을 복사하지 않습니다. 파이프라인 파일과 변수 파일만 추가 또는 수정합니다.

## GitHub Actions 흐름

workflow_dispatch에서 country, deploy_env, release_mode, release_tag을 선택하면 다음 파일이 함께 주입됩니다.

~~~text
kr + stg
  -> .cicd/pipeline/kr.yml
  -> .cicd/vars/stg.yml
  -> .cicd/ansible/vars/kr/stg.yml
  -> build.yml
  -> deploy.yml
~~~

GitLab의 build_kr, deploy_kr 같은 국가별 job은 GitHub Actions의 입력값과 .cicd/pipeline/kr.yml로 대체했습니다. 환경별 runner 메타데이터는 .cicd/vars/{stg,prd}.yml에서 읽어 build/deploy job의 runs-on으로 사용합니다.

## Docker 호환성

빌드에는 CI runner에 설치된 Docker CLI를 사용하며 특정 docker:A, docker:B 이미지에 묶지 않습니다. 배포 플레이북은 대상 VM에서 docker compose를 먼저 확인하고, 없으면 legacy docker-compose를 자동 선택합니다. 따라서 VM의 Compose 세대가 달라도 같은 플레이북을 재사용할 수 있습니다.

## GitHub 환경과 시크릿

GitHub Environments를 아래처럼 권역과 환경 조합으로 생성합니다.

- kr-stg, kr-prd
- eu-stg, eu-prd
- na-stg, na-prd

각 Environment에 아래 시크릿을 설정합니다.

- HARBOR_USERNAME
- HARBOR_PASSWORD
- SSH_PRIVATE_KEY
- SSH_KNOWN_HOSTS

SSH_KNOWN_HOSTS는 대상 VM의 host key를 포함해야 합니다. 실제 이관 환경에서는 .cicd/ansible/inventories/hosts.yml의 예시 호스트와 각 변수 파일의 Harbor, 도메인, 배포 경로를 실값으로 바꿉니다.

## 로컬 검증

~~~bash
cd ansible-delivery-template
python -m pip install ansible-core==2.17.7
export ANSIBLE_CONFIG=.cicd/ansible/ansible.cfg

ansible-inventory --graph
ansible-playbook .cicd/ansible/playbooks/build.yml --syntax-check -e country=kr -e deploy_env=stg -e release_tag=syntax-check -e @.cicd/pipeline/kr.yml -e @.cicd/vars/stg.yml -e @.cicd/ansible/vars/kr/stg.yml
~~~

실제 수동 배포는 build.yml과 deploy.yml에 같은 세 개의 변수 파일을 주입해 실행합니다. GitHub Actions workflow가 이 호출을 그대로 자동화합니다.
