/* groovylint-disable CompileStatic, DuplicateNumberLiteral, DuplicateStringLiteral */
package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
        description 'should update reservation state successfully and return updated items'

        request {
                url '/api/reservations/ORDER-123/state'
                method PUT()
                headers {
                        contentType applicationJson()
                }
                body([
                'orderNumber': 'ORDER-123',
                'skuCodes': [
                        'iphone_12',
                        'iphone_13'
                ],
                'state': 'FULFILLED'
        ])
        }

        response {
                status OK()
                headers {
                        contentType applicationJson()
                }
                body([
                'orderNumber': 'ORDER-123',
                'state': 'FULFILLED',
                'updatedItems': [
                        [
                                'skuCode': 'iphone_12',
                                'reservedQuantity': 3,
                                'status': 'FULFILLED'
                        ],
                        [
                                'skuCode': 'iphone_13',
                                'reservedQuantity': 5,
                                'status': 'FULFILLED'
                        ]
                ]
        ])
        }
} 