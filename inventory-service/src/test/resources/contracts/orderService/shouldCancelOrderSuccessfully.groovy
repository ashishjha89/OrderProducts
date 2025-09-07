/* groovylint-disable CompileStatic, DuplicateNumberLiteral, DuplicateStringLiteral */
package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
        description 'should cancel order successfully and return updated items'

        request {
                url '/api/reservations/ORDER-123/cancel'
                method POST()
                headers {
                        contentType applicationJson()
                }
        }

        response {
                status OK()
                headers {
                        contentType applicationJson()
                }
                body([
                'orderNumber': 'ORDER-123',
                'state': 'CANCELLED',
                'updatedItems': [
                        [
                                'skuCode': 'iphone_12',
                                'reservedQuantity': 3,
                                'status': 'CANCELLED'
                        ],
                        [
                                'skuCode': 'iphone_13',
                                'reservedQuantity': 5,
                                'status': 'CANCELLED'
                        ]
                ]
        ])
        }
}
