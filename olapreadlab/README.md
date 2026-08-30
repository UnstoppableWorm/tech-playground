# OLAP Read Optimization Lab

OLAP 조회 상황에서 여러 읽기 전략을 동일한 데이터와 쿼리 조건으로 비교하는 실험 프로젝트입니다.

## 고정된 실험 조건

- PostgreSQL 이력성 원천 테이블: 100,000,000행
- 모든 실험군은 동일한 원천 데이터와 동일한 논리 집계 결과를 사용한다.
- 측정 쿼리의 필터, 그룹 키, 시간 범위와 결과 정렬을 동일하게 유지한다.

## 비교군

| 실험군 | 데이터 경로 | 조회 시 수행 작업 |
| --- | --- | --- |
| PostgreSQL raw | PostgreSQL raw → 애플리케이션 | 1억 행을 읽고 애플리케이션에서 집계 |
| PostgreSQL aggregate | PostgreSQL raw → 집계 테이블 | 사전 계산된 집계 테이블을 조회 및 최종 집계 |
| ClickHouse MV | PostgreSQL → Debezium → Kafka → ClickHouse → Materialized View | Materialized View 결과 조회 |

세 경로는 `olapreadlab.experiment.ReadPath`로 식별한다.

## 단일 조회 API

세 조회 방식은 Spring GraphQL의 단일 HTTP 엔드포인트로 제공한다.

```http
POST /graphql
Content-Type: application/json
```

```graphql
query Aggregate($input: AggregationQueryInput!) {
  aggregate(input: $input) {
    mode
    aggregateCoveredUntil
    rows {
      bucket
      dimensions { name value }
      measures { name value }
    }
  }
}
```

variables:

```json
{
  "input": {
    "model": "medical-history",
    "view": "person-organ-disease-daily",
    "mode": "POSTGRES_BATCH_HYBRID",
    "fromInclusive": "2026-08-01T00:00:00Z",
    "toExclusive": "2026-09-01T00:00:00Z",
    "where": {
      "operator": "AND",
      "children": [
        { "operator": "IN", "field": "personId", "values": ["1", "2"] },
        {
          "operator": "OR",
          "children": [
            { "operator": "EQ", "field": "organCode", "value": "10" },
            { "operator": "BETWEEN", "field": "diseaseCode", "lower": "101", "upper": "199" }
          ]
        }
      ]
    },
    "having": { "operator": "GTE", "field": "eventCount", "value": "10" }
  }
}
```

