package org.tuaregue.khepri.bff

import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.service.ServiceValidationException
import java.math.BigDecimal

class WorkshopOrchestratorTests extends OFBizTestCase {

    private GenericValue userLogin
    private static final String TEST_FACILITY = "OFICINA_ATIBAIA"
    private static final String TEST_PRODUCT_STORE = "9000"

    WorkshopOrchestratorTests(String name) {
        super(name)
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp()
        userLogin = EntityQuery.use(getDelegator()).from("UserLogin").where("userLoginId", "system").queryOne()

        getDelegator().createOrStore(getDelegator().makeValue("UserLoginSecurityGroup", [
                userLoginId: "system",
                groupId: "SUPER",
                fromDate: UtilDateTime.nowTimestamp()
        ]))

        getDelegator().createOrStore(getDelegator().makeValue("StatusItem", [
                statusId: "QUOTE_SENT",
                statusTypeId: "QUOTE_STATUS",
                description: "Orçamento Enviado"
        ]))

        getDelegator().createOrStore(getDelegator().makeValue("Party", [partyId: "Company", partyTypeId: "PARTY_GROUP"]))
        getDelegator().createOrStore(getDelegator().makeValue("RoleType", [roleTypeId: "INT_ORG", description: "Internal Organization"]))
        getDelegator().createOrStore(getDelegator().makeValue("PartyRole", [partyId: "Company", roleTypeId: "INT_ORG"]))

        // Configuração de Infraestrutura para testes de Order
        getDelegator().createOrStore(getDelegator().makeValue("Facility", [facilityId: TEST_FACILITY, facilityTypeId: "WAREHOUSE", ownerPartyId: "Company"]))
        getDelegator().createOrStore(getDelegator().makeValue("ProductStore", [
                productStoreId: TEST_PRODUCT_STORE,
                payToPartyId: "Company",
                inventoryFacilityId: TEST_FACILITY,
                reserveInventory: "Y",
                checkInventory: "N",
                defaultCurrencyUomId: "BRL"
        ]))
    }

    void testRegisterVehicleServiceEntry_Success() {
        long sfx = System.currentTimeMillis() % 100000
        Map ctx = [
                firstName: "Emerson",
                lastName: "Rios",
                licensePlate: "ABC-" + sfx,
                userLogin: userLogin
        ]

        Map resp = getDispatcher().runSync("registerVehicleServiceEntry", ctx)

        assert ServiceUtil.isSuccess(resp)
        assert resp.partyId != null
        assert resp.fixedAssetId != null
        assert resp.workEffortId != null
    }

    void testRegisterVehicleServiceEntry_Idempotency_Person() {
        long sfx = System.currentTimeMillis() % 100000
        String fName = "Joao" + sfx
        String lName = "Silva" + sfx

        Map ctx1 = [firstName: fName, lastName: lName, licensePlate: "PLATE1-" + sfx, userLogin: userLogin]
        Map resp1 = getDispatcher().runSync("registerVehicleServiceEntry", ctx1)
        String partyId1 = resp1.partyId

        Map ctx2 = [firstName: fName, lastName: lName, licensePlate: "PLATE2-" + sfx, userLogin: userLogin]
        Map resp2 = getDispatcher().runSync("registerVehicleServiceEntry", ctx2)
        String partyId2 = resp2.partyId

        assertEquals("O partyId deve ser o mesmo para o mesmo nome e sobrenome", partyId1, partyId2)
    }

    void testRegisterVehicleServiceEntry_Idempotency_Asset_Sanitization() {
        long sfx = System.currentTimeMillis() % 100000
        String plateOriginal = "khp-" + sfx
        String plateSanitizedInput = "  KHP-" + sfx + "  "

        Map ctx1 = [firstName: "User1", lastName: "Test", licensePlate: plateOriginal, userLogin: userLogin]
        Map resp1 = getDispatcher().runSync("registerVehicleServiceEntry", ctx1)
        String assetId1 = resp1.fixedAssetId

        Map ctx2 = [firstName: "User2", lastName: "Test", licensePlate: plateSanitizedInput, userLogin: userLogin]
        Map resp2 = getDispatcher().runSync("registerVehicleServiceEntry", ctx2)
        String assetId2 = resp2.fixedAssetId

        assertEquals("O fixedAssetId deve ser o mesmo devido a sanitizao e busca por nome", assetId1, assetId2)
    }

