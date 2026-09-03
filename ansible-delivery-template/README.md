# Ansible Delivery Template

GitHub Actions에서 트리거하고, 실제 릴리스 흐름은 Ansible 플레이북으로 통일하는 배포 템플릿입니다.

현재 워크스페이스에서는 하위 디렉터리로 만들었고, 이 디렉터리를 별도 GitHub 저장소로 분리하면 `.github/workflows` 경로가 그대로 저장소 루트 기준 구조가 됩니다.

핵심 목표는 아래와 같습니다.

- `북미 / 유럽 / 내수`와 `검증 / 운영`을 분리해서 변수 중복을 줄입니다.
- `Harbor 로그인 -> 이미지 빌드 -> 푸시 -> 서버 접속 -> pull/up 배포`를 쉘 스크립트가 아니라 Ansible 태스크로 관리합니다.
- GitHub Actions는 입력과 시크릿 주입만 담당하고, 배포 로직은 플레이북 하나로 유지합니다.

## 구조

```text
ansible-delivery-template/
├── .github/workflows/
│   ├── deploy.yml
│   └── validate.yml
├── inventories/
│   ├── hosts.yml
│   └── group_vars/
│       ├── all.yml
│       ├── north_america.yml
│       ├── europe.yml
│       ├── domestic.yml
│       ├── validation.yml
│       ├── production.yml
│       └── {region}_{stage}.yml
├── playbooks/release.yml
├── roles/
│   ├── release_image/
│   └── deploy_app/
├── templates/
│   ├── docker-compose.yml.j2
│   └── app.env.j2
├── Dockerfile
└── static/index.html
```

## 변수 분리 방식

호스트는 `north_america_validation`, `europe_production` 같은 조합 그룹에 속합니다.

- `group_vars/north_america.yml`: 지역 공통값
- `group_vars/validation.yml`: 스테이지 공통값
- `group_vars/north_america_validation.yml`: 조합별 override

즉, 한 타깃을 배포할 때 지역값 + 스테이지값 + 타깃 override가 합쳐집니다.

## 배포 흐름

`playbooks/release.yml`은 두 단계로 움직입니다.

1. GitHub Actions 러너에서 Harbor 로그인 후 `docker build` / `docker push`
2. 대상 서버에서 Harbor 로그인 후 `docker compose pull` / `docker compose up -d`

운영 환경은 기본적으로 `serial: 1`로 굴리게 해 두었고, 검증 환경은 전체 호스트를 한 번에 배포하게 설정했습니다.

## GitHub 설정

GitHub Environments를 아래 이름으로 만드는 것을 전제로 잡았습니다.

- `north_america_validation`
- `north_america_production`
- `europe_validation`
- `europe_production`
- `domestic_validation`
- `domestic_production`

각 Environment에는 최소 아래 시크릿이 필요합니다.

- `HARBOR_USERNAME`
- `HARBOR_PASSWORD`
- `SSH_PRIVATE_KEY`
- `SSH_KNOWN_HOSTS`

## 실제 프로젝트에 붙일 때 바꿀 부분

- `inventories/hosts.yml`의 실제 서버 주소
- `inventories/group_vars/*.yml`의 도메인, Harbor project, 런타임 env
- 루트 `Dockerfile`과 애플리케이션 소스
- 필요하면 `templates/docker-compose.yml.j2`의 볼륨, 헬스체크, 추가 서비스

샘플 `Dockerfile`과 `static/index.html`은 플레이북이 곧바로 빌드 가능한 최소 예시입니다. 실제 서비스 레포로 옮길 때는 이 둘을 서비스 코드로 교체하면 됩니다.

## 로컬 검증

```bash
cd ansible-delivery-template
python -m pip install ansible-core==2.17.7
ansible-inventory --graph
ansible-playbook playbooks/release.yml --syntax-check -e target_group=north_america_validation
```

수동 배포 테스트 예시는 아래처럼 돌릴 수 있습니다.

```bash
cd ansible-delivery-template
export HARBOR_USERNAME=...
export HARBOR_PASSWORD=...
ansible-playbook playbooks/release.yml \
  -e target_group=north_america_validation \
  -e release_tag=manual-test-001 \
  -e deploy_only=true
```
