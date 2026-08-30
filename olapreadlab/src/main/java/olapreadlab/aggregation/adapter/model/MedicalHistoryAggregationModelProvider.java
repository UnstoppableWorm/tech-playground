package olapreadlab.aggregation.adapter.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import olapreadlab.aggregation.application.port.AggregationModelProvider;
import olapreadlab.aggregation.model.AggregateViewDefinition;
import olapreadlab.aggregation.model.AggregationModelDefinition;
import olapreadlab.aggregation.model.DimensionDefinition;
import olapreadlab.aggregation.model.MeasureDefinition;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;
import olapreadlab.aggregation.model.ScalarType;
import olapreadlab.aggregation.model.TimeBucket;

import org.springframework.stereotype.Component;

@Component
class MedicalHistoryAggregationModelProvider implements AggregationModelProvider {

	@Override
	public AggregationModelDefinition definition() {
		var dimensions = Map.of(
				"personId", new DimensionDefinition("personId", ScalarType.LONG),
				"organCode", new DimensionDefinition("organCode", ScalarType.INTEGER),
				"diseaseCode", new DimensionDefinition("diseaseCode", ScalarType.INTEGER));
		var measures = Map.of(
				"eventCount", new MeasureDefinition("eventCount", RawAggregation.COUNT_ROWS),
				"metricSum", new MeasureDefinition("metricSum", RawAggregation.SUM));
		var leafView = new AggregateViewDefinition(
				"person-organ-disease-daily",
				TimeBucket.DAY,
				List.of("personId", "organCode", "diseaseCode"),
				Set.of("personId", "organCode", "diseaseCode"));
		return new AggregationModelDefinition(
				"medical-history",
				dimensions,
				measures,
				Map.of(leafView.name(), leafView));
	}
}
