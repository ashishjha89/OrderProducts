/* groovylint-disable CompileStatic */
package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should return positive quantity for skuCodes=iphone_13 and zero for iphone_14'

    request {
        url '/api/inventory?skuCode=iphone_13&skuCode=iphone_14'
        method GET()
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                ['skuCode'  : 'iphone_13',
                 'availableQuantity': 10],
                ['skuCode'  : 'iphone_14',
                 'availableQuantity': 0]
        ])
    }
}
