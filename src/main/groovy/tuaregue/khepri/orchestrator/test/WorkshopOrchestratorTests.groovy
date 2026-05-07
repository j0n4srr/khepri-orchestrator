package org.tuaregue.khepri.bff;

import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.testtools.OFBizTestCase;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import java.util.Map;
import java.util.HashMap;

/**
 * Testes de Integração para a Orquestração de Oficina (OFBiz 24.09)
 * Técnicas aplicadas: Caminho Básico e Particionamento de Equivalência.
 */
public class WorkshopOrchestratorTests extends OFBizTestCase {

    public WorkshopOrchestratorTests(String name) {
        super(name);
    }

    private GenericValue userLogin;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Pré-condição: Ter um usuário sistêmico para as chamadas de serviço
        userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").queryOne();
    }

    /**
     * CENÁRIO: Fluxo Feliz (Happy Path)
     * Objetivo: Validar a criação de toda a cadeia de atendimento.
     */
    public void testRegisterVehicleServiceEntry_Success() throws Exception {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("firstName", "Emerson");
        ctx.put("lastName", "Rios");
        ctx.put("licensePlate", "ABC-1234");
        ctx.put("userLogin", userLogin);

        Map<String, Object> resp = dispatcher.runSync("registerVehicleServiceEntry", ctx);

        // Assert 1: Sucesso do serviço
        assertTrue(ServiceUtil.isSuccess(resp));

        String partyId = (String) resp.get("partyId");
        String fixedAssetId = (String) resp.get("fixedAssetId");
        String workEffortId = (String) resp.get("workEffortId");

        // Assert 2: Verificação de persistência (Entity Engine)
        assertNotNull(partyId);
        GenericValue person = EntityQuery.use(delegator).from("Person").where("partyId", partyId).queryOne();
        assertEquals("Emerson", person.getString("firstName"));

        assertNotNull(fixedAssetId);
        GenericValue asset = EntityQuery.use(delegator).from("FixedAsset").where("fixedAssetId", fixedAssetId).queryOne();
        assertEquals("ABC-1234", asset.getString("fixedAssetName"));

        // Assert 3: Validação do Vínculo (Regra de Negócio)
        GenericValue assignment = EntityQuery.use(delegator)
                .from("WorkEffortPartyAssignment")
                .where("workEffortId", workEffortId, "partyId", partyId, "roleTypeId", "CLIENT")
                .queryFirst();
        assertNotNull("O vínculo entre o cliente e o atendimento deve existir", assignment);
    }

    /**
     * CENÁRIO: Negativo - Falha na criação da Pessoa
     * Objetivo: Validar que o orquestrador interrompe o fluxo se um sub-serviço falhar.
     * Técnica: Valor Limite / Falha de Integração.
     */
    public void testRegisterVehicleServiceEntry_FailureOnPerson() throws Exception {
        Map<String, Object> ctx = new HashMap<>();
        // Omitindo firstName para forçar erro no serviço 'createPerson' nativo
        ctx.put("lastName", "Teste Erro");
        ctx.put("licensePlate", "ERR-0000");
        ctx.put("userLogin", userLogin);

        Map<String, Object> resp = dispatcher.runSync("registerVehicleServiceEntry", ctx);

        // Assert: Deve retornar erro propagado do createPerson
        assertTrue(ServiceUtil.isError(resp));
        assertNull(resp.get("workEffortId"));
    }

    /**
     * CENÁRIO: Negativo - Placa Inválida
     * Nota: No OFBiz, 'createFixedAsset' pode falhar se tipos obrigatórios não forem passados.
     */
    public void testRegisterVehicleServiceEntry_MissingPlate() throws Exception {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("firstName", "John");
        ctx.put("lastName", "Doe");
        // licensePlate nulo
        ctx.put("userLogin", userLogin);

        Map<String, Object> resp = dispatcher.runSync("registerVehicleServiceEntry", ctx);

        assertTrue("O serviço deve falhar quando a placa (fixedAssetName) está ausente", ServiceUtil.isError(resp));
    }
}