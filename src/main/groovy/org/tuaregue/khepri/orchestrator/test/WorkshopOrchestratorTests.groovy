package org.tuaregue.khepri.bff

import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.service.ServiceValidationException

class WorkshopOrchestratorTests extends OFBizTestCase {

    private GenericValue userLogin

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
}