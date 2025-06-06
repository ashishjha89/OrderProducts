package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should return true and false availability status for skuCodes=iphone_13,iphone_14"

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
                 "inStock": true],
                ["skuCode"  : "iphone_14",
                 "inStock": false]
        ])
    }

}
