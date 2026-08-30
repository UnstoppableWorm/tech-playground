package olapreadlab.aggregation.adapter.jdbc.compiler;

import olapreadlab.aggregation.model.ScalarType;

public record DimensionProjection(String name, ScalarType type, String alias) {
}
