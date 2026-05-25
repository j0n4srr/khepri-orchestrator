package org.tuaregue.khepri.orchestrator

import groovy.json.JsonSlurper
import java.nio.file.Path
import org.apache.ofbiz.webapp.control.JWTManager
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.base.util.Debug

String contextFilesystemPath = context.contextRoot.toString().replaceAll('/$', '')
String manifestPathStr = contextFilesystemPath + "/static/dist/.vite/manifest.json"

Path manifestPath = Path.of(manifestPathStr)
Debug.logInfo("DEBUG: Tentando ler manifesto em: " + manifestPathStr, "LoadVueApp")

if (manifestPath.toFile().exists()) {
    def manifest = new JsonSlurper().parse(manifestPath.toFile())
    def entry = manifest['index.html']

    if (entry) {
        String baseUrl = "/khepri-orchestrator/static/dist/"
        context.vueJsUrl = baseUrl + entry.file
        if (entry.css) {
            context.vueCssUrl = baseUrl + entry.css[0]
        }
    } else {
        Debug.logError("ERRO: Chave 'index.html' não encontrada no manifesto.", "LoadVueApp")
    }
} else {
    Debug.logError("ERRO: Arquivo manifest.json não encontrado em: " + manifestPathStr, "LoadVueApp")
}

if (UtilValidate.isNotEmpty(context.userLogin)) {
    String userLoginId = context.userLogin.getString("userLoginId")
    Map<String, Object> claims = UtilMisc.toMap("userLoginId", userLoginId)
    String accessToken = JWTManager.createJwt(delegator, claims)
    context.accessToken = accessToken
}