package olapreadlab.aggregation.adapter.model;

import java.util.Map;

import olapreadlab.aggregation.adapter.jdbc.mapping.AggregateTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.AggregateViewStorageBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.AggregationStorageBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.AggregationStorageBindingProvider;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.SqlIdentifier;

import org.springframework.stereotype.Component;

@Component
class MedicalHistoryStorageBindingProvider implements AggregationStorageBindingProvider {

	@Override
	public AggregationStorageBinding binding() {
		var dimensions = Map.of(
				"personId", id("person_id"),
				"organCode", id("organ_code"),
				"diseaseCode", id("disease_code"));
		var aggregateMeasures = Map.of(
				"eventCount", id("event_count"),
				"metricSum", id("metric_sum"));
		var raw = new RawTableBinding(
				id("olap.medical_history"),
				id("occurred_at"),
				dimensions,
				Map.of("metricSum", id("metric_value")));
		var view = new AggregateViewStorageBinding(
				"person-organ-disease-daily",
				Map.of(
						JdbcStorageKeys.POSTGRES,
						new AggregateTableBinding(
								id("olap.agg_person_organ_disease"), id("bucket_date"),
								dimensions, aggregateMeasures),
						JdbcStorageKeys.CLICKHOUSE,
						new AggregateTableBinding(
								id("olap_clickhouse.agg_person_organ_disease"), id("bucket_date"),
								dimensions, aggregateMeasures)));
		return new AggregationStorageBinding(
				"medical-history", raw, Map.of(view.view(), view));
	}

	private static SqlIdentifier id(String value) {
		return SqlIdentifier.of(value);
	}
}
