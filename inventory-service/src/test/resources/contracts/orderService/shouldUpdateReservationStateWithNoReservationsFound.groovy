/* groovylint-disable CompileStatic, DuplicateNumberLiteral, DuplicateStringLiteral */
package contracts.orderService

import org.springframework.cloud.contract.spec.Contract

Contract.make {
        description 'should return empty list when no reservations found for order to fulfill'

        request {
                url '/api/reservations/ORDER-NO-RESERVATIONS/fulfill'
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
                'orderNumber': 'ORDER-NO-RESERVATIONS',
                'state': 'FULFILLED',
                'updatedItems': []
        ])
        }
} 