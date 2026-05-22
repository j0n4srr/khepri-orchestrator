package org.tuaregue.khepri.bff;

import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.ModelService;
import java.util.Locale;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.order.shoppingcart.ShoppingCartItem;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.order.shoppingcart.ShoppingCart;
import org.apache.ofbiz.order.shoppingcart.ShoppingCartItem;
import org.apache.ofbiz.order.shoppingcart.CheckOutHelper;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.entity.Delegator;
import org.tuaregue.khepri.KhepriConstants;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.math.BigDecimal;

public class WorkshopOrchestrator {
    public static final String MODULE = WorkshopOrchestrator.class.getName();

    private static void performSecurityCheck(DispatchContext dctx, Map<String, ? extends Object> context) throws Exception {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> result = dispatcher.runSync("khepriOrchestratorPermissionCheck", context);
        if (ServiceUtil.isError(result)) {
            throw new Exception((String) result.get(ModelService.ERROR_MESSAGE));
        }
    }

    public static Map<String, Object> registerVehicleServiceEntry(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        if (context.get("firstName") == null || context.get("lastName") == null || context.get("licensePlate") == null) {
            return ServiceUtil.returnError("Parametros obrigatorios ausentes.");
        }

        String firstName = (String) context.get("firstName");
        String lastName = (String) context.get("lastName");
        String licensePlate = ((String) context.get("licensePlate")).trim().toUpperCase();
        GenericValue userLogin = (GenericValue) context.get("userLogin");

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

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("partyId", partyId);
            result.put("fixedAssetId", fixedAssetId);
            result.put("workEffortId", workEffortId);
            return result;

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro na orquestracao: " + e.getMessage());
        }
    }

    public static Map<String, Object> createWorkshopQuoteFromOS(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        String workEffortId = (String) context.get("workEffortId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue partyAssignment = EntityQuery.use(dctx.getDelegator())
                    .from("WorkEffortPartyAssignment")
                    .where("workEffortId", workEffortId, "roleTypeId", KhepriConstants.ROLE_TYPE_CLIENT)
                    .filterByDate()
                    .queryFirst();

            if (partyAssignment == null) {
                return ServiceUtil.returnError("Nenhum cliente vinculado.");
            }
            String partyId = partyAssignment.getString("partyId");

            Map<String, Object> quoteCtx = new HashMap<>();
            quoteCtx.put("quoteTypeId", KhepriConstants.QUOTE_TYPE_WORKSHOP);
            quoteCtx.put("partyId", partyId);
            quoteCtx.put("productStoreId", "9000");
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
            return ServiceUtil.returnError("Erro ao gerar orcamento: " + e.getMessage());
        }
    }

    public static Map<String, Object> addItemToWorkshopQuote(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        String quoteId = (String) context.get("quoteId");
        String productId = (String) context.get("productId");
        BigDecimal quantity = (BigDecimal) context.get("quantity");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue quote = EntityQuery.use(dctx.getDelegator()).from("Quote").where("quoteId", quoteId).queryOne();
            if (quote == null) {
                return ServiceUtil.returnError("Orcamento nao encontrado.");
            }

            String quoteStatusId = quote.getString("statusId");
            if ("QUOTE_APPROVED".equals(quoteStatusId) || "QUOTE_ACCEPTED".equals(quoteStatusId)) {
                return ServiceUtil.returnError("Orcamento bloqueado.");
            }

            GenericValue product = EntityQuery.use(dctx.getDelegator()).from("Product").where("productId", productId).queryOne();
            if (product == null) {
                return ServiceUtil.returnError("Produto nao encontrado.");
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
            return ServiceUtil.returnError("Erro ao adicionar item: " + e.getMessage());
        }
    }

    public static Map<String, Object> createWorkshopQuoteAmendment(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        String originalQuoteId = (String) context.get("quoteId");
        String diagnosticNote = (String) context.get("diagnosticNote");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        if (diagnosticNote == null || diagnosticNote.trim().isEmpty()) {
            return ServiceUtil.returnError("Justificativa obrigatoria.");
        }

        try {
            GenericValue origQuote = EntityQuery.use(dctx.getDelegator()).from("Quote").where("quoteId", originalQuoteId).queryOne();
            if (origQuote == null) {
                return ServiceUtil.returnError("Orcamento base nao encontrado.");
            }

            GenericValue quoteWorkEffort = EntityQuery.use(dctx.getDelegator())
                    .from("QuoteWorkEffort")
                    .where("quoteId", originalQuoteId)
                    .queryFirst();

            if (quoteWorkEffort == null) {
                return ServiceUtil.returnError("Atendimento nao vinculado.");
            }
            String workEffortId = quoteWorkEffort.getString("workEffortId");

            Map<String, Object> weNoteCtx = new HashMap<>();
            weNoteCtx.put("workEffortId", workEffortId);
            weNoteCtx.put("noteInfo", diagnosticNote);
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

            GenericValue quoteAttr = dctx.getDelegator().makeValue("QuoteAttribute");
            quoteAttr.set("quoteId", newQuoteId);
            quoteAttr.set("attrName", "ORIGINAL_QUOTE_ID");
            quoteAttr.set("attrValue", originalQuoteId);
            dctx.getDelegator().create(quoteAttr);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("quoteId", newQuoteId);
            return result;

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro ao gerar orcamento aditivo: " + e.getMessage());
        }
    }

    public static Map<String, Object> decideWorkshopAmendment(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        String quoteId = (String) context.get("quoteId");
        String statusId = (String) context.get("statusId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue quote = EntityQuery.use(dctx.getDelegator()).from("Quote").where("quoteId", quoteId).queryOne();
            if (quote == null) {
                return ServiceUtil.returnError("Orcamento nao encontrado.");
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
            Map<String, Object> statusRes = dctx.getDispatcher().runSync("updateQuote", statusCtx);
            if (ServiceUtil.isError(statusRes)) return statusRes;

            return ServiceUtil.returnSuccess();

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro ao processar decisao: " + e.getMessage());
        }
    }

    public static Map<String, Object> issuePartsToWorkEffort(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        String workEffortId = (String) context.get("workEffortId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue workEffort = EntityQuery.use(dctx.getDelegator()).from("WorkEffort").where("workEffortId", workEffortId).queryOne();
            if (workEffort == null) {
                return ServiceUtil.returnError("Atendimento nao encontrado.");
            }

            List<GenericValue> requirements = EntityQuery.use(dctx.getDelegator())
                    .from("WorkEffortRequirement")
                    .where("workEffortId", workEffortId)
                    .queryList();

            for (GenericValue reqLink : requirements) {
                Map<String, Object> issueCtx = new HashMap<>();
                issueCtx.put("workEffortId", workEffortId);
                issueCtx.put("requirementId", reqLink.getString("requirementId"));
                issueCtx.put("userLogin", userLogin);

                Map<String, Object> issueRes = dctx.getDispatcher().runSync("issueOrderItemToWorkEffort", issueCtx);
                if (ServiceUtil.isError(issueRes)) {
                    return issueRes;
                }
            }

            return ServiceUtil.returnSuccess();

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro na baixa de estoque: " + e.getMessage());
        }
    }

    public static Map<String, Object> getWorkshopOSSummary(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

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
            return ServiceUtil.returnError("Erro ao calcular sumario: " + e.getMessage());
        }
    }
    public static Map<String, Object> approveWorkshopQuoteAndCreateOrder(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String quoteId = (String) context.get("quoteId");

        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        Debug.logInfo("Iniciando aprovação e conversão de orçamento para quoteId: " + quoteId, MODULE);

        try {
            GenericValue quote = EntityQuery.use(delegator).from("Quote").where("quoteId", quoteId).queryOne();
            if (quote == null) {
                return ServiceUtil.returnError("Orçamento não encontrado: " + quoteId);
            }

            if (!"QUOTE_APPROVED".equals(quote.getString("statusId"))) {
                return ServiceUtil.returnError("O orçamento deve estar aprovado para conversão.");
            }

            String productStoreId = quote.getString("productStoreId");
            if (UtilValidate.isEmpty(productStoreId)) {
                productStoreId = "9000";
            }

            String currencyUomId = quote.getString("currencyUomId");
            if (UtilValidate.isEmpty(currencyUomId)) {
                currencyUomId = "BRL";
            }

            ShoppingCart cart = new ShoppingCart(delegator, productStoreId, null, Locale.getDefault(), currencyUomId);
            cart.setOrderType("SALES_ORDER");
            cart.setOrderPartyId(quote.getString("partyId"));
            cart.setUserLogin(userLogin, dispatcher);

            Debug.logInfo("DEBUG CART CONTEXT - StoreId: " + cart.getProductStoreId() +
                    ", Currency: " + cart.getCurrency() +
                    ", PartyId: " + cart.getPartyId(), MODULE);

            Map<String, Object> loadRes = dispatcher.runSync("loadCartFromQuote", UtilMisc.toMap(
                    "quoteId", quoteId,
                    "shoppingCart", cart,
                    "userLogin", userLogin
            ));

            if (ServiceUtil.isError(loadRes)) {
                Debug.logError("Erro ao carregar carrinho para quoteId " + quoteId + ": " + ServiceUtil.getErrorMessage(loadRes), MODULE);
                return loadRes;
            }

            if (cart.size() == 0) {
                Debug.logError("Carrinho vazio após loadCartFromQuote para quoteId: " + quoteId, MODULE);
                return ServiceUtil.returnError("Falha ao carregar itens do orçamento no carrinho.");
            }

            if (cart.getShipGroupSize() == 0) {
                cart.addShipInfo();
            }

            CheckOutHelper helper = new CheckOutHelper(dispatcher, delegator, cart);
            Map<String, Object> orderRes = helper.createOrder(userLogin, null, null, null, false, null, null);

            if (ServiceUtil.isError(orderRes)) {
                Debug.logError("Erro na criação do pedido: " + ServiceUtil.getErrorMessage(orderRes), MODULE);
                return orderRes;
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("orderId", orderRes.get("orderId"));
            Debug.logInfo("Pedido criado com sucesso: " + orderRes.get("orderId"), MODULE);
            return result;

        } catch (Exception e) {
            Debug.logError(e, "Erro fatal em approveWorkshopQuoteAndCreateOrder para quoteId " + quoteId, MODULE);
            return ServiceUtil.returnError("Erro ao processar conversão do orçamento: " + e.getMessage());
        }
    }

    public static Map<String, Object> requestVehicleGatePass(DispatchContext dctx, Map<String, ? extends Object> context) {
        try {
            performSecurityCheck(dctx, context);
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        String workEffortId = (String) context.get("workEffortId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            GenericValue workEffort = EntityQuery.use(dctx.getDelegator()).from("WorkEffort").where("workEffortId", workEffortId).queryOne();
            if (workEffort == null) return ServiceUtil.returnError("Atendimento nao encontrado.");

            List<GenericValue> orderHeaders = EntityQuery.use(dctx.getDelegator())
                    .from("WorkEffortOrderHeaderView")
                    .where("workEffortId", workEffortId)
                    .queryList();

            for (GenericValue orderHeader : orderHeaders) {
                String orderId = orderHeader.getString("orderId");
                List<GenericValue> invoices = EntityQuery.use(dctx.getDelegator())
                        .from("OrderInvoiceHeaderView")
                        .where("orderId", orderId)
                        .queryList();

                for (GenericValue invoice : invoices) {
                    if (!"INVOICE_PAID".equals(invoice.getString("statusId"))) {
                        return ServiceUtil.returnError("Liberacao bloqueada: Fatura " + invoice.getString("invoiceId") + " pendente.");
                    }
                }
            }

            Map<String, Object> gatePassCtx = new HashMap<>();
            gatePassCtx.put("workEffortTypeId", "GATE_PASS");
            gatePassCtx.put("workEffortName", "Gate Pass for: " + workEffortId);
            gatePassCtx.put("currentStatusId", "KGP_RELEASED");
            gatePassCtx.put("workEffortParentId", workEffortId);
            gatePassCtx.put("userLogin", userLogin);

            Map<String, Object> resp = dctx.getDispatcher().runSync("createWorkEffort", gatePassCtx);
            return resp;

        } catch (GenericServiceException | GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro na validacao de Gate Pass: " + e.getMessage());
        }
    }

    public static Map<String, Object> createSimpleWorkshopProduct(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        String productName = (String) context.get("productName");
        BigDecimal price = (BigDecimal) context.get("price");
        String categoryId = (String) context.get("productCategoryId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        try {
            // 1. Criar Produto
            Map<String, Object> productCtx = UtilMisc.toMap(
                    "productTypeId", "FINISHED_GOOD",
                    "productName", productName,
                    "internalName", productName,
                    "userLogin", userLogin
            );
            Map<String, Object> productResult = dispatcher.runSync("createProduct", productCtx);
            if (ServiceUtil.isError(productResult)) return productResult;
            String productId = (String) productResult.get("productId");

            // 2. Criar Preço com Purpose COMPONENT_PRICE (Aceito em vendas)
            Map<String, Object> priceCtx = UtilMisc.toMap(
                    "productId", productId,
                    "productPriceTypeId", "DEFAULT_PRICE",
                    "productPricePurposeId", "COMPONENT_PRICE", 
                    "productStoreGroupId", "_NA_",
                    "currencyUomId", "BRL",
                    "price", price,
                    "fromDate", UtilDateTime.nowTimestamp(),
                    "userLogin", userLogin
            );
            Map<String, Object> priceResult = dispatcher.runSync("createProductPrice", priceCtx);
            if (ServiceUtil.isError(priceResult)) return priceResult;

            // 3. Associar Categoria
            if (categoryId != null) {
                Map<String, Object> catCtx = UtilMisc.toMap(
                        "productId", productId,
                        "productCategoryId", categoryId,
                        "fromDate", UtilDateTime.nowTimestamp(),
                        "userLogin", userLogin
                );
                Map<String, Object> catResult = dispatcher.runSync("addProductToCategory", catCtx);
                if (ServiceUtil.isError(catResult)) return catResult;
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("productId", productId);
            return result;

        } catch (Exception e) {
            return ServiceUtil.returnError("Falha: " + e.getMessage());
        }
    }
}