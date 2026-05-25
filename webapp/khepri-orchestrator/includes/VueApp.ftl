<script>
    window.OFBIZ_BOOTSTRAP = {
        token: "${requestAttributes.externalLoginKey!}"
    };
</script>

<div id="app"></div>

<#if vueCssUrl?has_content>
    <link rel="stylesheet" href="${vueCssUrl}">
</#if>

<#if vueJsUrl?has_content>
    <script type="module" src="${vueJsUrl}"></script>
<#else>
    <div class="alert alert-danger">
        <strong>ERRO:</strong> Não foi possível carregar o Frontend Vue.js.<br>
        O script <code>LoadVueApp.groovy</code> não retornou as URLs do manifesto.
    </div>
</#if>