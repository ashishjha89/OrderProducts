package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {

    description "should return availability=true for both iphone_12 and iphone_13"

    request {
        url "/api/inventory?skuCode=iphone_12&skuCode=iphone_13"
        method GET()
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                ["skuCode"  : "iphone_12",
                 "isInStock": true],
                ["skuCode"  : "iphone_13",
                 "isInStock": true]
        ])
    }

}
