/* groovylint-disable CompileStatic, DuplicateNumberLiteral, DuplicateStringLiteral */
package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should reserve products successfully and return available quantities'

    request {
        url '/api/inventory/reserve'
        method POST()
        headers {
            contentType applicationJson()
        }
        body([
                'orderNumber': 'ORDER-123',
                'itemReservationRequests': [
                        [
                                'skuCode': 'iphone_12',
                                'quantity': 3
                        ],
                        [
                                'skuCode': 'iphone_13',
                                'quantity': 5
                        ]
                ]
        ])
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
                [
                        'skuCode': 'iphone_12',
                        'quantity': 2
                ],
                [
                        'skuCode': 'iphone_13',
                        'quantity': 5
                ]
        ])
    }
} 