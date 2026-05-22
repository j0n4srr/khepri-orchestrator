package org.tuaregue.khepri.orchestrator

import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.entity.GenericValue

def khepriOrchestratorPermissionCheck() {
    Map context = this.context
    var security = context.security
    GenericValue userLogin = context.userLogin

    if (security.hasEntityPermission("KHEPRI-ORCHESTRATOR", "_ADMIN", userLogin) ||
            security.hasEntityPermission("KHEPRI", "_ADMIN", userLogin) ||
            security.hasEntityPermission("WORKSHOP", "_OPERATOR", userLogin)) {
        return ServiceUtil.returnSuccess()
    }
    return ServiceUtil.returnError("Usuário não possui privilégios operacionais para esta ação no Khepri Orchestrator.")
}