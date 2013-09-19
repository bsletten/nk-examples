package net.bosatsu.rest.metadata

import org.netkernel.ext.system.representation.IRepDeployedModules
import org.netkernel.layer0.meta.IEndpointMeta
import org.netkernel.layer0.nkf.INKFRequest
import org.netkernel.layer0.nkf.INKFRequestContext
import org.netkernel.layer0.nkf.INKFResponse
import org.netkernel.layer0.representation.impl.HDSBuilder
import org.netkernel.layer0.representation.impl.HDSFactory

import org.netkernel.module.standard.endpoint.StandardAccessorImpl

import com.ten60.netkernel.cache.se.representation2.ConcurrentCache


class MetadataAccessor extends StandardAccessorImpl {
    
    protected def lookupMetadata(hashes, context) {
        def modules = context.source("active:moduleList", IRepDeployedModules.class)
        def cache = ConcurrentCache.getInstance()
        
        def hdsBuilder = new HDSBuilder()
        hdsBuilder.pushNode("eps")
        
        hashes.getNodes("/hashes/hash").each { h ->
            def hash = h.getValue().toString()
            def meta = modules.metadataForHash(hash)
            if(meta != null && meta instanceof IEndpointMeta) {
                hdsBuilder.pushNode("ep")
                hdsBuilder.addNode("hash", hash)
                hdsBuilder.addNode("name", meta.name)
                hdsBuilder.addNode("description", meta.description)
                
                def fields = meta.additionalFields
                
                println "FIELDS: " + fields
                
                def mm = fields.getValue("meta")
                
                println "MM: " + mm
                
                if(mm != null) {
                    def metaField = HDSFactory.parseDOM(mm)
                    hdsBuilder.importChildren(metaField)
                }
                
                int idx = hash.lastIndexOf("/")
                String epId = hash.substring(idx + 1)
                hdsBuilder.addNode("id", epId)
                
                println cache
                
                if(cache != null) {
                    hdsBuilder.pushNode("cacheStats")
                    
                    def cacheData = cache.getEndpointState(epId, ConcurrentCache.EpSort.NONE, true, 0, 1)
                    def node = cacheData.getFirstNode("/endpoints/e")
                    
                    if(node != null) {
                       	//key
                        // c - number of cached representations
                        // r - requests per min
                        // h - hits per min
                        // p - % cache hits
                        // t - total cost of cached representations
                        // a - average cost of cached representations
                        // m - served cost per minute
                        hdsBuilder.addNode("c", node.getFirstValue("@c").toString())
                        hdsBuilder.addNode("r", node.getFirstValue("@r").toString())
                        hdsBuilder.addNode("h", node.getFirstValue("@h").toString())
                        hdsBuilder.addNode("p", (node.getFirstValue("@p")*100).toString())
                        hdsBuilder.addNode("t", (node.getFirstValue("@tc")/1000).toString())
                        hdsBuilder.addNode("a", (node.getFirstValue("@ac")/1000).toString())
                        hdsBuilder.addNode("m", (node.getFirstValue("@cpm")/1000).toString())
                    }
                       
                    hdsBuilder.popNode()
                }
            }
            
            hdsBuilder.popNode()
        }
        
        return hdsBuilder.getRoot()
    }
    
    @Override
    public void onSource(INKFRequestContext context) {
        def hashes = context.source("arg:hashes")
        println hashes
        def metadata = lookupMetadata(hashes, context)
        context.createResponseFrom(metadata)
    }
}