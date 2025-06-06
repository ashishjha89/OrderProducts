package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {

    description "should return availability=false for skuCode=iphone_14"

    request {
        url "/api/inventory/iphone_14"
        method GET()
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body(
                "skuCode": "iphone_14",
                "inStock": false
        )
    }

}
