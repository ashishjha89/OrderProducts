package com.orderproduct.productservice.config

import com.orderproduct.productservice.dto.ProductResponse
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.stereotype.Component

@Component
class GraphQLConfig : RuntimeWiringConfigurer {
    override fun configure(builder: RuntimeWiring.Builder) {
        builder.scalar(ExtendedScalars.GraphQLBigDecimal)
        builder.scalar(buildAnyScalar())
        builder.type("_Entity") { wiring ->
            wiring.typeResolver { env ->
                when (env.getObject<Any>()) {
                    is ProductResponse -> env.schema.getObjectType("Product")
                    else -> null
                }
            }
        }
    }

    // _Any is the federation scalar for entity representations.
    // It accepts any JSON object, so we reuse the Json scalar's coercing logic.
    private fun buildAnyScalar(): GraphQLScalarType =
        GraphQLScalarType.newScalar()
            .name("_Any")
            .description("Federation _Any scalar — accepts any JSON object (entity representation)")
            .coercing(ExtendedScalars.Json.coercing)
            .build()
}
