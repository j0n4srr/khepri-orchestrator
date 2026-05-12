package tuaregue.khepri.orchestrator.core.inventory;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.base.util.Debug;

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

            long pendingItems = EntityQuery.use(delegator)
                    .from("OrderItem")
                    .where("orderId", orderId, "statusId", "ITEM_APPROVED")
                    .queryCount();

            long reservedItems = EntityQuery.use(delegator)
                    .from("OrderItemShipGrpInvRes")
                    .where("orderId", orderId)
                    .queryCount();

            boolean allPartsReserved = (pendingItems > 0 && pendingItems == reservedItems);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("allPartsReserved", allPartsReserved);
            return result;

        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return ServiceUtil.returnError("Erro de banco de dados ao verificar inventário: " + e.getMessage());
        }
    }
}