    void testRegisterVehicleServiceEntry_FailureMissingLastName() {
        Map ctx = [
                firstName: "John",
                licensePlate: "FAIL-123",
                userLogin: userLogin
        ]

        try {
            getDispatcher().runSync("registerVehicleServiceEntry", ctx)
            fail("Deveria ter falhado")
        } catch (ServiceValidationException e) {
            assert e.getMessage().contains("lastName")
        }
    }

    void testRegisterVehicleServiceEntry_MissingPlate() {
        Map ctx = [
                firstName: "John",
                lastName: "Doe",
                userLogin: userLogin
        ]

        try {
            getDispatcher().runSync("registerVehicleServiceEntry", ctx)
            fail("Deveria ter falhado")
        } catch (ServiceValidationException e) {
            assert e.getMessage().contains("licensePlate")
        }
    }

    void testCreateWorkshopQuoteFromOS_Success() {
        long sfx = System.currentTimeMillis() % 100000
        Map entryCtx = [
                firstName: "Quote",
                lastName: "Test",
                licensePlate: "QT-" + sfx,
                userLogin: userLogin
        ]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        String workEffortId = entryResp.workEffortId

        Map quoteCtx = [
                workEffortId: workEffortId,
                userLogin: userLogin
        ]
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", quoteCtx)

        assert ServiceUtil.isSuccess(quoteResp)
    }

    void testCreateWorkshopQuoteFromOS_FailureNoClient() {
        String workEffortId = getDelegator().getNextSeqId("WorkEffort")
        GenericValue workEffort = getDelegator().makeValue("WorkEffort", [
                workEffortId: workEffortId,
                workEffortTypeId: "SERVICE_EVENT",
                currentStatusId: "WE_CREATED",
                workEffortName: "Test OS Without Client"
        ])
        getDelegator().createOrStore(workEffort)

        Map quoteCtx = [
                workEffortId: workEffortId,
                userLogin: userLogin
        ]
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", quoteCtx)

        assert ServiceUtil.isError(quoteResp)
    }

    void testAddItemToWorkshopQuote_Success() {
        long sfx = System.currentTimeMillis() % 100000
        String productId = "PROD_TEST_" + sfx

        getDelegator().createOrStore(getDelegator().makeValue("Product", [productId: productId, productTypeId: "FINISHED_GOOD", internalName: "Peca de Teste"]))

        Map entryCtx = [
                firstName: "Item",
                lastName: "Tester",
                licensePlate: "ADD-" + sfx,
                userLogin: userLogin
        ]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        Map quoteCtx = [
                workEffortId: entryResp.workEffortId,
                userLogin: userLogin
        ]
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", quoteCtx)
        String quoteId = quoteResp.quoteId

        Map addItemCtx = [
                quoteId: quoteId,
                productId: productId,
                quantity: new BigDecimal("2.0"),
                facilityId: TEST_FACILITY,
                userLogin: userLogin
        ]
        Map addItemResp = getDispatcher().runSync("addItemToWorkshopQuote", addItemCtx)

        assert ServiceUtil.isSuccess(addItemResp)
    }

    void testAddItemToWorkshopQuote_FailureInvalidQuote() {
        Map addItemCtx = [
                quoteId: "NON_EXISTENT",
                productId: "PROD_MANUF",
                quantity: new BigDecimal("1.0"),
                userLogin: userLogin
        ]
        Map addItemResp = getDispatcher().runSync("addItemToWorkshopQuote", addItemCtx)

        assert ServiceUtil.isError(addItemResp)
    }

