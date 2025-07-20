/* groovylint-disable CompileStatic, DuplicateNumberLiteral, DuplicateStringLiteral */
package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
        description 'should return empty list when no reservations found for order and SKUs'

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
                'state': 'CANCELLED'
        ])
        }

        response {
                status OK()
                headers {
                        contentType applicationJson()
                }
                body([
                'orderNumber': 'ORDER-123',
                'state': 'CANCELLED',
                'updatedItems': []
        ])
        }
} 