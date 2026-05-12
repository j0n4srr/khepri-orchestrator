package org.tuaregue.khepri.orchestrator.core.inventory;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.base.util.Debug;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class KhepriInventoryServices {
    public static final String MODULE = KhepriInventoryServices.class.getName();

    public static Map<String, Object> verifyPartsAvailability(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String orderId = (String) context.get("orderId");

        try {
            GenericValue orderHeader = EntityQuery.use(delegator).from("OrderHeader").where("orderId", orderId).queryOne();
            if (orderHeader == null) {
                return ServiceUtil.returnError("Ordem não encontrada");
            }

            // Busca itens que requerem reserva física
            List<GenericValue> orderItems = EntityQuery.use(delegator)
                    .from("OrderItem")
                    .where("orderId", orderId, "statusId", "ITEM_APPROVED")
                    .queryList();

            if (orderItems.isEmpty()) {
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("allPartsReserved", false);
                return result;
            }

            boolean allReserved = true;
            for (GenericValue item : orderItems) {
                BigDecimal quantityOrdered = item.getBigDecimal("quantity");
                String itemSeqId = item.getString("orderItemSeqId");

                // Soma total reservado para este item específico
                List<GenericValue> reservations = EntityQuery.use(delegator)
                        .from("OrderItemShipGrpInvRes")
                        .where("orderId", orderId, "orderItemSeqId", itemSeqId)
                        .queryList();

                BigDecimal totalReserved = BigDecimal.ZERO;
                if (reservations != null) {
                    for (GenericValue res : reservations) {
                        totalReserved = totalReserved.add(res.getBigDecimal("quantity"));
                    }
                }

                if (totalReserved.compareTo(quantityOrdered) < 0) {
                    allReserved = false;
                    break;
                }
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("allPartsReserved", allReserved);
            return result;

        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro de banco de dados ao verificar inventário: " + e.getMessage());
        }
    }
}