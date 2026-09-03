package olapreadlab.aggregation.adapter.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import olapreadlab.aggregation.model.ViewDefinition;
import olapreadlab.aggregation.model.ModelDefinition;
import olapreadlab.aggregation.model.DimensionDefinition;
import olapreadlab.aggregation.model.MeasureDefinition;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;
import olapreadlab.aggregation.model.ScalarType;
import olapreadlab.aggregation.model.TimeBucket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MedicalHistoryModelConfiguration {

	@Bean
	ModelDefinition medicalHistoryModelDefinition() {
		var dimensions = Map.of(
				"personId", new DimensionDefinition("personId", ScalarType.LONG),
				"organCode", new DimensionDefinition("organCode", ScalarType.INTEGER),
				"diseaseCode", new DimensionDefinition("diseaseCode", ScalarType.INTEGER));
		var measures = Map.of(
				"eventCount", new MeasureDefinition("eventCount", RawAggregation.COUNT_ROWS),
				"metricSum", new MeasureDefinition("metricSum", RawAggregation.SUM));
		var leafView = new ViewDefinition(
				"person-organ-disease-daily",
				TimeBucket.DAY,
				List.of("personId", "organCode", "diseaseCode"),
				Set.of("personId", "organCode", "diseaseCode"));
		return new ModelDefinition(
				"medical-history",
				dimensions,
				measures,
				Map.of(leafView.name(), leafView));
	}
}
