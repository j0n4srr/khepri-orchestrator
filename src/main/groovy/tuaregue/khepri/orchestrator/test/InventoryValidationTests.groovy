package tuaregue.khepri.orchestrator.test

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase
import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime

class InventoryValidationTests extends OFBizTestCase {

    private GenericValue userLogin
    private String sfx
    private Timestamp now = UtilDateTime.nowTimestamp()

    InventoryValidationTests(String name) {
        super(name)
    }

    @Override
    void setUp() throws Exception {
        super.setUp()
        userLogin = getDelegator().findOne("UserLogin", [userLoginId: "system"], true)
        sfx = "_" + (System.currentTimeMillis() % 100000)
    }

    void testVerifyOrderNotFound() {
        String orderId = "NON_EXISTENT" + sfx

        Map result = getDispatcher().runSync("khepriVerifyPartsAvailability", [orderId: orderId, userLogin: userLogin])

        assert ServiceUtil.isError(result)
        assert ServiceUtil.getErrorMessage(result).contains("Ordem não encontrada")
    }

    void testVerifyNoItems() {
        String orderId = "ORD_EMPTY" + sfx
        getDelegator().create(getDelegator().makeValue("OrderHeader", [orderId: orderId, orderTypeId: "SALES_ORDER", statusId: "ORDER_CREATED", entryDate: now]))

        Map result = getDispatcher().runSync("khepriVerifyPartsAvailability", [orderId: orderId, userLogin: userLogin])

        assert ServiceUtil.isSuccess(result)
        assertEquals("Deveria ser falso para ordem sem itens", false, result.allPartsReserved)
    }

    void testVerifyItemsNoReservation() {
        String orderId = "ORD_NO_RES" + sfx
        String prodId = "PROD_TEST" + sfx

        getDelegator().create(getDelegator().makeValue("Product", [productId: prodId, productTypeId: "FINISHED_GOOD", internalName: "Peca Teste"]))
        getDelegator().create(getDelegator().makeValue("OrderHeader", [orderId: orderId, orderTypeId: "SALES_ORDER", statusId: "ORDER_APPROVED", entryDate: now]))
        getDelegator().create(getDelegator().makeValue("OrderItem", [orderId: orderId, orderItemSeqId: "00001", productId: prodId, statusId: "ITEM_APPROVED", quantity: 1.0, unitPrice: 10.0]))

        Map result = getDispatcher().runSync("khepriVerifyPartsAvailability", [orderId: orderId, userLogin: userLogin])

        assert ServiceUtil.isSuccess(result)
        assertEquals("Deveria ser falso pois nao ha reserva", false, result.allPartsReserved)
    }

    void testVerifyItemsFullReservation() {
        String orderId = "ORD_OK" + sfx
        String prodId = "PROD_OK" + sfx
        String facilityId = "WH_TEST" + sfx
        String inventoryItemId = "INV_OK" + sfx

        getDelegator().create(getDelegator().makeValue("Facility", [facilityId: facilityId, facilityName: "Almoxarifado Teste", facilityTypeId: "WAREHOUSE"]))
        getDelegator().create(getDelegator().makeValue("Product", [productId: prodId, productTypeId: "FINISHED_GOOD", internalName: "Peca OK"]))
        getDelegator().create(getDelegator().makeValue("InventoryItem", [inventoryItemId: inventoryItemId, productId: prodId, facilityId: facilityId, inventoryItemTypeId: "NON_SERIAL_INV_ITEM"]))

        getDelegator().create(getDelegator().makeValue("OrderHeader", [orderId: orderId, orderTypeId: "SALES_ORDER", statusId: "ORDER_APPROVED", entryDate: now]))
        getDelegator().create(getDelegator().makeValue("OrderItem", [orderId: orderId, orderItemSeqId: "00001", productId: prodId, statusId: "ITEM_APPROVED", quantity: 1.0, unitPrice: 10.0]))

        getDelegator().create(getDelegator().makeValue("OrderItemShipGrpInvRes", [
                orderId: orderId,
                shipGroupSeqId: "00001",
                orderItemSeqId: "00001",
                inventoryItemId: inventoryItemId,
                quantity: 1.0,
                reservedDatetime: now
        ]))

        Map result = getDispatcher().runSync("khepriVerifyPartsAvailability", [orderId: orderId, userLogin: userLogin])

        assert ServiceUtil.isSuccess(result)
        assertEquals("Deveria ser verdadeiro pois itens batem com reservas", true, result.allPartsReserved)
    }
}