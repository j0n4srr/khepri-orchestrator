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
        getDelegator().createOrStore(getDelegator().makeValue("InventoryItem", [
                inventoryItemId: "INV_" + sfx,
                inventoryItemTypeId: "NON_SERIAL_INV_ITEM",
                productId: productId,
                facilityId: TEST_FACILITY,
                quantityOnHandTotal: new BigDecimal("10.0"),
                availableToPromiseTotal: new BigDecimal("10.0")
        ]))

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
        getDelegator().createOrStore(getDelegator().makeValue("InventoryItem", [
                inventoryItemId: "INV_LCK_" + sfx,
                inventoryItemTypeId: "NON_SERIAL_INV_ITEM",
                productId: productId,
                facilityId: TEST_FACILITY,
                quantityOnHandTotal: new BigDecimal("10.0"),
                availableToPromiseTotal: new BigDecimal("10.0")
        ]))

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

    void testAddItemToWorkshopQuote_ServiceProduct_NoInventoryCheck() {
        long sfx = System.currentTimeMillis() % 100000
        String serviceId = "SERV_TEST_" + sfx
        getDelegator().createOrStore(getDelegator().makeValue("Product", [productId: serviceId, productTypeId: "SERVICE", internalName: "Mo de Obra"]))

        Map entryCtx = [firstName: "Srv", lastName: "Test", licensePlate: "SRV-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        Map qRes = getDispatcher().runSync("createWorkshopQuoteFromOS", [workEffortId: entryResp.workEffortId, userLogin: userLogin])

        Map addItemCtx = [quoteId: qRes.quoteId, productId: serviceId, quantity: BigDecimal.ONE, userLogin: userLogin]
        Map addItemResp = getDispatcher().runSync("addItemToWorkshopQuote", addItemCtx)

        assert ServiceUtil.isSuccess(addItemResp)
    }

    void testAddItemToWorkshopQuote_InsufficientInventory_Fails() {
        long sfx = System.currentTimeMillis() % 100000
        String productId = "PROD_EMPTY_" + sfx
        getDelegator().createOrStore(getDelegator().makeValue("Product", [productId: productId, productTypeId: "FINISHED_GOOD", internalName: "Empty Part"]))

        Map entryCtx = [firstName: "Empty", lastName: "Test", licensePlate: "EMP-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        Map qRes = getDispatcher().runSync("createWorkshopQuoteFromOS", [workEffortId: entryResp.workEffortId, userLogin: userLogin])

        Map addItemCtx = [quoteId: qRes.quoteId, productId: productId, quantity: BigDecimal.TEN, facilityId: TEST_FACILITY, userLogin: userLogin]
        Map addItemResp = getDispatcher().runSync("addItemToWorkshopQuote", addItemCtx)

        assert ServiceUtil.isError(addItemResp)
    }

    void testCreateWorkshopQuoteAmendment_Success() {
        long sfx = System.currentTimeMillis() % 100000
        Map entryCtx = [firstName: "Amend", lastName: "Test", licensePlate: "AM-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        Map quoteCtx = [workEffortId: entryResp.workEffortId, userLogin: userLogin]
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", quoteCtx)
        String originalQuoteId = quoteResp.quoteId

        Map amendCtx = [quoteId: originalQuoteId, diagnosticNote: "Teste de aditivo", userLogin: userLogin]
        Map amendResp = getDispatcher().runSync("createWorkshopQuoteAmendment", amendCtx)

        assert ServiceUtil.isSuccess(amendResp)
    }

    void testCreateWorkshopQuoteAmendment_FailureMissingNote() {
        Map amendCtx = [quoteId: "ANY_ID", userLogin: userLogin] // diagnosticNote ausente propositalmente
        try {
            getDispatcher().runSync("createWorkshopQuoteAmendment", amendCtx)
            fail("Deveria ter falhado")
        } catch (ServiceValidationException e) {
            assert e.getMessage().contains("diagnosticNote")
        }
    }

    void testDecideWorkshopAmendment_Rejected_VerifyReservesDeleted() {
        long sfx = System.currentTimeMillis() % 100000
        String productId = "REJ_PROD_" + sfx
        getDelegator().createOrStore(getDelegator().makeValue("Product", [productId: productId, productTypeId: "FINISHED_GOOD", internalName: "Rejection Part"]))
        getDelegator().createOrStore(getDelegator().makeValue("InventoryItem", [inventoryItemId: "REJ_INV_" + sfx, inventoryItemTypeId: "NON_SERIAL_INV_ITEM", productId: productId, facilityId: TEST_FACILITY, quantityOnHandTotal: BigDecimal.TEN, availableToPromiseTotal: BigDecimal.TEN]))

        Map entryCtx = [firstName: "Rej", lastName: "Test", licensePlate: "REJ-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        String weId = entryResp.workEffortId
        Map quoteCtx = [workEffortId: weId, userLogin: userLogin]
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", quoteCtx)
        String quoteId = quoteResp.quoteId

        getDispatcher().runSync("addItemToWorkshopQuote", [quoteId: quoteId, productId: productId, quantity: BigDecimal.ONE, facilityId: TEST_FACILITY, userLogin: userLogin])

        GenericValue quote = EntityQuery.use(getDelegator()).from("Quote").where("quoteId", quoteId).queryOne()
        quote.set("statusId", "QUOTE_SENT")
        quote.store()

        getDispatcher().runSync("decideWorkshopAmendment", [quoteId: quoteId, statusId: "QUOTE_REJECTED", userLogin: userLogin])

        GenericValue deletedReserve = EntityQuery.use(getDelegator()).from("WorkEffortGoodStandard").where("workEffortId", weId, "productId", productId).queryFirst()
        assert deletedReserve == null
    }

    void testIssuePartsToWorkEffort_Success() {
        long sfx = System.currentTimeMillis() % 100000
        String productId = "ISSUE_PROD_" + sfx

        getDelegator().createOrStore(getDelegator().makeValue("Product", [productId: productId, productTypeId: "FINISHED_GOOD", internalName: "Issuance Part"]))
        getDelegator().createOrStore(getDelegator().makeValue("InventoryItem", [inventoryItemId: "ISS_INV_" + sfx, inventoryItemTypeId: "NON_SERIAL_INV_ITEM", productId: productId, facilityId: TEST_FACILITY, quantityOnHandTotal: BigDecimal.TEN, availableToPromiseTotal: BigDecimal.TEN, ownerPartyId: "Company"]))

        Map entryCtx = [firstName: "Iss", lastName: "Test", licensePlate: "ISS-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        String weId = entryResp.workEffortId

        GenericValue we = EntityQuery.use(getDelegator()).from("WorkEffort").where("workEffortId", weId).queryOne()
        we.set("facilityId", TEST_FACILITY)
        we.store()

        Map quoteCtx = [workEffortId: weId, userLogin: userLogin]
        Map quoteResp = getDispatcher().runSync("createWorkshopQuoteFromOS", quoteCtx)
        getDispatcher().runSync("addItemToWorkshopQuote", [quoteId: quoteResp.quoteId, productId: productId, quantity: BigDecimal.ONE, facilityId: TEST_FACILITY, userLogin: userLogin])

        Map issueResp = getDispatcher().runSync("issuePartsToWorkEffort", [workEffortId: weId, userLogin: userLogin])
        assert ServiceUtil.isSuccess(issueResp)
    }

    void testGetWorkshopOSSummary_CalculatesTotal() {
        long sfx = System.currentTimeMillis() % 100000
        Map entryCtx = [firstName: "Sum", lastName: "Test", licensePlate: "SUM-" + sfx, userLogin: userLogin]
        Map entryResp = getDispatcher().runSync("registerVehicleServiceEntry", entryCtx)
        String weId = entryResp.workEffortId

        Map q1Res = getDispatcher().runSync("createWorkshopQuoteFromOS", [workEffortId: weId, userLogin: userLogin])
        getDelegator().createOrStore(getDelegator().makeValue("QuoteItem", [quoteId: q1Res.quoteId, quoteItemSeqId: "00001", productId: "PROD_MANUF", quantity: BigDecimal.ONE, quoteUnitPrice: new BigDecimal("100.0")]))

        GenericValue quote1 = EntityQuery.use(getDelegator()).from("Quote").where("quoteId", q1Res.quoteId).queryOne()
        quote1.set("statusId", "QUOTE_SENT")
        quote1.store()
        getDispatcher().runSync("updateQuote", [quoteId: q1Res.quoteId, statusId: "QUOTE_APPROVED", userLogin: userLogin])

        Map q2Res = getDispatcher().runSync("createWorkshopQuoteAmendment", [quoteId: q1Res.quoteId, diagnosticNote: "Extra", userLogin: userLogin])
        getDelegator().createOrStore(getDelegator().makeValue("QuoteItem", [quoteId: q2Res.quoteId, quoteItemSeqId: "00001", productId: "PROD_MANUF", quantity: BigDecimal.ONE, quoteUnitPrice: new BigDecimal("50.0")]))

        GenericValue quote2 = EntityQuery.use(getDelegator()).from("Quote").where("quoteId", q2Res.quoteId).queryOne()
        quote2.set("statusId", "QUOTE_SENT")
        quote2.store()
        getDispatcher().runSync("decideWorkshopAmendment", [quoteId: q2Res.quoteId, statusId: "QUOTE_REJECTED", userLogin: userLogin])

        Map summaryResp = getDispatcher().runSync("getWorkshopOSSummary", [workEffortId: weId, userLogin: userLogin])
        assert ServiceUtil.isSuccess(summaryResp)
    }
}