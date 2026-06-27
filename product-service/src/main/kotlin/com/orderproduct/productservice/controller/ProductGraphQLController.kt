package com.orderproduct.productservice.controller

import com.orderproduct.productservice.common.BadRequestException
import com.orderproduct.productservice.dto.CreateProductInput
import com.orderproduct.productservice.dto.FederationServiceSdl
import com.orderproduct.productservice.dto.ProductResponse
import com.orderproduct.productservice.dto.SavedProduct
import com.orderproduct.productservice.service.ProductService
import graphql.schema.DataFetchingEnvironment
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class ProductGraphQLController(private val productService: ProductService) {

    private val log = LoggerFactory.getLogger(ProductGraphQLController::class.java)

    @QueryMapping
    suspend fun products(): List<ProductResponse> {
        log.info("GraphQL query: products")
        return productService.getAllProducts()
    }

    @MutationMapping
    suspend fun createProduct(@Argument input: CreateProductInput): SavedProduct {
        log.info("GraphQL mutation: createProduct")
        val name = input.name.takeIf { it.isNotBlank() } ?: throw BadRequestException()
        val description = input.description.takeIf { it.isNotBlank() } ?: throw BadRequestException()
        return productService.createProduct(name, description, input.price, input.skuCode)
    }

    // Federation: the router calls _service { sdl } at startup to discover this subgraph's schema.
    @QueryMapping(name = "_service")
    fun service(): FederationServiceSdl = FederationServiceSdl(SUBGRAPH_SDL)

    // Federation: the router calls _entities with a list of representations (e.g. [{__typename:"Product", id:"123"}])
    // to hydrate entity stubs referenced by other subgraphs. We resolve each by its key field.
    @QueryMapping(name = "_entities")
    suspend fun entities(env: DataFetchingEnvironment): List<Any?> {
        val representations = env.getArgument<List<Map<String, Any>>>("representations") ?: emptyList()
        log.info("GraphQL query: _entities — resolving {} representations", representations.size)
        return representations.map { representation ->
            when (representation["__typename"]) {
                "Product" -> when {
                    representation.containsKey("id") -> {
                        val id = representation["id"] as? String ?: return@map null
                        productService.getProductById(id)
                    }

                    representation.containsKey("skuCode") -> {
                        val skuCode = representation["skuCode"] as? String ?: return@map null
                        productService.getProductBySkuCode(skuCode)
                    }

                    else -> null
                }

                else -> null
            }
        }
    }

    companion object {
        // The SDL advertised to the router. Includes @key to mark Product as a federation entity,
        // but excludes the federation built-ins (_entities, _service, _Entity, _Service, _Any)
        // which the router already knows about and adds itself during supergraph composition.
        private val SUBGRAPH_SDL = """
            scalar BigDecimal

            directive @key(fields: String!, resolvable: Boolean) repeatable on OBJECT | INTERFACE

            type Query {
                products: [Product!]!
            }

            type Mutation {
                createProduct(input: CreateProductInput!): CreatedProduct!
            }

            type Product @key(fields: "id") @key(fields: "skuCode") {
                id: String!
                name: String!
                description: String!
                price: BigDecimal!
                skuCode: String
            }

            type CreatedProduct {
                productId: String!
            }

            input CreateProductInput {
                name: String!
                description: String!
                price: BigDecimal!
                skuCode: String!
            }
        """.trimIndent()
    }
}
