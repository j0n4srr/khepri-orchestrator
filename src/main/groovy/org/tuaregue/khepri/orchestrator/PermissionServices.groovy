package org.tuaregue.khepri.orchestrator

import org.apache.ofbiz.service.ServiceUtil

def khepriOrchestratorPermissionCheck() {
    // BYPASS para desenvolvimento/testes: Sempre permitir
    return ServiceUtil.returnSuccess()
}