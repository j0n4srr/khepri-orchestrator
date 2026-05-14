package org.tuaregue.khepri.bff

import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.service.ServiceValidationException
import java.math.BigDecimal

class WorkshopOrchestratorTests extends OFBizTestCase {

    private GenericValue userLogin
    private static final String TEST_FACILITY = "OFICINA_ATIBAIA"

    WorkshopOrchestratorTests(String name) {
        super(name)
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp()
        userLogin = EntityQuery.use(getDelegator()).from("UserLogin").where("userLoginId", "system").queryOne()

        GenericValue perm = getDelegator().makeValue("UserLoginSecurityGroup", [
                userLoginId: "system",
                groupId: "SUPER",
                fromDate: UtilDateTime.nowTimestamp()
        ])
        getDelegator().createOrStore(perm)
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

        GenericValue person = EntityQuery.use(getDelegator()).from("Person").where("partyId", resp.partyId).queryOne()
        assert person.firstName == "Emerson"

        GenericValue asset = EntityQuery.use(getDelegator()).from("FixedAsset").where("fixedAssetId", resp.fixedAssetId).queryOne()
        assert asset.fixedAssetName == ("ABC-" + sfx)
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

        assertEquals("O fixedAssetId deve ser o mesmo devido a sanitização e busca por nome", assetId1, assetId2)
    }

    void testRegisterVehicleServiceEntry_FailureMissingLastName() {
        Map ctx = [
                firstName: "John",
                licensePlate: "FAIL-123",
                userLogin: userLogin
        ]

        try {
            getDispatcher().runSync("registerVehicleServiceEntry", ctx)
            fail("Deveria ter lançado ServiceValidationException devido ao lastName ausente")
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
            fail("Deveria ter lançado ServiceValidationException devido à licensePlate ausente")
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
        assert quoteResp.quoteId != null

        GenericValue weQuote = EntityQuery.use(getDelegator())
                .from("QuoteWorkEffort")
                .where("workEffortId", workEffortId, "quoteId", quoteResp.quoteId)
                .queryOne()
        assert weQuote != null
    }

    void testCreateWorkshopQuoteFromOS_FailureNoClient() {
        String workEffortId = getDelegator().getNextSeqId("WorkEffort")
        GenericValue workEffort = getDelegator().makeValue("WorkEffort", [
                workEffortId: workEffortId,
                workEffortTypeId: "SERVICE_EVENT",
                currentStatusId: "WE_CREATED",
                workEffortName: "Test OS Without Client"
        ])
        getDelegator().create(workEffort)

        Map quoteCtx = [
                workEffortId: workEffortId,
                userLogin: userLogin
        ]
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", quoteCtx)

        assert ServiceUtil.isError(quoteResp)
        assert ServiceUtil.getErrorMessage(quoteResp).contains("Nenhum cliente")
    }

    void testAddItemToWorkshopQuote_Success() {
        long sfx = System.currentTimeMillis() % 100000
        String productId = "PROD_TEST_" + sfx

        // Setup: Garantir que o produto existe e tem estoque na Facility correta
        getDelegator().create("Product", [productId: productId, productTypeId: "FINISHED_GOOD", internalName: "Peça de Teste"])
        getDelegator().create("InventoryItem", [
                inventoryItemId: "INV_" + sfx,
                inventoryItemTypeId: "NON_SERIAL_INV_ITEM",
                productId: productId,
                facilityId: TEST_FACILITY,
                quantityOnHandTotal: new BigDecimal("10.0"),
                availableToPromiseTotal: new BigDecimal("10.0")
        ])

        Map entryCtx = [
                firstName: "Item",
                lastName: "Tester",
                licensePlate: "ADD-" + sfx,
                userLogin: userLogin
        ]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        String workEffortId = entryResp.workEffortId

        Map quoteCtx = [
                workEffortId: workEffortId,
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
        assert addItemResp.quoteItemSeqId != null

        GenericValue quoteItem = EntityQuery.use(getDelegator())
                .from("QuoteItem")
                .where("quoteId", quoteId, "quoteItemSeqId", addItemResp.quoteItemSeqId)
                .queryOne()
        assert quoteItem != null
        assert quoteItem.productId == productId
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
        assert ServiceUtil.getErrorMessage(addItemResp).contains("encontrado")
    }
}