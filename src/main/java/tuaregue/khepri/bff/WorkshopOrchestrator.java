package org.tuaregue.khepri.bff;

import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.GenericServiceException;
import java.util.Map;
import java.util.HashMap;

public class WorkshopOrchestrator {
    public static Map<String, Object> registerVehicleServiceEntry(DispatchContext dctx, Map<String, ? extends Object> context) {
        if (context.get("firstName") == null) {
            return ServiceUtil.returnError("O parâmetro firstName é obrigatório.");
        }
        if (context.get("licensePlate") == null) {
            return ServiceUtil.returnError("O parâmetro licensePlate é obrigatório.");
        }

        Map<String, Object> result = ServiceUtil.returnSuccess();
        try {
            // 1. Criar Solicitante (Pessoa)
            Map<String, Object> personCtx = new HashMap<>();
            personCtx.put("firstName", context.get("firstName"));
            personCtx.put("lastName", context.get("lastName"));
            personCtx.put("userLogin", context.get("userLogin"));
            Map<String, Object> personRes = dctx.getDispatcher().runSync("createPerson", personCtx);
            if (ServiceUtil.isError(personRes)) return personRes;
            String partyId = (String) personRes.get("partyId");

            // 2. Criar/Identificar Veículo (FixedAsset)
            Map<String, Object> assetCtx = new HashMap<>();
            assetCtx.put("fixedAssetTypeId", "VEHICLE");
            assetCtx.put("fixedAssetName", context.get("licensePlate"));
            assetCtx.put("userLogin", context.get("userLogin"));
            Map<String, Object> assetRes = dctx.getDispatcher().runSync("createFixedAsset", assetCtx);
            if (ServiceUtil.isError(assetRes)) return assetRes;
            String fixedAssetId = (String) assetRes.get("fixedAssetId");

            // 3. Criar Atendimento (WorkEffort - A Visita)
            Map<String, Object> workEffortCtx = new HashMap<>();
            workEffortCtx.put("workEffortTypeId", "SERVICE_EVENT");
            workEffortCtx.put("workEffortName", "Atendimento Veicular: " + context.get("licensePlate"));
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
            return ServiceUtil.returnError("Erro na orquestração: " + e.getMessage());
        }
        return result;
    }
}