`model`은 비즈니스 데이터 모델, `view`는 허용된 집계 형태를 선택한다. `where`는 `AND`/`OR` 중첩과 `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `BETWEEN`, `LIKE`, `PREFIX`, `IS_NULL`, `IS_NOT_NULL`을 지원한다. 값은 등록된 `ScalarType`으로 변환된다. 기존 `filters` 입력도 호환되며 각 항목을 `AND`로 연결한 `IN` 조건으로 해석한다.

`having`은 같은 조건 트리를 사용하지만 measure만 참조할 수 있다. 하이브리드 경로에서는 조각별 SQL에 먼저 적용하지 않고 aggregate/raw 결과를 합친 뒤 적용해 의미가 달라지는 것을 방지한다. 결과 행 수 수준에서 평가하므로 1억 raw 행 전체에 대한 애플리케이션 필터는 아니다.

`CUSTOM`은 도메인별 검색조건 이름과 인자를 받고 `CustomFilterResolver` 구현체가 공통 조건 트리로 확장한다. 의료 모델에는 `{ operator: CUSTOM, name: "organDiseasePair", arguments: [{ name: "organCode", values: ["10"] }, { name: "diseaseCode", values: ["101"] }] }` 예시가 등록돼 있다. 커스텀 구현도 SQL이나 물리 컬럼명을 반환하지 않으므로 저장소별 compiler와 바인딩 검증을 우회하지 못한다.

등록되지 않은 모델·view·필터 또는 요청에서 전달한 테이블명과 컬럼명은 거부한다. 클라이언트는 필요한 응답 필드만 선택할 수 있지만 물리 저장소나 테이블은 선택할 수 없다. `mode`를 기존 애플리케이션 서비스가 조회 계획으로 변환한다.

로컬 GraphiQL은 `/graphiql`, schema 출력은 `/graphql/schema`에서 확인할 수 있다.

| `mode` | 실제 조회 경로 |
| --- | --- |
| `POSTGRES_RAW` | 조건에 맞는 PostgreSQL raw 행을 스트리밍하고 애플리케이션에서 일별 집계 |
| `POSTGRES_BATCH_HYBRID` | Spring Batch 완료 구간은 PostgreSQL 집계 테이블, 이후 구간은 PostgreSQL raw |
| `CLICKHOUSE_HYBRID` | CDC 반영 완료 구간은 ClickHouse MV 집계, 이후 구간은 PostgreSQL raw |

응답은 `bucket`, `dimensions`, `measures`라는 범용 형태다. 현재 등록된 medical view는 `(UTC 일자, 사람, 장기, 질병)`과 `eventCount`, `metricSum`을 정의한다. 시간 범위는 선택한 view의 bucket 경계와 일치해야 한다.

지원하는 UTC 시간 bucket은 `HOUR`, `DAY`, `MONTH`, `YEAR`다. 응답의 `bucket`은 모든 단위를 손실 없이 표현하기 위해 UTC `Instant` 형식으로 반환한다. 물리 집계 테이블은 `HOUR` view의 bucket 컬럼을 timestamp/DateTime 계열로, `DAY`, `MONTH`, `YEAR` view는 Date 계열로 정의하는 컨벤션을 사용한다.

혼합 조회는 PostgreSQL의 `olap.aggregation_checkpoint.covered_until`을 exclusive 경계로 사용한다. 경계 이전은 집계 저장소에서, 경계 이후는 raw에서만 읽으므로 중복 합산하지 않는다. 체크포인트가 없으면 정합성을 우선하여 전체 범위를 PostgreSQL raw로 조회한다.

배치 또는 CDC 검증기가 집계 완료를 확인한 뒤 체크포인트를 다음처럼 갱신한다.

```sql
INSERT INTO olap.aggregation_checkpoint (model_key, view_key, pipeline, covered_until)
VALUES (
  'medical-history',
  'person-organ-disease-daily',
  'SPRING_BATCH',
  TIMESTAMPTZ '2026-08-29 00:00:00+00'
)
ON CONFLICT (model_key, view_key, pipeline) DO UPDATE
SET covered_until = EXCLUDED.covered_until,
    completed_at = clock_timestamp();
