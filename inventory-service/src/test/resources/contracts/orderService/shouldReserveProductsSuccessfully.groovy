/* groovylint-disable CompileStatic, DuplicateNumberLiteral, DuplicateStringLiteral */
package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

/* 
* Before making reservations: iPhone12 = 5, iPhone13 = 10, iPhone14 = 0.
* See CdcBaseClass.java for more details.
*/

Contract.make {
        description 'should reserve products successfully and return available quantities'

        request {
                url '/api/reservations'
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
                        'availableQuantity': 2
                ],
                [
                        'skuCode': 'iphone_13',
                        'availableQuantity': 5
                ]
        ])
        }
}
