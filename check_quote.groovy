import org.apache.ofbiz.entity.DelegatorFactory
import org.apache.ofbiz.entity.Delegator
import org.apache.ofbiz.entity.model.ModelEntity

Delegator delegator = DelegatorFactory.getDelegator("default")
ModelEntity modelQuote = delegator.getModelReader().getModelEntity("Quote")
println "Fields of Quote: " + modelQuote.getAllFieldNames()