```

ClickHouse 경로는 `pipeline='CLICKHOUSE'`를 사용하며, 단순 Kafka consume 시점이 아니라 해당 경계 이전 데이터의 checksum 일치까지 확인한 뒤 전진시킨다. 과거 구간을 보정할 때는 PostgreSQL 원천을 수정하기 **전에** 체크포인트를 영향받는 가장 이른 UTC 일자까지 후퇴시킨다. 그러면 보정 전파 중인 구간은 raw로 조회되고, checksum이 다시 일치한 뒤에만 체크포인트를 전진시킬 수 있다. Spring Batch 재집계도 같은 규칙을 사용한다.

## 범용 집계 모델과 헥사고날 경계

공통 애플리케이션 코어는 사람·장기·질병 또는 실제 테이블명을 알지 못한다. 코어가 담당하는 일은 모델 조회, 입력 검증, 체크포인트를 기준으로 한 aggregate/raw 범위 분할, 결과 병합뿐이다.

각 비즈니스의 `AggregationModelProvider` 구현체는 저장소와 무관한 논리 정의만 등록한다.

- 논리적인 model/view 이름
- 차원의 API 이름과 값 타입
- COUNT/SUM처럼 다시 합칠 수 있는 additive measure의 의미
- view가 보존하는 차원과 허용 검색 조건
- 시간 bucket

별도의 `AggregationStorageBindingProvider`가 JDBC 인프라 매핑을 등록한다.

- PostgreSQL raw 테이블, event-time 컬럼과 차원/measure 컬럼
- view별 PostgreSQL 집계 테이블과 컬럼
- view별 ClickHouse 집계 테이블과 컬럼

`AggregateViewStorageBinding`은 `postgres`, `clickHouse` 같은 고정 필드를 갖지 않고 `StorageBindingKey → AggregateTableBinding` 맵만 가진다. 실제 키는 JDBC 인프라가 소유하므로 다른 warehouse를 추가해도 binding 타입을 수정하지 않는다.

`AggregationModelDefinition`, `AggregateViewDefinition`, `DimensionDefinition`, `MeasureDefinition`에는 SQL 식별자나 PostgreSQL/ClickHouse 정보가 없다. JDBC outbound adapter만 `AggregationStorageBindingCatalog`를 통해 물리 매핑을 조회한다. 모든 SQL 식별자는 binding 등록 시 검증되며 요청값을 테이블명이나 컬럼명으로 사용하지 않는다.

SQL 문자열 생성도 실행 어댑터와 분리되어 있다.

- `PostgresRawQueryCompiler`: PostgreSQL raw SELECT와 중첩 predicate SQL 생성
- `PostgresAggregateQueryCompiler`: PostgreSQL 집계 SELECT/GROUP BY 생성
- `ClickHouseAggregateQueryCompiler`: ClickHouse 집계 SELECT/GROUP BY 생성
- `RawQueryCompilerRegistry` / `AggregateQueryCompilerRegistry`: storage key, `supports(model/view)`, priority로 compiler 선택
- `CompiledQuery`: SQL, 바인딩 파라미터, 결과 projection을 함께 전달
- `JdbcAggregationQueryExecutor`: compiled query 실행과 projection 기반 결과 매핑만 담당

논리 조건은 저장소 독립적인 `FilterExpression` AST다. WHERE 조건은 각 compiler가 물리 컬럼 binding과 named parameter로 변환한다. 도메인 확장은 `CustomFilterResolver`에서 표준 AST로 낮춘 뒤 같은 검증과 compiler를 통과한다. HAVING은 `AggregationHavingEvaluator`가 최종 병합 결과에 적용한다.

기본 compiler는 선언형 model/storage binding으로 SQL을 생성한다. 저장소 전용 함수나 특수 view가 필요하면 논리 모델에 SQL 문자열을 넣지 않고 해당 저장소의 compiler 구현을 추가한다. 전용 compiler는 `supports`로 model/view를 제한하고 기본값보다 높은 `priority`를 선언하면 registry가 우선 선택한다.

현재 의료 예시는 논리 정의인 `MedicalHistoryAggregationModelProvider`와 물리 매핑인 `MedicalHistoryStorageBindingProvider`로 분리돼 있다. 새 비즈니스를 추가할 때 이 두 provider와 실제 테이블/MV를 추가하며 공통 서비스나 컨트롤러는 수정하지 않는다.

```text
HTTP request
    -> AggregationQueryService (공통 유스케이스)
        -> AggregationModelCatalog (논리 모델만 조회)
        1. AggregationReadPlanner
           -> Checkpoint port로 aggregate/raw 조회 경계 결정
        2. PostgresRawAggregationQueryAdapter
           -> AggregationStorageBindingCatalog에서 PG raw 매핑 조회
           -> PostgresRawQueryCompiler로 SQL compile
           -> 경계 이후 PostgreSQL raw 조회 및 집계
        3. AggregateStoreQueryPortRegistry
           -> POSTGRES_BATCH_HYBRID: PostgresAggregateQueryAdapter
           -> CLICKHOUSE_HYBRID: ClickHouseAggregateQueryAdapter
           -> 각 어댑터가 자신의 storage binding 조회
           -> 저장소별 AggregateQueryCompiler로 SQL compile
           -> JdbcAggregationQueryExecutor로 실행
        4. 두 결과 병합 및 반환
