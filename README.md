This project shows how to extend the PortalRequestHandler class of GateIn, with the intent to return a HTTP 404 response for requests to non-existing pages, eg. http://host:port/portal/classic/non-existing-page. Note that due to the package visibility of some methods and members of the PortalRequestHandler class, the custom extension needs to reside in the 'org.exoplatform.portal.application' package.

Build instructions:
==================

1. Download JPP 6.1 quickstarts and maven repository and follow the instructions to set up the maven repository

2. Run the build with
   $ mvn clean install -s /path/to/jpp610/quickstarts/settings-hosted-repo.xml
   

Installation instructions
=========================

1. Copy target/portal-request-handler-<VERSION>.jar to $JPP_HOME/modules/system/layers/gatein/org/gatein/lib/main

2. Edit $JPP_HOME/modules/system/layers/gatein/org/gatein/lib/main/module.xml, and add the following to the <resources> element:
   
   <resource-root path="portal-request-handler-<VERSION>.jar"/>
   
3. Edit $JPP_HOME/gatein/gatein.ear/portal.war/WEB-INF/conf/portal/controller-configuration, and apply the following change:   

    <component-plugin>
      <name>PortalRequestHandler</name>
      <set-method>register</set-method>
      <!-- Disable the default handler
      <type>org.exoplatform.portal.application.PortalRequestHandler</type>
      -->
      <type>org.exoplatform.portal.application.CustomPortalRequestHandler</type>


