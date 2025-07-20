/* groovylint-disable CompileStatic, DuplicateNumberLiteral, DuplicateStringLiteral */
package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
        description 'should return insufficient items error when requested quantity exceeds available'

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
                                'quantity': 100
                        ]
                ]
        ])
        }

        response {
                status CONFLICT()
                headers {
                        contentType applicationJson()
                }
                body([
                'errorCode': 'NOT_ENOUGH_ITEM_ERROR_CODE',
                'errorMessage': 'Not enough stock for some products',
                'unavailableProducts': [
                        [
                                'skuCode': 'iphone_12',
                                'requestedQuantity': 100,
                                'availableQuantity': 5
                        ]
                ]
        ])
        }
}
