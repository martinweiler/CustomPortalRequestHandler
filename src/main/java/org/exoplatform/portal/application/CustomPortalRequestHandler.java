/**
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.portal.application;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exoplatform.commons.utils.I18N;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.web.ControllerContext;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.gatein.api.PortalRequest;
import org.gatein.api.navigation.Navigation;
import org.gatein.api.navigation.Node;
import org.gatein.api.navigation.NodePath;
import org.gatein.api.navigation.Nodes;
import org.gatein.api.site.SiteType;

/**
 * Created by The eXo Platform SAS Dec 9, 2006
 *
 * This class handle the request that target the portal paths /public and /private
 *
 */
public class CustomPortalRequestHandler extends PortalRequestHandler {

    private static Set<String> validPaths = Collections.synchronizedSet(new HashSet<String>());
    /**
     * Override to return 404 on invalid path requests
     * 
     * Limitations:
     * - some dashboard URLs (eg. host:port/portal/u/user) are recognized as SiteType.SITE instead of DASHBOARD
     *   validation of these URLs is failing, and therefore omitted
     * - A successfully validated URL (eg. http://host:port/portal/classic/home) is cached in a HashSet, to prevent further
     *   validations. There is no mechanism to clean the entries of this HashSet.
     */
    @Override
    public boolean execute(ControllerContext controllerContext) throws Exception {
        HttpServletRequest req = controllerContext.getRequest();
        HttpServletResponse res = controllerContext.getResponse();

        log.debug("Session ID = " + req.getSession().getId());
        res.setHeader("Cache-Control", "no-cache");

        //
        String requestPath = controllerContext.getParameter(REQUEST_PATH);
        String requestSiteType = controllerContext.getParameter(REQUEST_SITE_TYPE);
        String requestSiteName = controllerContext.getParameter(REQUEST_SITE_NAME);

        //
        Locale requestLocale;
        String lang = controllerContext.getParameter(LANG);
        if (lang == null || lang.length() == 0) {
            requestLocale = null;
        } else {
            requestLocale = I18N.parseTagIdentifier(lang);
        }

        if (requestSiteName == null) {
            res.sendRedirect(req.getContextPath());
            return true;
        }

        PortalApplication app = controllerContext.getController().getApplication(PortalApplication.PORTAL_APPLICATION_ID);
        PortalRequestContext context = new PortalRequestContext(app, controllerContext, requestSiteType, requestSiteName,
                requestPath, requestLocale);
        if (context.getUserPortalConfig() == null) {
            DataStorage storage = (DataStorage) PortalContainer.getComponent(DataStorage.class);
            PortalConfig persistentPortalConfig = storage.getPortalConfig(requestSiteType, requestSiteName);
            if (persistentPortalConfig == null) {
                return false;
            } else if (req.getRemoteUser() == null) {
                context.requestAuthenticationLogin();
            } else {
                context.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        } else {
            // check if the current request is a valid portal path            
            if(pathExists(req, context)) {
	            processRequest(context, app);
	        } else {
	            // return http 404 response
	            context.sendError(HttpServletResponse.SC_NOT_FOUND);
	        }
        }
        return true;
    }
    
    private boolean pathExists(HttpServletRequest req, PortalRequestContext context) {
        boolean skipValidation = false;
        Node node = null;
        
        String uri = req.getRequestURI();
        if(uriValidated(uri)) {
            if(log.isDebugEnabled()) {
                log.debug("URI successfully validated: " + uri);
            }
            return true;
        }

        // remove context path, eg. /portal
        String path = uri.replace(req.getContextPath(), "");

        // necessary for Portal API
        WebuiRequestContext.setCurrentInstance(context);
        PortalRequestImpl.createInstance(context);            

        // Portal API
        PortalRequest portalRequest = PortalRequest.getInstance();        
        Navigation navigation = portalRequest.getNavigation();
        
        SiteType siteType = navigation.getSiteId().getType();
        String siteName = navigation.getSiteId().getName();

        // remove site name, eg. /classic
        if(SiteType.SITE == siteType) {
            // some user dashboards are recognized as SiteType.SITE - is this a bug?
            if(path.startsWith("/u/")) {
                skipValidation = true;
            }
            siteName = "/" + siteName;
        } else if(SiteType.DASHBOARD == siteType) {
            path = path.replace("/u", "");
            siteName = "/" + siteName;
        } else if(SiteType.SPACE == siteType) {
            path = path.replace("/g/:", "/");
            path = path.replace(":", "/");
        }
        path = path.replace(siteName, "");
        if(log.isDebugEnabled()) {
            log.debug("site name: " + siteName + ", site type " + siteType);
        }
                    
        // Nodes.visitAll() could have a performance impact with high number of navigation nodes
        if(!skipValidation) {
            node = navigation.getNode(NodePath.fromString(path), Nodes.visitAll());
        }
        
        PortalRequestImpl.clearInstance();
        WebuiRequestContext.setCurrentInstance(null);        
        
        if(log.isDebugEnabled()) {    
            log.debug("Path " + path + " is valid: " + (node!=null?"true":"false"));
        }
        
        // node exists
        if(node != null || skipValidation == true) {
            cacheValidUri(uri);
        	return true;
        }        

        // invalid uri        
        return false;
    }
    
    private boolean uriValidated(String path) {
        return validPaths.contains(path);
    }
    
    private void cacheValidUri(String path) {
        if(!uriValidated(path)) {
            validPaths.add(path);
        }
    }

    
}
