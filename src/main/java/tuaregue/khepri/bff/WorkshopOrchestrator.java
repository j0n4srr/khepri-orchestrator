package org.tuaregue.khepri.bff;

import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.base.util.Debug;
import java.util.Map;
import java.util.HashMap;

public class WorkshopOrchestrator {
    public static final String MODULE = WorkshopOrchestrator.class.getName();

    public static Map<String, Object> registerVehicleServiceEntry(DispatchContext dctx, Map<String, ? extends Object> context) {
        if (context.get("firstName") == null) {
            return ServiceUtil.returnError("O parâmetro firstName é obrigatório.");
        }
        if (context.get("lastName") == null) {
            return ServiceUtil.returnError("O parâmetro lastName é obrigatório.");
        }
        if (context.get("licensePlate") == null) {
            return ServiceUtil.returnError("O parâmetro licensePlate é obrigatório.");
        }

        String firstName = (String) context.get("firstName");
        String lastName = (String) context.get("lastName");
        String licensePlate = ((String) context.get("licensePlate")).trim().toUpperCase();

        Map<String, Object> result = ServiceUtil.returnSuccess();
        try {
            // 1. Identificar ou Criar Solicitante (Pessoa)
            String partyId = null;
            GenericValue existingPerson = EntityQuery.use(dctx.getDelegator())
                    .from("Person")
                    .where("firstName", firstName, "lastName", lastName)
                    .queryFirst();

            if (existingPerson != null) {
                partyId = existingPerson.getString("partyId");
            } else {
                Map<String, Object> personCtx = new HashMap<>();
                personCtx.put("firstName", firstName);
                personCtx.put("lastName", lastName);
                personCtx.put("userLogin", context.get("userLogin"));
                Map<String, Object> personRes = dctx.getDispatcher().runSync("createPerson", personCtx);
                if (ServiceUtil.isError(personRes)) return personRes;
                partyId = (String) personRes.get("partyId");
            }

            // 2. Identificar ou Criar Veículo (FixedAsset)
            String fixedAssetId = null;
            GenericValue existingAsset = EntityQuery.use(dctx.getDelegator())
                    .from("FixedAsset")
                    .where("fixedAssetName", licensePlate)
                    .queryFirst();

            if (existingAsset != null) {
                fixedAssetId = existingAsset.getString("fixedAssetId");
            } else {
                Map<String, Object> assetCtx = new HashMap<>();
                assetCtx.put("fixedAssetTypeId", "VEHICLE");
                assetCtx.put("fixedAssetName", licensePlate);
                assetCtx.put("userLogin", context.get("userLogin"));
                Map<String, Object> assetRes = dctx.getDispatcher().runSync("createFixedAsset", assetCtx);
                if (ServiceUtil.isError(assetRes)) return assetRes;
                fixedAssetId = (String) assetRes.get("fixedAssetId");
            }

            // 3. Criar Atendimento (WorkEffort - A Visita)
            Map<String, Object> workEffortCtx = new HashMap<>();
            workEffortCtx.put("workEffortTypeId", "SERVICE_EVENT");
            workEffortCtx.put("workEffortName", "Atendimento Veicular: " + licensePlate);
            workEffortCtx.put("fixedAssetId", fixedAssetId);
            workEffortCtx.put("currentStatusId", "WE_CREATED");
            workEffortCtx.put("userLogin", context.get("userLogin"));
            Map<String, Object> workRes = dctx.getDispatcher().runSync("createWorkEffort", workEffortCtx);
            if (ServiceUtil.isError(workRes)) return workRes;
            String workEffortId = (String) workRes.get("workEffortId");

            // 4. Vincular Solicitante ao Atendimento (WorkEffortPartyAssignment)
            Map<String, Object> linkCtx = new HashMap<>();
            linkCtx.put("workEffortId", workEffortId);
            linkCtx.put("partyId", partyId);
            linkCtx.put("roleTypeId", "CLIENT");
            linkCtx.put("fromDate", org.apache.ofbiz.base.util.UtilDateTime.nowTimestamp());
            linkCtx.put("statusId", "PRTYASGN_ASSIGNED");
            linkCtx.put("userLogin", context.get("userLogin"));
            Map<String, Object> linkRes = dctx.getDispatcher().runSync("assignPartyToWorkEffort", linkCtx);
            if (ServiceUtil.isError(linkRes)) return linkRes;

            result.put("partyId", partyId);
            result.put("fixedAssetId", fixedAssetId);
            result.put("workEffortId", workEffortId);

        } catch (GenericServiceException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro de serviço na orquestração: " + e.getMessage());
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro de banco de dados na orquestração: " + e.getMessage());
        }
        return result;
    }
}