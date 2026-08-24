# Spring Batch thread efficiency experiments

Spring Batch 작업의 I/O 유무와 실행 방식에 따른 시간과 동시성을 비교한다.

## 실험 1: 일반 배치 vs 가상 스레드

모든 시나리오는 I/O가 없는 순차 Reader와 No-op Writer를 사용한다. Processor만 반복 정수 연산을 수행하는
`CPU_HEAVY`와 항목마다 10ms를 기다리는 `SIMULATED_IO`로 나눈다.

- CPU-heavy Processor: 일반 실행, 가상 스레드 동시성 4/40/400
- I/O-heavy Processor: 일반 실행, 가상 스레드 동시성 4/40/400

두 비교군 모두 400개 항목과 Chunk 크기 400을 사용한다. 첫 비교군은 CPU 연산에서 가상 스레드 동시성을
높였을 때의 한계를, 두 번째 비교군은 블로킹 I/O에서 동시성 증가에 따른 처리 시간 변화를 보여준다.

각 항목은 JUnit 동적 테스트로 표시되므로 IDE에서 시나리오별 성공 여부를 확인할 수 있다.

## 실험 2: 플랫폼 스레드 vs 가상 스레드

CPU-heavy와 I/O-heavy Processor 각각에 대해 플랫폼 스레드와 가상 스레드를 동시성 4/40/400에서 비교한다.
실행 시간, 처리량, process CPU time을 기록한다.

메모리는 이 실험의 결과에서 제외한다. JVM heap만 측정하면 플랫폼 스레드의 native stack이 빠지고,
같은 JVM에서 시나리오를 연속 실행하면 GC 시점과 이전 실험의 영향도 섞이기 때문이다. 메모리 비교는 별도 실험에서
조건마다 새 JVM을 실행하고 Native Memory Tracking(NMT)의 `Thread` 및 `Total committed`를 수집해야 한다.

## 측정 방법

각 조건은 측정 전에 1회 워밍업하고, 이후 6회 측정한다. 한 라운드는 시나리오를 정방향으로 실행하고 다음
라운드는 역방향으로 실행하는 방식을 반복하여 특정 조건이 항상 먼저 또는 나중에 실행되는 편향을 줄인다.
리포트에는 6개 표본의 산술평균이 아닌 중앙값을 기록한다. 표본 수가 짝수이므로 정렬 후 가운데 두 값의 평균을
중앙값으로 사용한다.

## 실험 3: 일반 Step vs 파티셔닝

첫 비교는 Reader와 Processor에 비용이 없고 Writer만 사용하는 상황이다. Writer는 항목 수와 관계없이 요청당
1초가 걸리는 bulk insert와 단일 쓰기 채널을 모사한다. 일반 Step은 400개를 한 번에 기록하고, 파티션 4는
100개씩 네 번, 파티션 40은 10개씩 40번 요청한다. 요청이 직렬화되므로 파티셔닝이 bulk 효율을 떨어뜨리는
극단적인 조건을 보여준다.

두 번째 비교는 200개 항목이 각각 1ms I/O 후 실패하는 Processor를 사용한다. 실패 확률은 `0.01%`, `0.1%`,
`1%`로 나누어 일반 Step과 파티셔닝을 비교한다. 실패할 때마다 새로운 Job을
만드는 것이 아니라 동일한 식별 파라미터의 JobInstance를 완료될 때까지 재시작한다. 일반 Step과 파티션 worker
모두 커밋된 Chunk의 체크포인트에서 이어지며, 결과의 `elapsedMs`는 최초 실행부터 최종 완료까지의 누적 시간이고
`jobExecutions`는 필요한 실행 횟수다. 난수 시드는 같은 측정 라운드에서 동일하게 초기화해 비교를 재현할 수 있게 한다.
실패 확률 10%는 다수 항목이 한 실행에서 모두 성공할 확률이 사실상 0에 가까워 제외한다. 1%에서도 400개를
한 번에 완료할 확률은 약 1.8%뿐이므로, 세 실패율을 같은 작업량으로 비교할 수 있는 200개로 조정한다.

## Step 조립 방식

Step 실행 방식은 하나의 배타적인 타입이 아니라 서로 독립적인 두 축으로 정의한다.

- `ProcessingMode`: Processor를 순차 실행할지 concurrent 실행할지 결정한다.
- `PartitionMode`: worker Step 하나를 그대로 실행할지 여러 파티션으로 감쌀지 결정한다.
- `ThreadMode`: executor가 필요한 경우 플랫폼 스레드와 가상 스레드 중 무엇을 사용할지 결정한다.

먼저 공통 Reader/Processor/Writer로 worker Step을 만들고, concurrent 모드라면 Processor executor를
추가한다. 이후 partitioned 모드라면 완성된 worker Step을 manager Step으로 감싼다. 따라서 순차/동시 실행과
단일/파티션 실행, executor 스레드 종류를 독립적으로 조합할 수 있다.

## 테스트별 실행 환경

`application.properties`는 기본값만 제공한다. 테스트마다 `ExperimentSettings`를 만들어 독립적인 실행 환경을
`ExperimentJobFactory`에 전달할 수 있다.

```java
ExperimentSettings concurrency40 =
		new ExperimentSettings(400, 400, 1, 40, Duration.ofMillis(10));

run(IO_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, concurrency40);
```

## 실행

`BatchThreadEfficiencyExperimentTests`에서 원하는 테스트 메서드를 IDE로 직접 실행한다.
전체 실험군을 한 번에 실행할 수도 있다.

```shell
./gradlew experimentTest
```

IDE에서 각 테스트 메서드를 직접 실행할 수 있고, `./gradlew test`는 일반 테스트와 실험 테스트를 모두 실행한다.
결과에는 전체 소요 시간(`elapsedMs`)과 동시에 진행된 I/O의 최댓값(`peakConcurrentIo`)이 출력된다.

전체 실험을 실행하고 텍스트 리포트를 바로 확인하려면 다음 스크립트를 사용한다.

```shell
./scripts/run-experiment-report.sh
```

생성된 리포트는 다음 경로에 저장된다.

```text
build/reports/experiments/experiment-results.txt
build/reports/experiments/experiment-results.json
docs/images/experiment-1/*.svg
docs/images/experiment-2/*.svg
docs/images/experiment-3/*.svg
```

JSON은 모든 lab이 공유하는 실험 결과 규격이며, 상위 `experiment-reporter` Python 프로그램이 JSON의
`charts` 정의에 따라 Git으로 관리되는 `docs/images` 아래에 블로그용 SVG를 생성한다.
