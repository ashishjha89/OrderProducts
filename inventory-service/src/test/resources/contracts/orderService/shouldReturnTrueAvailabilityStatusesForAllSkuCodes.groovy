package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {

    description "should return true availability status for skuCodes=iphone_12,iphone_13"

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
                 "inStock": true],
                ["skuCode"  : "iphone_13",
                 "inStock": true]
        ])
    }

}
