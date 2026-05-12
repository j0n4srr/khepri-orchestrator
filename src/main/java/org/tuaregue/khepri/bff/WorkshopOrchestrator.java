package org.tuaregue.khepri.bff;

import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.tuaregue.khepri.KhepriConstants;

import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;

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
        GenericValue userLogin = (GenericValue) context.get("userLogin");

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
                personCtx.put("userLogin", userLogin);
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
                assetCtx.put("fixedAssetTypeId", KhepriConstants.FIXED_ASSET_TYPE_VEHICLE);
                assetCtx.put("fixedAssetName", licensePlate);
                assetCtx.put("userLogin", userLogin);
                Map<String, Object> assetRes = dctx.getDispatcher().runSync("createFixedAsset", assetCtx);
                if (ServiceUtil.isError(assetRes)) return assetRes;
                fixedAssetId = (String) assetRes.get("fixedAssetId");
            }

            // 3. Criar Atendimento (WorkEffort)
            Map<String, Object> workEffortCtx = new HashMap<>();
            workEffortCtx.put("workEffortTypeId", KhepriConstants.WORK_EFFORT_TYPE_EVENT);
            workEffortCtx.put("workEffortName", "Atendimento Veicular: " + licensePlate);
            workEffortCtx.put("fixedAssetId", fixedAssetId);
            workEffortCtx.put("currentStatusId", KhepriConstants.WE_STATUS_CREATED);
            workEffortCtx.put("userLogin", userLogin);
            Map<String, Object> workRes = dctx.getDispatcher().runSync("createWorkEffort", workEffortCtx);
            if (ServiceUtil.isError(workRes)) return workRes;
            String workEffortId = (String) workRes.get("workEffortId");

            // 4. Vincular Solicitante ao Atendimento
            Map<String, Object> linkCtx = new HashMap<>();
            linkCtx.put("workEffortId", workEffortId);
            linkCtx.put("partyId", partyId);
            linkCtx.put("roleTypeId", KhepriConstants.ROLE_TYPE_CLIENT);
            linkCtx.put("fromDate", UtilDateTime.nowTimestamp());
            linkCtx.put("statusId", KhepriConstants.ASGN_STATUS_ASSIGNED);
            linkCtx.put("userLogin", userLogin);
            Map<String, Object> linkRes = dctx.getDispatcher().runSync("assignPartyToWorkEffort", linkCtx);
            if (ServiceUtil.isError(linkRes)) return linkRes;

            result.put("partyId", partyId);
            result.put("fixedAssetId", fixedAssetId);
            result.put("workEffortId", workEffortId);

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro na orquestração transacional: " + e.getMessage());
        }
        return result;
    }

    public static Map<String, Object> createWorkshopQuoteFromOS(DispatchContext dctx, Map<String, ? extends Object> context) {
        String workEffortId = (String) context.get("workEffortId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue partyAssignment = EntityQuery.use(dctx.getDelegator())
                    .from("WorkEffortPartyAssignment")
                    .where("workEffortId", workEffortId, "roleTypeId", KhepriConstants.ROLE_TYPE_CLIENT)
                    .filterByDate()
                    .queryFirst();

            if (partyAssignment == null) {
                return ServiceUtil.returnError("Nenhum cliente (CLIENT) vinculado ao atendimento " + workEffortId);
            }
            String partyId = partyAssignment.getString("partyId");

            Map<String, Object> quoteCtx = new HashMap<>();
            quoteCtx.put("quoteTypeId", KhepriConstants.QUOTE_TYPE_WORKSHOP);
            quoteCtx.put("partyId", partyId);
            quoteCtx.put("statusId", KhepriConstants.QUOTE_STATUS_CREATED);
            quoteCtx.put("issueDate", UtilDateTime.nowTimestamp());
            quoteCtx.put("userLogin", userLogin);

            Map<String, Object> quoteRes = dctx.getDispatcher().runSync("createQuote", quoteCtx);
            if (ServiceUtil.isError(quoteRes)) return quoteRes;
            String quoteId = (String) quoteRes.get("quoteId");

            Map<String, Object> weQuoteCtx = new HashMap<>();
            weQuoteCtx.put("workEffortId", workEffortId);
            weQuoteCtx.put("quoteId", quoteId);
            weQuoteCtx.put("userLogin", userLogin);

            Map<String, Object> weQuoteRes = dctx.getDispatcher().runSync("createQuoteWorkEffort", weQuoteCtx);
            if (ServiceUtil.isError(weQuoteRes)) return weQuoteRes;

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("quoteId", quoteId);
            return result;

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro ao gerar orçamento a partir da OS: " + e.getMessage());
        }
    }

    public static Map<String, Object> addItemToWorkshopQuote(DispatchContext dctx, Map<String, ? extends Object> context) {
        String quoteId = (String) context.get("quoteId");
        String productId = (String) context.get("productId");
        BigDecimal quantity = (BigDecimal) context.get("quantity");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue quote = EntityQuery.use(dctx.getDelegator()).from("Quote").where("quoteId", quoteId).queryOne();
            if (quote == null) {
                return ServiceUtil.returnError("Orçamento " + quoteId + " não encontrado.");
            }

            GenericValue product = EntityQuery.use(dctx.getDelegator()).from("Product").where("productId", productId).queryOne();
            if (product == null) {
                return ServiceUtil.returnError("Produto " + productId + " não encontrado.");
            }

            Map<String, Object> priceCtx = new HashMap<>();
            priceCtx.put("product", product);
            priceCtx.put("productId", productId);
            priceCtx.put("partyId", quote.getString("partyId"));
            priceCtx.put("currencyUomId", quote.getString("currencyUomId"));
            priceCtx.put("userLogin", userLogin);

            Map<String, Object> priceRes = dctx.getDispatcher().runSync("calculateProductPrice", priceCtx);
            if (ServiceUtil.isError(priceRes)) return priceRes;
            BigDecimal unitPrice = (BigDecimal) priceRes.get("price");

            Map<String, Object> itemCtx = new HashMap<>();
            itemCtx.put("quoteId", quoteId);
            itemCtx.put("productId", productId);
            itemCtx.put("quantity", quantity);
            itemCtx.put("quoteUnitPrice", unitPrice);
            itemCtx.put("userLogin", userLogin);

            Map<String, Object> itemRes = dctx.getDispatcher().runSync("createQuoteItem", itemCtx);
            if (ServiceUtil.isError(itemRes)) return itemRes;

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("quoteItemSeqId", itemRes.get("quoteItemSeqId"));
            return result;

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro ao adicionar item ao orçamento: " + e.getMessage());
        }
    }
}