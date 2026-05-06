package tuaregue.khepri.orchestrator.core.inventory;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.Map;

public class KhepriInventoryServices {

    public static Map<String, Object> verifyPartsAvailability(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String orderId = (String) context.get("orderId");

        try {
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

        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }
    }
}