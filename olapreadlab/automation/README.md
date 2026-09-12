# OLAP Lab Automation

이 디렉터리는 로컬 성능 비교에 필요한 데이터 준비 절차를 Ansible로 고정한다. 수동 명령의 실행 순서가 바뀌어 ClickHouse MV가 일부 데이터를 놓치거나, 검증 전에 hybrid checkpoint가 전진하는 일을 막는 것이 목적이다.

## 자동화 범위

`prepare.yml`은 다음 순서로 실행한다.

1. Docker CLI, Compose plugin, Docker daemon, Gradle/Java toolchain, 가용 디스크와 입력값을 검사한다.
2. PostgreSQL, Kafka, Debezium Connect, ClickHouse를 기동하고 Compose health check를 기다린다.
3. ClickHouse `leaf` 또는 `dedicated` MV 전략과 Kafka block size를 데이터 적재 전에 적용한다.
4. Jinja 템플릿으로 지정한 행 수의 결정적 raw 데이터를 생성해 PostgreSQL에 chunk 단위로 적재한다.
5. Spring Batch `medicalHistoryRollupJob`으로 PostgreSQL 집계 테이블을 다시 만들고, 성공한 트랜잭션에서 `SPRING_BATCH` checkpoint를 전진시킨다.
6. Debezium connector를 등록하고 ClickHouse leaf 집계 checksum이 PostgreSQL raw와 일치할 때까지 기다린다.
7. checksum 일치 후에만 `CLICKHOUSE` checkpoint를 전진시키고 `build/automation/preparation-result.json`을 생성한다.

준비 과정에서 기록한 시간은 자동화 상태를 파악하기 위한 값이다. PostgreSQL Batch와 ClickHouse를 한 번의 순차 실행으로 준비하므로 이 값을 공정한 성능 비교 결과로 사용하면 안 된다.

현재 `medicalHistoryRollupJob`은 Spring Batch가 트랜잭션, 실행 이력과 checkpoint를 관리하고 실제 `GROUP BY` 계산은 PostgreSQL에 위임하는 tasklet 방식이다. 사전 집계 데이터를 이용한 **조회 경로 비교**에는 충분하지만, 애플리케이션 chunk/partition 집계 자체와 ClickHouse의 **구축 성능**을 비교하려면 별도의 chunk-oriented Batch 구현이 필요하다.

## 실행

Docker Desktop 또는 로컬 Docker daemon을 먼저 실행한다.

```shell
cd olapreadlab/automation
ANSIBLE_CONFIG=ansible.cfg ansible-playbook playbooks/prepare.yml
```

기본값은 빠른 확인용 100만 행, leaf MV, block size 10만이다. 고정된 1억 행 실험은 충분한 디스크를 확보한 뒤 명시적으로 실행한다.
기본 checkpoint는 `2025-10-01T00:00:00Z`이므로 2025년 전체 조회는 9개월 rollup과
3개월 PostgreSQL raw tail을 합친다.

```shell
ANSIBLE_CONFIG=ansible.cfg ansible-playbook playbooks/prepare.yml \
  -e lab_reset_volumes=true \
  -e lab_row_count=100000000 \
  -e lab_seed_chunk_size=1000000 \
  -e lab_min_free_disk_gb=150 \
  -e lab_clickhouse_strategy=leaf \
  -e lab_clickhouse_block_size=100000
```

기존 데이터가 발견되면 `prepare.yml`은 덮어쓰지 않고 중단한다. 완전 초기화는 삭제 의사를 별도 플레이북에 명시해야 한다.

```shell
ANSIBLE_CONFIG=ansible.cfg ansible-playbook playbooks/reset.yml \
  -e lab_reset_volumes=true
```

현재 컨테이너, raw/rollup 행 수, checkpoint와 Debezium 상태는 다음 명령으로 확인한다.

```shell
ANSIBLE_CONFIG=ansible.cfg ansible-playbook playbooks/status.yml
```

## 조회 벤치마크

준비된 데이터로 세 `QueryMode`를 같은 GraphQL 요청 조건에서 측정하려면 다음을 실행한다.

```shell
ANSIBLE_CONFIG=ansible.cfg ansible-playbook playbooks/benchmark.yml
```

빈 환경 구성부터 데이터 준비와 측정까지 한 번에 실행할 수도 있다. 기존 볼륨이 있으면 안전하게
중단하며, 삭제가 필요한 실행에서만 `lab_reset_volumes=true`를 명시한다.

```shell
ANSIBLE_CONFIG=ansible.cfg ansible-playbook playbooks/full-benchmark.yml \
  -e lab_reset_volumes=true
```

runner는 `ONE_DAY`, `ONE_MONTH`, `ONE_YEAR`, `ALL` 범위와 세 조회 모드의 실행 순서를
고정 seed로 섞는다. 각 조합을 기본 3회 warm-up하고 10회 측정하며, HTTP 응답을 메모리에
누적하지 않고 스트리밍으로 SHA-256과 바이트 수를 계산한다. 같은 범위에서 모드별 응답 hash나
행 수가 다르면 playbook이 실패한다.

결과는 `build/benchmark/benchmark-<UTC timestamp>.json`과 `.csv`로 보존하고,
`latest.json`, `latest.csv`도 갱신한다. JSON에는 min, mean, p50, p95, p99, max,
requests/sec, 응답 행 수·바이트와 개별 측정값이 포함된다.

기존에 `2026-01-01T00:00:00Z` checkpoint로 준비한 볼륨은 raw tail이 없다. 실제 hybrid
혼합 성능을 측정하려면 새 기본값으로 명시적 reset 후 `prepare.yml`을 다시 실행해야 한다.

주요 변수는 `vars/lab.yml`에 모여 있다. CLI의 `-e` 값이 기본값보다 우선한다.

## 아직 필요한 후속 실험 자동화

현재 runner는 warm cache의 단일 사용자 GraphQL end-to-end 지연을 측정한다. 전체 실험군에는
아래 자동화가 더 필요하다.

- PostgreSQL set-based 집계가 아니라 Spring Batch 애플리케이션 집계 비용도 비교하려는 경우의 chunk/partition reader-writer 구현
- cold cache 실행과 각 케이스의 독립 볼륨 또는 원천 snapshot 복원
- PostgreSQL/ClickHouse 실행 계획, scan rows/bytes와 Docker CPU, 메모리, disk I/O 수집
- 전역 count/sum보다 강한 bucket·dimension별 정합성 checksum
- backfill 전에 checkpoint를 후퇴시키고, Batch 재집계/CDC 전파/정합성 회복 시간을 각각 재는 보정 플레이북
- 동시 사용자 수 1/10/50/100과 여러 grain을 실제 요청 view 및 storage binding으로 연결하는 시나리오

이 항목이 갖춰져야 `preparation-result.json`이 아니라 재현 가능한 성능 비교 보고서를 만들 수 있다.
