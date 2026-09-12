# Mac Mini Ansible CI/CD

Mac mini의 격리 GitHub Actions runner에서 Spring Boot 애플리케이션을 검증하고, 테스트하고, 이미지로 빌드한 뒤 같은 runner의 Docker 엔진에 배포하는 예제입니다.

## 실행 구조

~~~text
Mac mini
└── Docker Desktop
    └── isolated runner boundary
        ├── GitHub Actions runner
        └── delivery-template service
~~~

DinD는 runner를 Mac 파일시스템과 분리하는 경계로만 사용합니다. 별도의 DinD 배포 플레이북이나 VM/SSH 배포 경로는 없습니다. workflow가 self-hosted runner에서 시작되면 일반 build/deploy 플레이북이 runner의 Docker socket을 사용합니다.

## 파일 구조

~~~text
tech-playground/
├── .github/workflows/01_cicd.yml
└── ansible-delivery-template/
    ├── ansible.cfg
    ├── Dockerfile
    ├── pom.xml
    └── .github/ansible/
        ├── hosts.yml
        ├── playbooks/
        │   ├── 01_setup_isolated_runner.yml
        │   ├── 02_validate.yml
        │   ├── 03_test.yml
        │   ├── 04_build.yml
        │   └── 05_deploy.yml
        ├── runner/
        ├── tasks/load_delivery_context.yml
        └── vars/
            ├── common/
            └── regions/{kr,eu,na}/{stg,prd}.yml
~~~

## 호출 순서

`01_setup_isolated_runner.yml`은 Mac mini에서 한 번만 실행합니다. 이후 GitHub workflow는 다음 플레이북을 순서대로 실행합니다.

~~~text
02_validate.yml
  -> 03_test.yml
  -> 04_build.yml
  -> 05_deploy.yml
~~~

workflow를 수동 실행할 때 `내수`, `유럽`, `북미` 중 권역을 반드시 클릭해 선택합니다. 초기값인 `선택 필요`로는 테스트, 빌드, 배포를 시작할 수 없습니다. 선택한 값은 각각 `kr`, `eu`, `na` 설정으로 연결됩니다. 배포 환경은 실행 브랜치에서 결정합니다. `prd`는 운영계, `main`, `stg`, `feature/*`는 검증계 설정을 사용하며 그 밖의 브랜치는 `02_validate.yml`에서 실패합니다. checkout한 Git SHA는 별도 입력 없이 이미지 버전으로 사용합니다.

실행 호스트가 한 Mac mini인 것과 배포 모델은 분리되어 있습니다. 서비스명과 소스 저장소처럼 모든 단계가 공유하는 값은 `vars/common/delivery.yml`의 `common`에 둡니다. `regions/{country}/{environment}.yml` 여섯 파일은 단계별 `build`, `deploy` 설정만 소유합니다. 현재 `deploy.targets`에는 환경별 `app-1` 한 개만 있지만 대상을 추가하면 `05_deploy.yml`이 동적 로컬 인벤토리로 등록해 한 대씩 순차 배포합니다. runner 설치 플레이북은 모든 국가와 환경의 target port를 모아 Mac loopback에 자동으로 노출합니다.

~~~yaml
build:
  arguments:
    REGION_PARAMETER: KR

deploy:
  spring_profile: "kr,stg"
  restart_policy: unless-stopped
  cleanup:
    remove_previous_image: true
    prune_dangling_images: false
  environment:
    APP_REGION: kr
    APP_BASE_URL: https://stg-kr.example.com
  volumes:
    - /var/log/delivery-template-kr-stg:/app/log
  targets:
    - name: app-1
      port_mapping: "18080:8080"
      environment:
        APM_TARGET_NAME: DELIVERY_TEMPLATE_KR_STG_01
      volumes: []
~~~

## Runner 준비

~~~bash
cd ansible-delivery-template
python -m pip install ansible-core==2.17.7 docker==7.1.0 requests
ansible-galaxy collection install \
  --requirements-file .github/ansible/requirements.yml \
  --collections-path .ansible/collections

ansible-playbook .github/ansible/playbooks/01_setup_isolated_runner.yml \
  --extra-vars "configure_runner=false"
~~~

GitHub 저장소에서 Linux ARM64 self-hosted runner 등록 토큰을 발급한 뒤 runner를 등록합니다.

~~~bash
ansible-playbook .github/ansible/playbooks/01_setup_isolated_runner.yml \
  --extra-vars "runner_name=mac-mini-kr-stg-runner" \
  --extra-vars "registration_token=$GITHUB_RUNNER_TOKEN"
~~~

## 로컬 실행

Mac 터미널에서 직접 build/deploy를 확인할 때만 격리 Docker API를 지정합니다. GitHub runner 안에서는 `/var/run/docker.sock`이 이미 연결되므로 필요하지 않습니다.

~~~bash
export DOCKER_HOST=tcp://127.0.0.1:23750
export DELIVERY_SCOPE_JSON='{"delivery_scope":{"country":"kr","environment":"stg"}}'

ansible-playbook .github/ansible/playbooks/02_validate.yml --extra-vars "$DELIVERY_SCOPE_JSON"
ansible-playbook .github/ansible/playbooks/03_test.yml --extra-vars "$DELIVERY_SCOPE_JSON"
ansible-playbook .github/ansible/playbooks/04_build.yml --extra-vars "$DELIVERY_SCOPE_JSON"
ansible-playbook .github/ansible/playbooks/05_deploy.yml --extra-vars "$DELIVERY_SCOPE_JSON"
~~~
