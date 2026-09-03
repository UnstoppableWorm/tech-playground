package olapreadlab.aggregation.adapter.model;

import java.util.Map;

import olapreadlab.aggregation.adapter.jdbc.mapping.RollupTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.RollupViewBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.ModelStorageBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.ModelStorageBindingProvider;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.SqlIdentifier;

import org.springframework.stereotype.Component;

@Component
class MedicalHistoryStorageBindingProvider implements ModelStorageBindingProvider {

	@Override
	public ModelStorageBinding binding() {
		var dimensions = Map.of(
				"personId", id("person_id"),
				"organCode", id("organ_code"),
				"diseaseCode", id("disease_code"));
		var rollupMeasures = Map.of(
				"eventCount", id("event_count"),
				"metricSum", id("metric_sum"));
		var raw = new RawTableBinding(
				id("olap.medical_history"),
				id("occurred_at"),
				dimensions,
				Map.of("metricSum", id("metric_value")));
		var view = new RollupViewBinding(
				"person-organ-disease-daily",
				Map.of(
						JdbcStorageKeys.POSTGRES,
						new RollupTableBinding(
								id("olap.agg_person_organ_disease"), id("bucket_date"),
								dimensions, rollupMeasures),
						JdbcStorageKeys.CLICKHOUSE,
						new RollupTableBinding(
								id("olap_clickhouse.agg_person_organ_disease"), id("bucket_date"),
								dimensions, rollupMeasures)));
		return new ModelStorageBinding(
				"medical-history", raw, Map.of(view.view(), view));
	}

	private static SqlIdentifier id(String value) {
		return SqlIdentifier.of(value);
	}
}
