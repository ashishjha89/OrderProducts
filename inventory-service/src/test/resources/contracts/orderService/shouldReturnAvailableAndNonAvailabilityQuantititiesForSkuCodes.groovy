package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should return availability quantities for skuCodes=iphone_12,iphone_13'

    request {
        url '/api/inventory?skuCode=iphone_12&skuCode=iphone_13'
        method GET()
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                ['skuCode'  : 'iphone_12',
                 'quantity': 5],
                ['skuCode'  : 'iphone_13',
                 'quantity': 10]
        ])
    }
}
