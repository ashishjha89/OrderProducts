package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {

    description "should return availability=true for iphone_13 and availability=false for iphone_14"

    request {
        url "/api/inventory?skuCode=iphone_13&skuCode=iphone_14"
        method GET()
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                ["skuCode"  : "iphone_13",
                 "isInStock": true],
                ["skuCode"  : "iphone_14",
                 "isInStock": false]
        ])
    }

}
