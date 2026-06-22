package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.BAD_REQUEST_MSG
import com.orderproduct.productservice.common.BadRequestException
import com.orderproduct.productservice.common.InternalServerException
import com.orderproduct.productservice.common.SOMETHING_WENT_WRONG_MSG
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component

@Component
class GraphQLExceptionHandler : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? =
        when (ex) {
            is BadRequestException -> GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.ValidationError)
                .message(BAD_REQUEST_MSG)
                .extensions(mapOf("code" to "BAD_USER_INPUT"))
                .build()

            is InternalServerException -> GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.DataFetchingException)
                .message(SOMETHING_WENT_WRONG_MSG)
                .extensions(mapOf("code" to "INTERNAL_SERVER_ERROR"))
                .build()

            else -> null
        }
}
