# Experiment Reporter

`tech-playground`의 각 실험 프로젝트가 생성한 표준 JSON을 블로그용 SVG 그래프로 변환한다.
Python 3.9 이상만 필요하며 외부 패키지를 사용하지 않는다.

```shell
python3 render.py \
  --input ../batchasynclab/build/reports/experiments/experiment-results.json \
  --output ../batchasynclab/build/reports/experiments/charts
```

## 입력 규격

- `schemaVersion`: 현재 `1`
- `lab`, `title`: 실험 묶음 식별 정보
- `results`: `group`, `scenario`, `parameters`, `metrics`를 가진 실행 결과 목록
- `charts`: 출력 디렉터리와 파일, 비교군, 표시할 metric 정의

그래프 정의가 결과 JSON에 포함되므로 reporter는 특정 실험이나 metric 이름에 의존하지 않는다.
전체 입력 계약은 `schema/experiment-report.schema.json`에 JSON Schema로 정의되어 있다.

`type`을 `line`으로 지정하면 숫자 parameter를 x축으로 사용하고 여러 series의 변화 추세를 한 그래프에서
비교할 수 있다. `bar`는 단일 비교, `line`은 동시성·지연시간·요청량 변화 실험에 사용한다.
