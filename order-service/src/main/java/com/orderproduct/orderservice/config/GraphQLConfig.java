package com.orderproduct.orderservice.config;

import com.orderproduct.orderservice.dto.SavedOrder;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.stereotype.Component;

@Component
public class GraphQLConfig implements RuntimeWiringConfigurer {

    @Override
    public void configure(RuntimeWiring.Builder builder) {
        builder.scalar(ExtendedScalars.GraphQLBigDecimal);
        builder.scalar(buildAnyScalar());
        builder.type("_Entity", wiring -> wiring.typeResolver(env -> {
            Object object = env.getObject();
            if (object instanceof SavedOrder) {
                return env.getSchema().getObjectType("PlacedOrder");
            }
            return null;
        }));
    }

    // _Any is the federation scalar for entity representations.
    // It accepts any JSON object, so we reuse the Json scalar's coercing logic.
    private GraphQLScalarType buildAnyScalar() {
        return GraphQLScalarType.newScalar()
                .name("_Any")
                .description("Federation _Any scalar — accepts any JSON object (entity representation)")
                .coercing(ExtendedScalars.Json.getCoercing())
                .build();
    }
}