```

`PostgresAggregationCheckpointAdapter`는 완료 경계를 저장한 PostgreSQL 제어 테이블만 읽으며 집계 데이터를 조회하지 않는다. 세 데이터 조회 어댑터는 물리 저장소와 역할별로 분리되어 있다.

## 집계 계층과 전략

원천 fact는 한 사람에게 같은 장기/질병 이벤트가 시간에 따라 여러 번 생기는 이력 데이터로 가정한다. 가장 세밀한 사전 집계 grain은 `(사람, 장기, 질병)`이며 여기서 차원을 제거해 다음 상위 집계를 만들 수 있다.

- 사람 + 장기 + 질병
- 사람 + 장기 / 사람 + 질병 / 장기 + 질병
- 사람 / 장기 / 질병
- 전체

예를 들어 `(사람, 장기, 질병)` 결과는 `(장기, 질병)`로 다시 집계할 수 있지만, 이미 사람 차원을 버린 `(장기, 질병)` 결과로 사람별 집계를 복원할 수는 없다. 범용 구조에서는 각 view의 `dimensions`가 보존된 차원을 명시한다.

각 grain에 대해 다음 다섯 전략을 비교한다.

| 전략 | 준비 방식 | 조회 방식 | 예상 trade-off |
| --- | --- | --- | --- |
| Raw application | 없음 | PostgreSQL 1억 행을 읽어 애플리케이션 집계 | 준비 비용 최소, 조회 비용 최대 |
| Batch leaf rollup | Spring Batch가 최하위 grain 하나 생성 | 최하위 테이블을 상위 grain으로 재집계 | 저장/보정 대상은 적지만 상위 조회에 추가 계산 필요 |
| Batch dedicated rollup | Spring Batch가 질의 grain별 테이블 생성 | 전용 집계 테이블 조회 | 조회는 빠르지만 grain 수만큼 배치와 보정 비용 증가 |
| ClickHouse leaf MV | 최하위 grain Materialized View 하나 유지 | ClickHouse에서 상위 grain으로 재집계 | 다양한 질의 대응과 유지 비용의 절충 |
| ClickHouse dedicated MV | 질의 grain별 Materialized View 유지 | 전용 View 조회 | 조회는 빠르지만 View 수, 저장 공간과 보정 복잡도 증가 |

실험 케이스는 등록된 model/view와 조회 `mode`의 조합으로 만든다. 도메인별 grain을 공통 enum에 추가하지 않는다.

### 질의 다양성 실험

질의 grain 수를 `1 → 3 → 5 → 8`로 늘리며 아래 항목의 증가율을 측정한다.

- 최초 전체 구축 시간
- 신규 이력의 증분 반영 시간과 최대 처리량
- 과거 데이터 보정 완료 시간
- 조회 지연과 동시 조회 처리량
- 집계 테이블/View 총 저장 공간
- 운영 객체 수와 실패 후 재처리 범위

이 실험을 통해 “전용 집계를 몇 개부터 유지할 때 Spring Batch 재계산보다 ClickHouse 증분 집계가 유리해지는지” 교차점을 찾는다.

> ClickHouse Materialized View는 입력된 블록을 증분 처리하지만, PostgreSQL의 과거 `UPDATE`/`DELETE` 의미를 기존 집계에서 자동으로 되돌려 주는 만능 장치는 아니다. 버전 행, 부호(sign) 행, 재삽입 또는 파티션 재구축 방식에 따라 보정 비용과 정합성이 달라지므로 각각 별도 구현으로 측정한다.

## 측정 항목

### 조회

- end-to-end 응답 시간(p50, p95, p99)
- DB 실행 시간과 애플리케이션 처리 시간
- 반환 및 스캔 행 수, 전송 바이트
- PostgreSQL/ClickHouse CPU, 메모리, 디스크 읽기
- 결과 정확성: 기준 결과 checksum과 행 수 비교

첫 번째 실험군은 의도적으로 1억 행을 애플리케이션까지 전송하므로, DB 실행 시간만 비교하지 않고 전송과 애플리케이션 집계 시간이 포함된 end-to-end 시간을 주 지표로 삼는다.

### 데이터 보정

과거 행에 대한 `UPDATE`, `DELETE`, `BACKFILL`을 같은 대상 행 집합에 적용한다.

- 보정 요청부터 조회 결과 반영까지 걸린 시간
- 보정 중 추가 CPU, 메모리, 디스크 I/O와 네트워크 사용량
- PostgreSQL 집계 테이블 재계산 범위와 잠금 시간
- Debezium/Kafka consumer lag 및 ClickHouse 반영 지연
- 재처리한 행 수와 추가 저장 공간
- 보정 중 조회 지연 변화와 결과가 불일치한 시간 구간

비동기 경로의 보정 완료 시점은 단순 Kafka consume 시점이 아니라, 동일한 검증 쿼리의 checksum이 기준 결과와 일치한 시점으로 정의한다.

ClickHouse 보정 방식도 다음 세 가지로 나누어 비교한다.

1. 역집계용 sign 행과 수정 행을 함께 발행
2. 버전 행을 적재한 뒤 조회 시 최신 버전을 선택
3. 영향받은 시간 파티션을 원천에서 다시 구축

## 공정한 비교를 위한 실행 규칙

1. PostgreSQL 원천 스냅샷과 보정 대상 PK 목록을 실행마다 재사용한다.
2. 각 전략은 완전히 동일한 논리 결과를 내야 하며 측정 전후 checksum으로 검증한다.
3. cold cache와 warm cache 결과를 분리하고 실행 순서를 무작위화한다.
4. 준비 비용은 조회 시간에서 제외하되 별도 기록한다.
5. 동시 사용자 수, DB 자원 제한과 네트워크 위치를 고정한다.
6. warm-up 3회 후 10회를 측정하며 기본값은 `application.properties`에서 변경할 수 있다.

## 비교 원칙

- 모든 전략은 같은 입력 데이터와 같은 결과를 사용한다.
- 준비 시간과 실제 조회 시간을 분리한다.
- warm-up 결과와 측정 결과를 분리한다.
- 응답 시간뿐 아니라 처리량, 읽은 행 수, 메모리와 DB 실행 계획을 함께 기록한다.
- 한 번에 하나의 변수만 바꾼다.

## 구현 순서

1. 원천 이력 테이블 스키마와 재현 가능한 1억 행 생성기
2. 정답으로 사용할 집계 쿼리와 checksum 검증기
3. PostgreSQL raw/집계 테이블 조회 구현
4. Debezium, Kafka, ClickHouse 파이프라인과 Materialized View
5. 조회 측정 러너 및 자원 메트릭 수집
6. 동일 보정 명령을 세 경로에 적용하는 보정 측정 러너

## 첫 번째 실험: 전체 구축과 10% 보정

### 최초 집계

| 케이스 | 입력 행 | 전달/block 크기 | block 수 | 종료 시점 |
| --- | ---: | ---: | ---: | --- |
| Spring Batch 일 배치 | 100,000,000 | 전체 재집계 | 1 | PostgreSQL 최하위 집계 checksum 일치 |
| ClickHouse MV | 100,000,000 | 1,000,000 | 100 | ClickHouse 최하위 집계 checksum 일치 |
| ClickHouse MV | 100,000,000 | 100,000 | 1,000 | ClickHouse 최하위 집계 checksum 일치 |
| ClickHouse MV | 100,000,000 | 10,000 | 10,000 | ClickHouse 최하위 집계 checksum 일치 |

### 10% backfill 후 재집계

보정 대상은 항상 `event_id=1..10,000,000`으로 고정하고 `metric_value += 100`을 적용한다. 각 케이스는 동일한 최초 스냅샷으로 되돌린 뒤 독립 실행한다.

| 케이스 | 실제 수정 행 | 처리 단위 | transaction/block 수 | 종료 시점 |
| --- | ---: | ---: | ---: | --- |
| Spring Batch 일 배치 | 10,000,000 | 전체 1억 건 재집계 | 1회 job | PostgreSQL 최하위 집계 checksum 일치 |
| ClickHouse MV | 10,000,000 | 1,000,000 | 10 | ClickHouse checksum 일치 |
| ClickHouse MV | 10,000,000 | 100,000 | 100 | ClickHouse checksum 일치 |
| ClickHouse MV | 10,000,000 | 10,000 | 1,000 | ClickHouse checksum 일치 |

ClickHouse 케이스의 처리 단위는 두 설정을 함께 바꾼다.

1. PostgreSQL backfill transaction당 수정 행 수
2. ClickHouse Kafka Engine의 `kafka_max_block_size`

따라서 이 실험은 운영상 하나의 end-to-end 튜닝 조합을 비교한다. block 크기 자체의 영향만 분리하려면 후속 실험에서 PostgreSQL transaction 크기를 고정하고 `kafka_max_block_size`만 변경한다.

ClickHouse Kafka Engine block 크기는 `infra/clickhouse/scenarios/block-size-*.sql`로 선택한다. 세 파일 모두 다음 조건을 함께 고정한다.

- `kafka_max_block_size`: MV에 전달할 최대 Kafka 메시지 수
- `kafka_poll_max_batch_size`: 한 번의 Kafka poll 최대 메시지 수
- `kafka_flush_interval_ms=10000`: block이 덜 찼을 때 flush하는 시간
- `kafka_num_consumers=1`: 병렬 consumer 수

설정 파일은 Kafka에 데이터가 들어오기 전에 적용해야 한다. flush timeout과 실제 메시지 가용량 때문에 만들어진 part가 항상 설정값과 정확히 일치하지는 않으므로 결과에는 설정값과 실제 block/part 수를 모두 기록한다.

### 파편화 상태 조회

각 ClickHouse block 크기는 두 가지 물리 상태로 측정한다.

1. 정상 운영 상태: background merge 활성
2. 의도적 파편화 상태: `agg_person_organ_disease`의 background merge만 중지

파편화 실험 순서는 다음과 같다.

1. 빈 ClickHouse 볼륨에서 `leaf-only.sql` 적용
2. 선택한 `block-size-*.sql` 적용
3. `fragmented-leaf-start.sql` 적용
4. Debezium snapshot 시작 및 checksum 일치 대기
5. `medical_history_changes`의 실행 중 merge가 없어질 때까지 대기
6. `parts-snapshot.sql` 결과 저장
7. leaf roll-up 조회 반복 측정
8. `fragmented-leaf-finish.sql`로 merge 재개 및 임시 part 제한 복구

집계 테이블은 `SummingMergeTree`이므로 merge가 중지돼도 조회는 반드시 `sum(event_count)`, `sum(metric_sum)`으로 다시 합산한다. `FINAL`이나 `OPTIMIZE`는 파편화 조회에 사용하지 않는다.

merge를 멈춘 상태에서는 작은 insert마다 immutable part가 계속 쌓인다. 특히 1만 block 케이스는 월 파티션마다 약 1만 개, 전체 약 12만 개의 leaf part가 생길 수 있다. 이는 의도적인 worst-case 실험이며 운영 권장 설정이 아니다. 임시로 높인 `parts_to_delay_insert`, `parts_to_throw_insert`, `max_parts_in_total`과 실제 peak memory 및 part 수를 반드시 결과에 남긴다.

### 측정 구간

- 최초 집계 시작: Spring Batch job 시작 또는 Debezium snapshot 시작
- backfill 시작: 첫 PostgreSQL 보정 transaction 시작
- 종료: Kafka lag가 0이고 대상 집계의 행 수·event count·metric sum checksum이 기준 결과와 일치
- 함께 기록: 총시간, 처리량, PostgreSQL WAL 증가량, Kafka bytes/lag, ClickHouse parts/merge 시간, 각 컨테이너 CPU·메모리·disk I/O

고정 실험 행렬은 `FirstExperimentPlan`에 정의되어 있으며, 데이터 생성과 보정 SQL은 `infra/postgres/experiment`에 있다.

## 전체 벤치마크 스위트

첫 실험 이후에는 변수를 한 번에 하나씩 바꾼다.

| 실험 | 독립 변수 | 고정 조건 | 핵심 결과 |
| --- | --- | --- | --- |
| 구축 | Spring Batch, PG native GROUP BY, ClickHouse block 크기 | 1억 건/leaf grain | checksum 일치까지 총시간 |
| 조회 | 1일/1개월/1년/전체, cold/warm | 단일 사용자 | p50/p95/p99, scan rows/bytes |
| 동시 조회 | 1/10/50/100 사용자 | warm cache/동일 query mix | 처리량, tail latency |
| 집계 다양성 | 활성 grain 1/3/5/8개 | 동일 입력률 | 구축·보정·저장 비용 증가율 |
| backfill 크기 | 0.1%/1%/10% | 동일 분포 | 정합성 회복 시간 |
| backfill 분포 | 최근 연속/단일 월/전체 분산/차원 hotspot | 동일 수정 행 수 | 재계산·merge 범위 |
| freshness | Batch 1일/1시간/10분, CDC | 동일 자원 제한 | commit-to-query-visible 지연 |
| 복구 | Batch 중단, Kafka backlog, ClickHouse 중단 | 동일 장애 시간 | 정상 지연 복귀 시간 |

`PostgreSQL raw → 애플리케이션 집계`만으로 ClickHouse와 비교하지 않는다. PostgreSQL 내부 `GROUP BY`를 별도 기준선으로 측정하고, Spring Batch와 ClickHouse 아키텍처의 추가 비용을 각각 비교한다.

backfill은 크기와 분포를 분리한다. 같은 1천만 건이라도 한 파티션에 집중된 보정과 전체 이력에 흩어진 보정은 전혀 다른 merge/rebuild 비용을 만들 수 있다. 재현 가능한 네 가지 SQL은 `infra/postgres/experiment/backfill-*.sql`에 있다.

`olapreadlab.experiment.ReadStrategy`가 각 조회 구현의 공통 경계입니다.

## 로컬 실험 인프라

Docker Compose가 다음 서비스를 동일한 버전과 설정으로 관리한다.

- PostgreSQL 17.11: 공통 1억 행 원천과 Spring Batch 집계 테이블
- Kafka/Debezium 3.6: PostgreSQL CDC 전달
- Debezium Connect 3.6: PostgreSQL source connector 실행
- ClickHouse 26.3: CDC 원본, 집계 테이블과 Materialized View

PostgreSQL은 Debezium이 logical replication slot을 사용할 수 있도록 `wal_level=logical`로 시작한다. Kafka는 로컬 실험용 단일 KRaft combined 노드이며 운영 구성의 성능이나 가용성을 대표하지 않는다.

```shell
docker compose up -d
docker compose ps
```

애플리케이션에서 Compose의 PostgreSQL을 사용할 때는 `docker` 프로필을 활성화한다.

```shell
SPRING_PROFILES_ACTIVE=docker ./gradlew bootRun
```

컨테이너만 중지하고 데이터 볼륨은 유지하려면 다음 명령을 사용한다.

```shell
docker compose down
```

`docker compose down -v`는 PostgreSQL 원천 1억 행을 포함한 모든 실험 데이터를 삭제하므로, 완전 초기화가 필요할 때만 사용한다.

### 초기화되는 데이터 구조

- PostgreSQL `olap.medical_history`: 공통 이력 fact 원천
- PostgreSQL `olap.agg_person_organ_disease`: Spring Batch 최하위 집계 대상
- Debezium connector `olap-postgres`: 원천 테이블 snapshot 및 CDC
- Kafka topic `olap.olap.medical_history`: `<topic.prefix>.<schema>.<table>` 규칙
- ClickHouse `medical_history_changes`: insert/snapshot `+1`, delete `-1`, update `-1/+1` signed 변경 로그
- ClickHouse `agg_*`: 8개 grain의 `SummingMergeTree` 집계 테이블
- ClickHouse `mv_*`: signed 변경 로그를 각 grain으로 증분 집계하는 Materialized View

ClickHouse DDL은 `infra/clickhouse/init`에, 비교 및 정합성 쿼리는 `infra/clickhouse/queries`에 있다. 초기화 SQL은 빈 볼륨을 처음 만들 때만 실행된다. 이미 볼륨이 생성된 뒤 DDL을 변경했다면 SQL을 직접 적용하거나, 데이터 삭제가 허용되는 경우에만 `docker compose down -v` 후 다시 시작한다.

`SummingMergeTree`의 동일 key 행은 background merge 전까지 여러 개 존재할 수 있다. 따라서 실험 조회도 `FINAL`에 의존하지 않고 `sum(event_count)`, `sum(metric_sum)`으로 최종 합산한다.

쓰기 및 보정 비용을 측정할 때 모든 MV를 동시에 켜 두면 최하위 MV 전략이 불리해진다. 각 실행은 빈 ClickHouse 볼륨에서 시작하고 데이터 적재 전에 다음 중 하나만 적용한다.

- leaf 전략: `infra/clickhouse/scenarios/leaf-only.sql`로 전용 MV 7개 제거
- dedicated 전략: 기본 8개 MV를 유지하고 `infra/clickhouse/scenarios/dedicated-only.sql`로 활성 개수 검증

조회 전용 비교에서는 동일 입력이 완전히 반영된 뒤 leaf roll-up 쿼리와 dedicated roll-up 쿼리를 각각 측정한다.