    void testAddItemToWorkshopQuote_LockedStatus() {
        long sfx = System.currentTimeMillis() % 100000
        String productId = "PROD_MANUF_" + sfx

        getDelegator().createOrStore(getDelegator().makeValue("Product", [productId: productId, productTypeId: "FINISHED_GOOD", internalName: "Peca Manuf"]))

        Map entryCtx = [firstName: "Lock", lastName: "Test", licensePlate: "LCK-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        Map quoteCtx = [workEffortId: entryResp.workEffortId, userLogin: userLogin]
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", quoteCtx)
        String quoteId = quoteResp.quoteId

        GenericValue quote = EntityQuery.use(getDelegator()).from("Quote").where("quoteId", quoteId).queryOne()
        quote.set("statusId", "QUOTE_APPROVED")
        quote.store()

        Map addItemCtx = [quoteId: quoteId, productId: productId, quantity: BigDecimal.ONE, facilityId: TEST_FACILITY, userLogin: userLogin]
        Map addItemResp = getDispatcher().runSync("addItemToWorkshopQuote", addItemCtx)

        assert ServiceUtil.isError(addItemResp)
    }

    void testAddItemToWorkshopQuote_SuccessWithoutInventory() {
        long sfx = System.currentTimeMillis() % 100000
        String productId = "PROD_EMPTY_" + sfx
        getDelegator().createOrStore(getDelegator().makeValue("Product", [productId: productId, productTypeId: "FINISHED_GOOD", internalName: "Empty Part"]))

        Map entryCtx = [firstName: "Empty", lastName: "Test", licensePlate: "EMP-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        Map qRes = getDispatcher().runSync("createWorkshopQuoteFromOS", [workEffortId: entryResp.workEffortId, userLogin: userLogin])

        Map addItemCtx = [quoteId: qRes.quoteId, productId: productId, quantity: BigDecimal.TEN, facilityId: TEST_FACILITY, userLogin: userLogin]
        Map addItemResp = getDispatcher().runSync("addItemToWorkshopQuote", addItemCtx)

        assert ServiceUtil.isSuccess(addItemResp)
    }

    void testApproveWorkshopQuoteAndCreateOrder_Success() {
        long sfx = System.currentTimeMillis() % 100000
        String productId = "PROD_ORDER_" + sfx

        getDelegator().createOrStore(getDelegator().makeValue("Product", [
                productId: productId,
                productTypeId: "FINISHED_GOOD",
                internalName: "Item de Pedido Teste"
        ]))

        getDelegator().createOrStore(getDelegator().makeValue("ProductPrice", [
                productId: productId,
                productPriceTypeId: "DEFAULT_PRICE",
                productPricePurposeId: "COMPONENT_PRICE", // Ajustado para ser compatível com venda
                currencyUomId: "BRL",
                productStoreGroupId: "_NA_",
                fromDate: UtilDateTime.nowTimestamp(),
                price: new BigDecimal("150.00")
        ]))

        Map entryCtx = [firstName: "Ord", lastName: "Test", licensePlate: "ORD-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", [workEffortId: entryResp.workEffortId, userLogin: userLogin])
        String quoteId = quoteResp.quoteId

        getDispatcher().runSync("updateQuote", [quoteId: quoteId, productStoreId: TEST_PRODUCT_STORE, currencyUomId: "BRL", userLogin: userLogin])

        Map addItemCtx = [
                quoteId: quoteId,
                productId: productId,
                quantity: BigDecimal.ONE,
                userLogin: userLogin
        ]
        Map addItemResp = getDispatcher().runSync("addItemToWorkshopQuote", addItemCtx)
        assert ServiceUtil.isSuccess(addItemResp)

        getDispatcher().runSync("updateQuote", [quoteId: quoteId, statusId: "QUOTE_APPROVED", userLogin: userLogin])

        Map orderResp = getDispatcher().runSync("approveWorkshopQuoteAndCreateOrder", [quoteId: quoteId, userLogin: userLogin])

        assert ServiceUtil.isSuccess(orderResp)
        assert orderResp.orderId != null

        GenericValue orderHeader = EntityQuery.use(getDelegator()).from("OrderHeader").where("orderId", orderResp.orderId).queryOne()
        assert orderHeader != null
    }

    void testRequestVehicleGatePass_Success() {
        long sfx = System.currentTimeMillis() % 100000
        String weId = getDelegator().getNextSeqId("WorkEffort")
        getDelegator().create(getDelegator().makeValue("WorkEffort", [workEffortId: weId, workEffortTypeId: "SERVICE_EVENT"]))

        Map resp = getDispatcher().runSync("requestVehicleGatePass", [workEffortId: weId, userLogin: userLogin])
        assert resp != null
    }
}