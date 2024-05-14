package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {

    description "should return availability=true for skuCode=iphone_13"

    request {
        url "/api/inventory/iphone_13"
        method GET()
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body(
                "skuCode": "iphone_13",
                "isInStock": true
        )
    }

}
