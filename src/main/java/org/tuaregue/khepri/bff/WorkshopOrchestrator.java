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
import java.util.List;
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

            Map<String, Object> workEffortCtx = new HashMap<>();
            workEffortCtx.put("workEffortTypeId", KhepriConstants.WORK_EFFORT_TYPE_EVENT);
            workEffortCtx.put("workEffortName", "Atendimento Veicular: " + licensePlate);
            workEffortCtx.put("fixedAssetId", fixedAssetId);
            workEffortCtx.put("currentStatusId", KhepriConstants.WE_STATUS_CREATED);
            workEffortCtx.put("userLogin", userLogin);
            Map<String, Object> workRes = dctx.getDispatcher().runSync("createWorkEffort", workEffortCtx);
            if (ServiceUtil.isError(workRes)) return workRes;
            String workEffortId = (String) workRes.get("workEffortId");

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
        String facilityId = (String) context.get("facilityId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue quote = EntityQuery.use(dctx.getDelegator()).from("Quote").where("quoteId", quoteId).queryOne();
            if (quote == null) {
                return ServiceUtil.returnError("Orçamento " + quoteId + " não encontrado.");
            }

            String quoteStatusId = quote.getString("statusId");
            if ("QUOTE_APPROVED".equals(quoteStatusId) || "QUOTE_ACCEPTED".equals(quoteStatusId)) {
                return ServiceUtil.returnError("Orçamento bloqueado para alteração direta (Aprovado/Aceito). É obrigatório criar um Aditivo.");
            }

            GenericValue quoteWorkEffort = EntityQuery.use(dctx.getDelegator())
                    .from("QuoteWorkEffort")
                    .where("quoteId", quoteId)
                    .queryFirst();

            if (quoteWorkEffort == null) {
                return ServiceUtil.returnError("Nenhum atendimento vinculado ao orçamento " + quoteId);
            }
            String workEffortId = quoteWorkEffort.getString("workEffortId");

            GenericValue product = EntityQuery.use(dctx.getDelegator()).from("Product").where("productId", productId).queryOne();
            if (product == null) {
                return ServiceUtil.returnError("Produto " + productId + " não encontrado.");
            }

            String productTypeId = product.getString("productTypeId");
            GenericValue productType = EntityQuery.use(dctx.getDelegator()).from("ProductType").where("productTypeId", productTypeId).cache().queryOne();
            boolean isPhysicalPart = "GOOD".equals(productTypeId) || (productType != null && "GOOD".equals(productType.getString("parentTypeId")));

            if (isPhysicalPart) {
                if (facilityId == null) {
                    return ServiceUtil.returnError("FacilityId é parâmetro obrigatório para adição de peças físicas.");
                }

                Map<String, Object> invCtx = new HashMap<>();
                invCtx.put("productId", productId);
                invCtx.put("facilityId", facilityId);
                Map<String, Object> invRes = dctx.getDispatcher().runSync("getInventoryAvailableByFacility", invCtx);
                if (ServiceUtil.isError(invRes)) return invRes;

                BigDecimal atp = (BigDecimal) invRes.get("availableToPromiseTotal");
                if (atp == null || atp.compareTo(quantity) < 0) {
                    return ServiceUtil.returnError("Saldo insuficiente para o produto " + productId + ". Disponível: " + atp);
                }

                Map<String, Object> wegsCtx = new HashMap<>();
                wegsCtx.put("workEffortId", workEffortId);
                wegsCtx.put("productId", productId);
                wegsCtx.put("workEffortGoodStdTypeId", "USE");
                wegsCtx.put("fromDate", UtilDateTime.nowTimestamp());
                wegsCtx.put("estimatedQuantity", quantity);
                wegsCtx.put("userLogin", userLogin);

                Map<String, Object> wegsRes = dctx.getDispatcher().runSync("createWorkEffortGoodStandard", wegsCtx);
                if (ServiceUtil.isError(wegsRes)) return wegsRes;
            }

            Map<String, Object> priceCtx = new HashMap<>();
            priceCtx.put("product", product);
            priceCtx.put("prodCatalogId", context.get("prodCatalogId"));
            priceCtx.put("partyId", quote.get("partyId"));
            priceCtx.put("currencyUomId", quote.get("currencyUomId"));
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

    public static Map<String, Object> createWorkshopQuoteAmendment(DispatchContext dctx, Map<String, ? extends Object> context) {
        String originalQuoteId = (String) context.get("quoteId");
        String diagnosticNote = (String) context.get("diagnosticNote");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        if (diagnosticNote == null || diagnosticNote.trim().isEmpty()) {
            return ServiceUtil.returnError("A justificativa técnica (diagnosticNote) é obrigatória para criar um aditivo.");
        }

        try {
            GenericValue origQuote = EntityQuery.use(dctx.getDelegator()).from("Quote").where("quoteId", originalQuoteId).queryOne();
            if (origQuote == null) {
                return ServiceUtil.returnError("Orçamento base " + originalQuoteId + " não encontrado.");
            }

            GenericValue quoteWorkEffort = EntityQuery.use(dctx.getDelegator())
                    .from("QuoteWorkEffort")
                    .where("quoteId", originalQuoteId)
                    .queryFirst();

            if (quoteWorkEffort == null) {
                return ServiceUtil.returnError("Nenhum atendimento vinculado ao orçamento " + originalQuoteId);
            }
            String workEffortId = quoteWorkEffort.getString("workEffortId");

            Map<String, Object> noteCtx = new HashMap<>();
            noteCtx.put("noteInfo", diagnosticNote);
            noteCtx.put("userLogin", userLogin);
            Map<String, Object> noteRes = dctx.getDispatcher().runSync("createNote", noteCtx);
            if (ServiceUtil.isError(noteRes)) return noteRes;
            String noteId = (String) noteRes.get("noteId");

            Map<String, Object> weNoteCtx = new HashMap<>();
            weNoteCtx.put("workEffortId", workEffortId);
            weNoteCtx.put("noteId", noteId);
            weNoteCtx.put("userLogin", userLogin);
            Map<String, Object> weNoteRes = dctx.getDispatcher().runSync("createWorkEffortNote", weNoteCtx);
            if (ServiceUtil.isError(weNoteRes)) return weNoteRes;

            Map<String, Object> newQuoteCtx = new HashMap<>();
            newQuoteCtx.put("quoteTypeId", KhepriConstants.QUOTE_TYPE_WORKSHOP);
            newQuoteCtx.put("partyId", origQuote.getString("partyId"));
            newQuoteCtx.put("statusId", KhepriConstants.QUOTE_STATUS_CREATED);
            newQuoteCtx.put("issueDate", UtilDateTime.nowTimestamp());
            newQuoteCtx.put("userLogin", userLogin);

            Map<String, Object> quoteRes = dctx.getDispatcher().runSync("createQuote", newQuoteCtx);
            if (ServiceUtil.isError(quoteRes)) return quoteRes;
            String newQuoteId = (String) quoteRes.get("quoteId");

            Map<String, Object> weQuoteCtx = new HashMap<>();
            weQuoteCtx.put("workEffortId", workEffortId);
            weQuoteCtx.put("quoteId", newQuoteId);
            weQuoteCtx.put("userLogin", userLogin);

            Map<String, Object> weQuoteRes = dctx.getDispatcher().runSync("createQuoteWorkEffort", weQuoteCtx);
            if (ServiceUtil.isError(weQuoteRes)) return weQuoteRes;

            Map<String, Object> quoteLinkCtx = new HashMap<>();
            quoteLinkCtx.put("quoteId", originalQuoteId);
            quoteLinkCtx.put("toQuoteId", newQuoteId);
            quoteLinkCtx.put("quoteLinkTypeId", "QUOTE_REVISION");
            quoteLinkCtx.put("userLogin", userLogin);

            Map<String, Object> quoteLinkRes = dctx.getDispatcher().runSync("createQuoteLink", quoteLinkCtx);
            if (ServiceUtil.isError(quoteLinkRes)) return quoteLinkRes;

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("quoteId", newQuoteId);
            return result;

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro ao gerar orçamento aditivo: " + e.getMessage());
        }
    }

    public static Map<String, Object> decideWorkshopAmendment(DispatchContext dctx, Map<String, ? extends Object> context) {
        String quoteId = (String) context.get("quoteId");
        String statusId = (String) context.get("statusId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue quote = EntityQuery.use(dctx.getDelegator()).from("Quote").where("quoteId", quoteId).queryOne();
            if (quote == null) {
                return ServiceUtil.returnError("Orçamento " + quoteId + " não encontrado.");
            }

            if ("QUOTE_REJECTED".equals(statusId)) {
                GenericValue quoteWorkEffort = EntityQuery.use(dctx.getDelegator())
                        .from("QuoteWorkEffort")
                        .where("quoteId", quoteId)
                        .queryFirst();

                if (quoteWorkEffort != null) {
                    String workEffortId = quoteWorkEffort.getString("workEffortId");
                    List<GenericValue> quoteItems = EntityQuery.use(dctx.getDelegator())
                            .from("QuoteItem")
                            .where("quoteId", quoteId)
                            .queryList();

                    for (GenericValue item : quoteItems) {
                        String productId = item.getString("productId");
                        if (productId != null) {
                            List<GenericValue> reservations = EntityQuery.use(dctx.getDelegator())
                                    .from("WorkEffortGoodStandard")
                                    .where("workEffortId", workEffortId, "productId", productId, "workEffortGoodStdTypeId", "USE")
                                    .queryList();

                            for (GenericValue res : reservations) {
                                res.remove();
                            }
                        }
                    }
                }
            }

            Map<String, Object> statusCtx = new HashMap<>();
            statusCtx.put("quoteId", quoteId);
            statusCtx.put("statusId", statusId);
            statusCtx.put("userLogin", userLogin);
            Map<String, Object> statusRes = dctx.getDispatcher().runSync("changeQuoteStatus", statusCtx);
            if (ServiceUtil.isError(statusRes)) return statusRes;

            return ServiceUtil.returnSuccess();

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro ao processar decisão do aditivo: " + e.getMessage());
        }
    }

    public static Map<String, Object> issuePartsToWorkEffort(DispatchContext dctx, Map<String, ? extends Object> context) {
        String workEffortId = (String) context.get("workEffortId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue workEffort = EntityQuery.use(dctx.getDelegator()).from("WorkEffort").where("workEffortId", workEffortId).queryOne();
            if (workEffort == null) {
                return ServiceUtil.returnError("Atendimento " + workEffortId + " não encontrado.");
            }
            String facilityId = workEffort.getString("facilityId");
            if (facilityId == null) {
                return ServiceUtil.returnError("Atendimento " + workEffortId + " não possui uma Facility vinculada.");
            }

            List<GenericValue> partsToIssue = EntityQuery.use(dctx.getDelegator())
                    .from("WorkEffortGoodStandard")
                    .where("workEffortId", workEffortId, "workEffortGoodStdTypeId", "USE")
                    .queryList();

            for (GenericValue reservation : partsToIssue) {
                String productId = reservation.getString("productId");
                BigDecimal quantity = reservation.getBigDecimal("estimatedQuantity");

                GenericValue inventoryItem = EntityQuery.use(dctx.getDelegator())
                        .from("InventoryItem")
                        .where("productId", productId, "facilityId", facilityId)
                        .orderBy("datetimeReceived ASC")
                        .queryFirst();

                if (inventoryItem != null) {
                    Map<String, Object> issueCtx = new HashMap<>();
                    issueCtx.put("workEffortId", workEffortId);
                    issueCtx.put("inventoryItemId", inventoryItem.getString("inventoryItemId"));
                    issueCtx.put("quantity", quantity);
                    issueCtx.put("userLogin", userLogin);

                    Map<String, Object> issueRes = dctx.getDispatcher().runSync("assignInventoryToWorkEffort", issueCtx);
                    if (ServiceUtil.isError(issueRes)) return issueRes;
                } else {
                    return ServiceUtil.returnError("Não foi encontrado item de inventário para o produto " + productId + " na Facility " + facilityId);
                }
            }

            return ServiceUtil.returnSuccess();

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro na orquestração de saída de estoque: " + e.getMessage());
        }
    }

    public static Map<String, Object> getWorkshopOSSummary(DispatchContext dctx, Map<String, ? extends Object> context) {
        String workEffortId = (String) context.get("workEffortId");
        BigDecimal totalAmount = BigDecimal.ZERO;

        try {
            List<GenericValue> quoteLinks = EntityQuery.use(dctx.getDelegator())
                    .from("QuoteWorkEffort")
                    .where("workEffortId", workEffortId)
                    .queryList();

            for (GenericValue link : quoteLinks) {
                String quoteId = link.getString("quoteId");
                GenericValue quote = EntityQuery.use(dctx.getDelegator())
                        .from("Quote")
                        .where("quoteId", quoteId)
                        .queryOne();

                if (quote != null) {
                    String statusId = quote.getString("statusId");
                    if ("QUOTE_APPROVED".equals(statusId) || "QUOTE_ACCEPTED".equals(statusId)) {
                        List<GenericValue> items = EntityQuery.use(dctx.getDelegator())
                                .from("QuoteItem")
                                .where("quoteId", quoteId)
                                .queryList();

                        for (GenericValue item : items) {
                            BigDecimal qty = item.getBigDecimal("quantity");
                            BigDecimal price = item.getBigDecimal("quoteUnitPrice");
                            if (qty != null && price != null) {
                                totalAmount = totalAmount.add(qty.multiply(price));
                            }
                        }
                    }
                }
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("totalAmount", totalAmount);
            return result;

        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro ao calcular sumário consolidado da OS: " + e.getMessage());
        }
    }
}